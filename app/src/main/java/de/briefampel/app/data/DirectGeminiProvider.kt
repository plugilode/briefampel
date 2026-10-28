package de.briefampel.app.data

import android.util.Base64
import de.briefampel.app.BuildConfig
import de.briefampel.app.domain.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Direct Gemini REST adapter for a user's own API key. Not bundled with the app; the key is entered by the user in Settings. */
internal class DirectGeminiProvider(
    private val key: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/",
    /** Hauptmodell, dann genau ein Ausweichmodell, falls Google das erste als überlastet meldet. Keine Schleife. */
    private val modelle: List<String> = listOf(BuildConfig.AI_MODEL, BuildConfig.AI_MODEL_FALLBACK).filter { it.isNotBlank() }.distinct(),
) : LlmProvider {
    private class Ueberlastet(val code: Int) : Exception()

    override suspend fun analyze(input: LetterInput, today: LocalDate): LetterAnalysis {
        val apiKey = key() ?: error("Kein Gemini-API-Schlüssel gespeichert. Bitte in den Einstellungen unter „Gemini-API-Schlüssel“ einen Schlüssel hinterlegen.")
        val payload = payload(input, Prompts.analysisRules, Prompts.analysis(today), json = true)
        var raw: String? = null
        for ((index, modell) in modelle.withIndex()) {
            try {
                raw = abrufen(request(apiKey, payload, streaming = false, modell))
                break
            } catch (e: Ueberlastet) {
                if (index == modelle.lastIndex) checkStatus(e.code)
            }
        }
        val response = JSONObject(raw ?: error("Keine Antwort erhalten. Bitte nochmal versuchen."))
        checkFinish(response)
        return AnalysisJson.parse(responseText(response).ifBlank { error("Keine lesbare Analyse erhalten. Bitte nochmal versuchen.") })
    }
    override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate): Flow<String> = callbackFlow {
        val apiKey = key() ?: error("Kein Gemini-API-Schlüssel gespeichert. Bitte in den Einstellungen unter „Gemini-API-Schlüssel“ einen Schlüssel hinterlegen.")
        val payload = payload(input, Prompts.draftRules, Prompts.draft(analysis, keywords, tone, sender, today), json = false)
        var aktuell: Call? = null
        fun starte(index: Int) {
            val call = client.newCall(request(apiKey, payload, streaming = true, modelle[index]))
            aktuell = call
            call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (it.code in UEBERLASTET && index < modelle.lastIndex) { starte(index + 1); return }
                        checkStatus(it.code)
                        val full = StringBuilder()
                        val body = it.body ?: error("Keine Antwort erhalten. Bitte nochmal versuchen.")
                        consumeSse(body.charStream().buffered()) { data ->
                            val part = JSONObject(data).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return@consumeSse
                            val text = (0 until part.length()).mapNotNull { part.optJSONObject(it) }.filterNot { it.optBoolean("thought", false) }.joinToString("") { it.optString("text", "") }
                            full.append(text)
                            if (full.isNotBlank()) trySend(full.toString())
                        }
                        if (full.isNotBlank()) close()
                        else close(Error("Keine Antwort erhalten. Bitte nochmal versuchen."))
                    }
                } catch (e: Exception) { close(e) }
            }
            })
        }
        starte(0)
        awaitClose { aktuell?.cancel() }
    }

    private suspend fun abrufen(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (it.code in UEBERLASTET) throw Ueberlastet(it.code)
                        checkStatus(it.code)
                        val body = it.body ?: error("Keine Antwort erhalten. Bitte nochmal versuchen.")
                        val rawResponse = body.string()
                        if (continuation.isActive) continuation.resume(rawResponse)
                    }
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

    private fun request(apiKey: String, payload: JSONObject, streaming: Boolean, modell: String): Request {
        val url = baseUrl + if (streaming) "models/$modell:streamGenerateContent?alt=sse" else "models/$modell:generateContent"
        return Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create("application/json".toMediaType(), payload.toString()))
            .build()
    }

    companion object {
        /** Serverseitige Überlastung bei Google: lohnt einen einmaligen Versuch mit dem Ausweichmodell. */
        private val UEBERLASTET = setOf(500, 502, 503, 504)

        internal fun payload(input: LetterInput, rules: String, prompt: String, json: Boolean): JSONObject {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", prompt))
        if (input.text.isNotBlank()) parts.put(JSONObject().put("text", "--- BRIEFTEXT (DATEN) ---\n${input.text}\n--- ENDE BRIEFTEXT ---"))
        input.attachments.forEach { parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", it.mime).put("data", Base64.encodeToString(it.bytes, Base64.NO_WRAP)))) }
        val config = JSONObject().put("maxOutputTokens", 4096)
        if (json) config.put("responseMimeType", "application/json")
        return JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", rules))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", config)
    }
    internal fun responseText(response: JSONObject): String {
        val parts = response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filterNot { it.optBoolean("thought", false) }.joinToString("") { it.optString("text", "") }
    }

    internal fun checkFinish(response: JSONObject) {
        response.optJSONObject("error")?.let { checkStatus(it.optInt("code", 500)) }
        val reason = response.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason").orEmpty()
        when {
            reason == "MAX_TOKENS" -> error("Wurde abgeschnitten. Weniger Stichworte probieren.")
            reason.isNotBlank() && reason != "STOP" -> error("Gemini konnte diesen Inhalt nicht verarbeiten. Bitte den Brief prüfen.")
            response.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty().isNotBlank() -> error("Gemini konnte diesen Inhalt nicht verarbeiten. Bitte den Brief prüfen.")
        }
    }

    internal fun checkStatus(code: Int) {
        if (code in 200..299) return
        error(when (code) {
            400 -> "Gemini hat die Anfrage abgelehnt. Bitte Brief, Modell und API-Schlüssel prüfen."
            401, 403 -> "Der Gemini-Schlüssel wurde nicht akzeptiert. Bitte Schlüssel und API-Berechtigung prüfen."
            404 -> "Das Gemini-Modell ist für dein API-Projekt nicht verfügbar. Bitte die Modellkonfiguration prüfen."
            429 -> "Gemini-Limit erreicht. Bitte dein API-Kontingent prüfen oder später erneut versuchen."
            500, 502, 503, 504 -> "Gemini ist gerade überlastet. Das geht meist schnell vorbei – bitte in ein paar Sekunden nochmal tippen."
            else -> "Gemini ist gerade nicht erreichbar (HTTP $code). Bitte später erneut versuchen."
        })
    }

    /** Handles SSE event boundaries, multiline data, keepalives, and a final event without a blank line. */
    internal fun consumeSse(reader: BufferedReader, event: (String) -> Unit) {
        val data = StringBuilder()
        fun dispatch() {
            if (data.isNotEmpty()) {
                val value = data.toString()
                data.setLength(0)
                if (value != "[DONE]") event(value)
            }
        }
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> dispatch()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").removePrefix(" "))
                    require(data.length <= 1_000_000) { "Die Gemini-Antwort ist zu groß." }
                }
            }
        }
        dispatch()
    }
    }
}
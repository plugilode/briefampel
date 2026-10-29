package de.briefampel.app.data

import de.briefampel.app.domain.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Mistral (Paris, EU) direkt mit dem eigenen Schlüssel, nur für die Test-APK.
 * Gleiche Prompts wie bei Gemini; Bilder gehen direkt ans Chat-Modell, PDFs vorher durch Mistrals Texterkennung.
 * Der Server macht dasselbe in functions/src/mistral.ts.
 */
internal class DirectMistralProvider(
    private val key: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build(),
    private val baseUrl: String = "https://api.mistral.ai/v1/",
    private val modelle: List<String> = listOf("mistral-small-latest", "mistral-medium-latest"),
) : LlmProvider {
    private class Ueberlastet : Exception()

    override suspend fun analyze(input: LetterInput, today: LocalDate, sprache: Sprache): LetterAnalysis {
        val apiKey = schluessel()
        val nachricht = nachricht(apiKey, input, Prompts.analysis(today))
        var roh: String? = null
        for ((index, modell) in modelle.withIndex()) {
            try {
                roh = abrufen(chat(apiKey, body(modell, Prompts.analysisRules(sprache), nachricht, json = true, streaming = false)))
                break
            } catch (e: Ueberlastet) {
                if (index == modelle.lastIndex) checkStatus(503)
            }
        }
        val wahl = JSONObject(roh ?: error(tr("Keine Antwort erhalten. Bitte nochmal versuchen."))).optJSONArray("choices")?.optJSONObject(0)
        pruefeEnde(wahl?.optString("finish_reason"))
        val text = wahl?.optJSONObject("message")?.optString("content").orEmpty()
        return AnalysisJson.parse(text.ifBlank { error(tr("Keine lesbare Analyse erhalten. Bitte nochmal versuchen.")) })
    }

    override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate): Flow<String> = callbackFlow {
        val apiKey = schluessel()
        val nachricht = nachricht(apiKey, input, Prompts.draft(analysis, keywords, tone, sender, today))
        var aktuell: Call? = null
        fun starte(index: Int) {
            val call = client.newCall(chat(apiKey, body(modelle[index], Prompts.draftRules, nachricht, json = false, streaming = true)))
            aktuell = call
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { close(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { r ->
                            if (r.code in UEBERLASTET && index < modelle.lastIndex) { starte(index + 1); return }
                            checkStatus(r.code)
                            val ganz = StringBuilder()
                            DirectGeminiProvider.consumeSse(r.body!!.charStream().buffered()) { data ->
                                val wahl = JSONObject(data).optJSONArray("choices")?.optJSONObject(0) ?: return@consumeSse
                                pruefeEnde(wahl.optString("finish_reason"))
                                val stueck = wahl.optJSONObject("delta")?.optString("content").orEmpty()
                                ganz.append(stueck)
                                if (ganz.isNotBlank()) trySend(ganz.toString())
                            }
                            if (ganz.isNotBlank()) close() else close(IllegalStateException(tr("Keine Antwort erhalten. Bitte nochmal versuchen.")))
                        }
                    } catch (e: Exception) { close(e) }
                }
            })
        }
        starte(0)
        awaitClose { aktuell?.cancel() }
    }

    private fun schluessel() = key() ?: error(tr("Kein Mistral-API-Schlüssel gespeichert. Bitte in den Einstellungen einen Schlüssel hinterlegen."))

    /** Nutzernachricht: Auftrag, Brieftext, Bilder als Daten-URL, PDFs als erkannter Text. */
    private suspend fun nachricht(apiKey: String, input: LetterInput, prompt: String): JSONArray {
        val teile = JSONArray().put(text(prompt))
        if (input.text.isNotBlank()) teile.put(text("--- BRIEFTEXT (DATEN) ---\n${input.text}\n--- ENDE BRIEFTEXT ---"))
        input.attachments.forEach { a ->
            val daten = android.util.Base64.encodeToString(a.bytes, android.util.Base64.NO_WRAP)
            if (a.mime == "application/pdf") teile.put(text("--- DOKUMENT (DATEN, per Texterkennung) ---\n${pdfLesen(apiKey, daten)}\n--- ENDE DOKUMENT ---"))
            else teile.put(JSONObject().put("type", "image_url").put("image_url", "data:${a.mime};base64,$daten"))
        }
        return teile
    }

    private suspend fun pdfLesen(apiKey: String, daten: String): String {
        val body = JSONObject().put("model", "mistral-ocr-latest")
            .put("document", JSONObject().put("type", "document_url").put("document_url", "data:application/pdf;base64,$daten"))
        val seiten = JSONObject(abrufen(post(apiKey, "ocr", body))).optJSONArray("pages") ?: JSONArray()
        return (0 until seiten.length()).joinToString("\n\n") { "--- SEITE ${it + 1} ---\n${seiten.optJSONObject(it)?.optString("markdown").orEmpty()}" }
    }

    private fun chat(apiKey: String, body: JSONObject) = post(apiKey, "chat/completions", body)

    private fun post(apiKey: String, weg: String, body: JSONObject) = Request.Builder().url(baseUrl + weg)
        .header("Authorization", "Bearer $apiKey")
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()

    private suspend fun abrufen(request: Request): String = suspendCancellableCoroutine { fortsetzung ->
        val call = client.newCall(request)
        fortsetzung.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (fortsetzung.isActive) fortsetzung.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { r ->
                        if (r.code in UEBERLASTET) throw Ueberlastet()
                        checkStatus(r.code)
                        val text = r.body?.string() ?: error(tr("Keine Antwort erhalten. Bitte nochmal versuchen."))
                        if (fortsetzung.isActive) fortsetzung.resume(text)
                    }
                } catch (e: Exception) { if (fortsetzung.isActive) fortsetzung.resumeWithException(e) }
            }
        })
    }

    companion object {
        private val UEBERLASTET = setOf(500, 502, 503, 504)

        private fun text(t: String) = JSONObject().put("type", "text").put("text", t)

        internal fun body(modell: String, regeln: String, nachricht: JSONArray, json: Boolean, streaming: Boolean): JSONObject = JSONObject()
            .put("model", modell)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", regeln))
                .put(JSONObject().put("role", "user").put("content", nachricht)))
            .put("max_tokens", 4096)
            .put("temperature", 0.2)
            .put("stream", streaming)
            .apply { if (json) put("response_format", JSONObject().put("type", "json_object")) }

        internal fun pruefeEnde(grund: String?) {
            when (grund) {
                null, "", "null", "stop" -> Unit
                "length", "model_length" -> error(tr("Wurde abgeschnitten. Weniger Stichworte probieren."))
                else -> error(tr("Mistral konnte diesen Inhalt nicht verarbeiten. Bitte den Brief prüfen."))
            }
        }

        internal fun checkStatus(code: Int) {
            if (code in 200..299) return
            error(when (code) {
                400, 422 -> tr("Mistral hat die Anfrage abgelehnt. Bitte Brief und Datei prüfen.")
                401, 403 -> tr("Der Mistral-Schlüssel wurde nicht akzeptiert. Bitte Schlüssel prüfen.")
                429 -> tr("Gerade zu viele Anfragen. Bitte gleich nochmal versuchen.")
                in UEBERLASTET -> tr("Mistral ist gerade überlastet. Bitte in ein paar Sekunden nochmal versuchen.")
                else -> tr("Etwas ist schiefgelaufen. Bitte nochmal versuchen.")
            })
        }
    }
}

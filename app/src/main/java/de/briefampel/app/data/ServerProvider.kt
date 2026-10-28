package de.briefampel.app.data

import android.util.Base64
import de.briefampel.app.domain.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Prüfen und Antworten über den eigenen Server. Der Server baut die Prompts, zählt das Guthaben
 * und reicht Geminis Antwort im selben Format durch, deshalb teilen wir das Auslesen mit [DirectGeminiProvider].
 */
internal class ServerProvider(private val server: FirebaseServer) : LlmProvider {
    /** Analyse-ID pro Ergebnis, damit der Server Antworten der richtigen Analyse zuordnet. */
    private val analyseIds = ConcurrentHashMap<LetterAnalysis, String>()

    override suspend fun analyze(input: LetterInput, today: LocalDate): LetterAnalysis {
        val anfrage = server.anfrage("/pruefen", server.koepfe(), brief(input, today))
        val roh = ausfuehren(anfrage)
        val antwort = JSONObject(roh)
        DirectGeminiProvider.checkFinish(antwort)
        val analyse = AnalysisJson.parse(DirectGeminiProvider.responseText(antwort).ifBlank { error("Keine lesbare Analyse erhalten. Bitte nochmal versuchen.") })
        antwort.optJSONObject("briefampel")?.let { b ->
            analyseIds[analyse] = b.getString("analyseId")
            server.guthabenStrom.value = b.optInt("guthaben")
        }
        return analyse
    }

    override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate): Flow<String> = callbackFlow {
        val id = analyseIds[analysis] ?: throw ServerFehler(404, "Bitte den Brief einmal neu prüfen, dann kann ich antworten.")
        val body = brief(input, today)
            .put("analyseId", id)
            .put("analyse", JSONObject(AnalysisJson.encode(analysis)))
            .put("stichworte", keywords)
            .put("ton", tone.name)
            .put("absender", JSONObject().put("name", sender.name).put("address", sender.address).put("email", sender.email))
        val call = server.client.newCall(server.anfrage("/antworten", server.koepfe(), body))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { r ->
                        if (!r.isSuccessful) server.pruefeAntwort(r.code, r.body?.string())
                        val ganz = StringBuilder()
                        DirectGeminiProvider.consumeSse(r.body!!.charStream().buffered()) { data ->
                            val o = JSONObject(data)
                            o.optJSONObject("briefampel")?.let { server.guthabenStrom.value = it.optInt("guthaben"); return@consumeSse }
                            val teile = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return@consumeSse
                            ganz.append((0 until teile.length()).mapNotNull { teile.optJSONObject(it) }.filterNot { it.optBoolean("thought", false) }.joinToString("") { it.optString("text", "") })
                            if (ganz.isNotBlank()) trySend(ganz.toString())
                        }
                        if (ganz.isNotBlank()) close() else close(IllegalStateException("Keine Antwort erhalten. Bitte nochmal versuchen."))
                    }
                } catch (e: Exception) { close(e) }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun brief(input: LetterInput, today: LocalDate) = JSONObject()
        .put("text", input.text)
        .put("heute", Fristen.format(today))
        .put("dateien", JSONArray().apply {
            input.attachments.forEach { put(JSONObject().put("mime", it.mime).put("data", Base64.encodeToString(it.bytes, Base64.NO_WRAP))) }
        })

    private suspend fun ausfuehren(anfrage: okhttp3.Request): String = suspendCancellableCoroutine { fortsetzung ->
        val call = server.client.newCall(anfrage)
        fortsetzung.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (fortsetzung.isActive) fortsetzung.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { r ->
                        val text = r.body?.string()
                        server.pruefeAntwort(r.code, text)
                        if (fortsetzung.isActive) fortsetzung.resume(text ?: error("Keine Antwort erhalten. Bitte nochmal versuchen."))
                    }
                } catch (e: Exception) { if (fortsetzung.isActive) fortsetzung.resumeWithException(e) }
            }
        })
    }
}

package de.briefampel.app.data

import de.briefampel.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class DirectMistralProviderTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val analyse = """{"ampel":"rot","titel":"Reclamación","kurz":"Pagar.","risiko":"Demanda.","antwort_noetig":true,"empfehlung":"Impugnar."}"""
    private lateinit var web: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var provider: DirectMistralProvider

    @Before fun setup() {
        web = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        provider = DirectMistralProvider({ "test-schluessel-nur-lokal" }, client, web.url("/v1/").toString(), listOf("klein", "mittel"))
    }
    @After fun cleanup() { client.dispatcher.cancelAll(); web.shutdown() }

    private fun chat(text: String, ende: String = "stop") = MockResponse().setHeader("Content-Type", "application/json").setBody(
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", text)).put("finish_reason", ende))).toString())

    @Test fun `Bilder direkt, PDF per Texterkennung, Sprache im Auftrag, Schluessel nur im Kopf`() = runBlocking {
        web.enqueue(MockResponse().setBody("""{"pages":[{"markdown":"Seite eins"}]}"""))
        web.enqueue(chat(analyse))
        val input = LetterInput("Bitte zahlen.", listOf(
            Attachment("p", "brief.pdf", "application/pdf", byteArrayOf(1), 1),
            Attachment("b", "foto.jpg", "image/jpeg", byteArrayOf(2), 1),
        ))
        val ergebnis = withTimeout(5000) { provider.analyze(input, today, Sprache.ES) }
        assertEquals(Ampel.ROT, ergebnis.ampel)
        val ocr = web.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/ocr", ocr.path)
        assertEquals("Bearer test-schluessel-nur-lokal", ocr.getHeader("Authorization"))
        assertEquals("data:application/pdf;base64,AQ==", JSONObject(ocr.body.readUtf8()).getJSONObject("document").getString("document_url"))
        val anfrage = web.takeRequest(2, TimeUnit.SECONDS)!!
        assertFalse(anfrage.requestUrl.toString().contains("test-schluessel"))
        val body = JSONObject(anfrage.body.readUtf8())
        assertEquals("klein", body.getString("model"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        val nachrichten = body.getJSONArray("messages")
        assertTrue(nachrichten.getJSONObject(0).getString("content").contains("auf Spanisch"))
        val teile = nachrichten.getJSONObject(1).getJSONArray("content").toString().replace("\\/", "/")
        assertTrue(teile.contains("Seite eins"))
        assertTrue(teile.contains("data:image/jpeg;base64,Ag=="))
    }

    @Test fun `bei Ueberlastung einmal das Ausweichmodell`() = runBlocking {
        web.enqueue(MockResponse().setResponseCode(503))
        web.enqueue(chat(analyse))
        provider.analyze(LetterInput("x"), today, Sprache.DE)
        web.takeRequest()
        assertEquals("mittel", JSONObject(web.takeRequest().body.readUtf8()).getString("model"))
    }

    @Test fun `abgeschnittene Analyse wird klar gemeldet`() = runBlocking {
        web.enqueue(chat("{", "length"))
        val fehler = runCatching { provider.analyze(LetterInput("x"), today, Sprache.DE) }.exceptionOrNull()
        assertEquals("Wurde abgeschnitten. Weniger Stichworte probieren.", fehler?.message)
    }

    @Test fun `Antwort streamt Stueck fuer Stueck`() = runBlocking {
        val sse = listOf(
            """{"choices":[{"delta":{"role":"assistant","content":""}}]}""",
            """{"choices":[{"delta":{"content":"Sehr geehrte "}}]}""",
            """{"choices":[{"delta":{"content":"Damen und Herren"},"finish_reason":"stop"}]}""",
            "[DONE]",
        ).joinToString("") { "data: $it\n\n" }
        web.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        val teile = withTimeout(5000) { provider.draft(LetterInput("x"), AnalysisJson.parse(analyse), "zahle", Tone.SACHLICH, Sender(), today).toList() }
        assertEquals("Sehr geehrte Damen und Herren", teile.last())
        assertTrue(JSONObject(web.takeRequest().body.readUtf8()).getBoolean("stream"))
    }
}

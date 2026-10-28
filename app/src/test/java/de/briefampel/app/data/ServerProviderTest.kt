package de.briefampel.app.data

import de.briefampel.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ServerProviderTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val analyseText = """{"ampel":"rot","titel":"Mahnung","kurz":"Zahlung offen.","risiko":"Inkasso droht.","antwort_noetig":true,"empfehlung":"Bezahlen oder widersprechen."}"""
    private lateinit var web: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var server: FirebaseServer
    private lateinit var provider: ServerProvider

    @Before fun setup() {
        web = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        server = object : FirebaseServer(RuntimeEnvironment.getApplication(), client, web.url("/api").toString()) {
            override suspend fun koepfe() = mapOf("Authorization" to "Bearer id-token", "X-Firebase-AppCheck" to "app-check")
        }
        provider = ServerProvider(server)
    }
    @After fun cleanup() { client.dispatcher.cancelAll(); web.shutdown() }

    private fun huelle(text: String, extra: JSONObject? = null) = JSONObject()
        .put("candidates", org.json.JSONArray().put(JSONObject()
            .put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", text))))
            .put("finishReason", "STOP")))
        .apply { if (extra != null) put("briefampel", extra) }.toString()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun `pruefen sendet Brief mit Anmeldung und merkt Guthaben`() = runBlocking {
        web.enqueue(json(200, huelle(analyseText, JSONObject().put("analyseId", "a1").put("guthaben", 2))))
        val analyse = withTimeout(5000) { provider.analyze(LetterInput("Bitte zahlen.", listOf(Attachment("p1", "seite.jpg", "image/jpeg", byteArrayOf(1, 2, 3), 1))), today) }
        assertEquals(Ampel.ROT, analyse.ampel)
        assertEquals(2, server.guthabenStrom.value)
        val anfrage = web.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/pruefen", anfrage.path)
        assertEquals("Bearer id-token", anfrage.getHeader("Authorization"))
        assertEquals("app-check", anfrage.getHeader("X-Firebase-AppCheck"))
        val body = JSONObject(anfrage.body.readUtf8())
        assertEquals("28.10.2026", body.getString("heute"))
        assertEquals("de", body.getString("sprache"))
        assertEquals("AQID", body.getJSONArray("dateien").getJSONObject(0).getString("data"))
        // Der Server baut die Prompts; die App schickt keine.
        assertFalse(body.has("systemInstruction"))
    }

    @Test fun `leeres Guthaben und abgelaufene Anmeldung werden eigene Fehler`() = runBlocking {
        web.enqueue(json(402, """{"fehler":"guthaben_leer","text":"Dein Guthaben ist aufgebraucht."}"""))
        val leer = runCatching { provider.analyze(LetterInput("x"), today) }.exceptionOrNull()
        assertTrue(leer is GuthabenLeer)
        assertEquals(0, server.guthabenStrom.value)
        web.enqueue(json(401, """{"fehler":"anmeldung","text":"Bitte anmelden."}"""))
        assertTrue(runCatching { provider.analyze(LetterInput("x"), today) }.exceptionOrNull() is AnmeldungNoetig)
        web.enqueue(json(503, """{"fehler":"ueberlastet","text":"Gerade sehr viel los. Bitte gleich nochmal."}"""))
        val voll = runCatching { provider.analyze(LetterInput("x"), today) }.exceptionOrNull()
        assertEquals("Gerade sehr viel los. Bitte gleich nochmal.", voll?.let(::errorMessage))
    }

    @Test fun `antwort streamt Text und schickt Analyse-ID mit`() = runBlocking {
        web.enqueue(json(200, huelle(analyseText, JSONObject().put("analyseId", "a7").put("guthaben", 5))))
        val analyse = provider.analyze(LetterInput("Bitte zahlen."), today)
        val sse = buildString {
            append("data: ").append(huelle("Sehr geehrte ")).append("\n\n")
            append("data: ").append(huelle("Damen und Herren")).append("\n\n")
            append("data: ").append(JSONObject().put("briefampel", JSONObject().put("guthaben", 4))).append("\n\n")
        }
        web.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        val teile = withTimeout(5000) { provider.draft(LetterInput("Bitte zahlen."), analyse, "zahle bis Freitag", Tone.FREUNDLICH, Sender("Ana"), today).toList() }
        assertEquals("Sehr geehrte Damen und Herren", teile.last())
        assertEquals(4, server.guthabenStrom.value)
        web.takeRequest()
        val body = JSONObject(web.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("a7", body.getString("analyseId"))
        assertEquals("FREUNDLICH", body.getString("ton"))
        assertEquals("rot", body.getJSONObject("analyse").getString("ampel"))
    }

    @Test fun `antwort ohne Analyse vom Server bittet um neue Pruefung`() = runBlocking {
        val fremd = AnalysisJson.parse(analyseText)
        val fehler = runCatching { provider.draft(LetterInput("x"), fremd, "ok", Tone.SACHLICH, Sender(), today).toList() }.exceptionOrNull()
        assertTrue(fehler is ServerFehler)
        assertEquals(0, web.requestCount)
    }
}

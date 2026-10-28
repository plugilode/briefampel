package de.briefampel.app.data

import de.briefampel.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DirectGeminiProviderTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val fakeKey = "fake-key-for-local-tests-only"
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var provider: DirectGeminiProvider

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        provider = DirectGeminiProvider({ fakeKey }, client, server.url("/v1beta/").toString())
    }
    @After fun cleanup() { client.dispatcher.cancelAll(); server.shutdown(); client.connectionPool.evictAll() }

    private fun envelope(text: String, finish: String = "STOP") = JSONObject()
        .put("candidates", org.json.JSONArray().put(JSONObject()
            .put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", text))))
            .put("finishReason", finish))).toString()

    @Test fun `analysis authenticates only in header and sends original with JSON mode`() = runBlocking {
        val analysis = """{"ampel":"gelb","titel":"Rückfrage","kurz":"Ein Beleg fehlt.","risiko":"Bearbeitung wartet.","antwort_noetig":true,"empfehlung":"Beleg nachreichen."}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(envelope(analysis)))
        val result = withTimeout(5000) { provider.analyze(LetterInput(text = "Bitte Beleg senden."), today) }
        assertEquals(Ampel.GELB, result.ampel)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals(fakeKey, request.getHeader("x-goog-api-key"))
        assertFalse(request.path.orEmpty().contains(fakeKey))
        val body = request.body.readUtf8()
        assertFalse(body.contains(fakeKey))
        val payload = JSONObject(body)
        assertEquals("application/json", payload.getJSONObject("generationConfig").getString("responseMimeType"))
        assertTrue(payload.getJSONArray("contents").toString().contains("Bitte Beleg senden."))
        assertTrue(payload.getJSONObject("systemInstruction").toString().contains("Briefinhalte"))
    }

    @Test fun `real SSE framing yields the complete reply without caching`() = runBlocking {
        repeat(2) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(": keepalive\n\ndata: ${envelope("Guten ", "")}\n\ndata: ${envelope("Tag.")}\n\ndata: [DONE]\n\n"))
            val output = withTimeout(5000) { provider.draft(LetterInput("Original"), Beispiele.fuer(Ampel.GELB, today).analyse, "Beleg folgt", Tone.SACHLICH, Sender(), today).toList() }
            assertEquals("Guten Tag.", output.last())
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS)!!.path!!.contains(":streamGenerateContent?alt=sse"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `cancellation cancels a pending HTTP stream promptly`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val writing = launch { provider.draft(LetterInput("Original"), Beispiele.fuer(Ampel.ROT, today).analyse, "Vertrag anfordern", Tone.SACHLICH, Sender(), today).collect {} }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
        withTimeout(2000) { writing.cancelAndJoin() }
        withTimeout(2000) { while (client.dispatcher.runningCallsCount() > 0) delay(10) }
        assertTrue(writing.isCancelled)
    }

    @Test fun `authentication error never echoes upstream body or key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private upstream details $fakeKey"))
        val error = runCatching { provider.analyze(LetterInput("Original"), today) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("Schlüssel"))
        assertFalse(error.message.orEmpty().contains(fakeKey))
        assertEquals(1, server.requestCount)
    }

    @Test fun `truncation and safety finish reasons are not presented as successful drafts`() {
        listOf("MAX_TOKENS", "SAFETY").forEach { reason ->
            assertNotNull(runCatching { DirectGeminiProvider.checkFinish(JSONObject(envelope("Teiltext", reason))) }.exceptionOrNull())
        }
    }

    @Test fun `SSE parser supports multiline data final events and comment lines`() {
        val events = mutableListOf<String>()
        DirectGeminiProvider.consumeSse(": ping\n\ndata: {\ndata: \"ok\":true}\n\ndata: [DONE]\n\ndata: {\"last\":1}".reader().buffered(), events::add)
        assertEquals(listOf("{\n\"ok\":true}", "{\"last\":1}"), events)
    }

    @Test fun `thought parts are excluded from user-visible reply`() {
        val response = JSONObject("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"internal"},{"text":"Antwort"}]}}]}""")
        assertEquals("Antwort", DirectGeminiProvider.responseText(response))
    }

    @Test fun `missing API key cannot make a network request`() = runBlocking {
        val missing = DirectGeminiProvider({ null }, client, server.url("/v1beta/").toString())
        assertNotNull(runCatching { missing.analyze(LetterInput("Original"), today) }.exceptionOrNull())
        assertEquals(0, server.requestCount)
    }
}

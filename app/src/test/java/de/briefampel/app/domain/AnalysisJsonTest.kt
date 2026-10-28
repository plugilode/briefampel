package de.briefampel.app.domain

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class AnalysisJsonTest {
    private fun valid() = JSONObject("""{
        "ampel":"rot","titel":"Mahnung","absender":null,"datum":"14.10.2026",
        "aktenzeichen":"A-1","kurz":"Eine Zahlung wird verlangt.","verlangt":"42 €",
        "frist":{"datum":"29.10.2026","hinweis":"Laut Brief"},"ton":"drohend",
        "tonnotiz":"Letzte Aufforderung","risiko":"Konsequenzen angekündigt.",
        "antwort_noetig":true,"empfehlung":"Original prüfen.",
        "stichwort_vorschlaege":["Nachweis anfordern","Nachweis anfordern",null,7],"sprache":"de"
    }""")
    @Test fun `parses plain fenced and prefixed responses`() {
        val json = valid().toString()
        listOf(json, "```json\n$json\n```", "Hier das Ergebnis:\n$json\nEnde").forEach {
            val a = AnalysisJson.parse(it)
            assertEquals(Ampel.ROT, a.ampel)
            assertEquals("Mahnung", a.titel)
            assertNull(a.absender)
            assertEquals(listOf("Nachweis anfordern"), a.stichwortVorschlaege)
            assertEquals("29.10.2026", a.frist?.datum?.let(Fristen::format))
        }
    }
    @Test fun `unknown signal is yellow and invalid deadline stays unknown`() {
        val json = valid().put("ampel", "unbekannt").put("frist", JSONObject().put("datum", "31.02.2026").put("hinweis", "Unklar"))
        val a = AnalysisJson.parse(json.toString())
        assertEquals(Ampel.GELB, a.ampel)
        assertNull(a.frist?.datum)
        assertEquals("Unklar", a.frist?.hinweis)
    }
    @Test fun `missing optional fields are safe`() {
        val json = valid().apply { remove("frist"); remove("sprache"); remove("ton"); remove("stichwort_vorschlaege") }
        val a = AnalysisJson.parse(json.toString())
        assertNull(a.frist)
        assertEquals("de", a.sprache)
        assertTrue(a.stichwortVorschlaege.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun `truncated response is rejected`() {
        AnalysisJson.parse("```json\n{\"ampel\":\"rot\"")
    }
    @Test(expected = IllegalArgumentException::class) fun `missing summary is rejected`() {
        AnalysisJson.parse(valid().apply { remove("kurz") }.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun `object cannot be coerced to a summary`() {
        AnalysisJson.parse(valid().put("kurz", JSONObject().put("text", "Wrong type")).toString())
    }
    @Test(expected = IllegalArgumentException::class) fun `wrong boolean type is rejected instead of assuming no reply`() {
        AnalysisJson.parse(valid().put("antwort_noetig", "false").toString())
    }
    @Test fun `draft context encoding preserves newlines quotes and amount`() {
        val a = AnalysisJson.parse(valid().put("absender", "A \"B\"\nC").toString())
        val decoded = JSONObject(AnalysisJson.encode(a))
        assertEquals("A \"B\"\nC", decoded.getString("absender"))
        assertEquals("42 €", decoded.getString("verlangt"))
    }
}

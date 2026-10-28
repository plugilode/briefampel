package de.briefampel.app.domain

import org.json.JSONObject

object AnalysisJson {
    fun parse(raw: String): LetterAnalysis {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "Antwort war unvollständig. Bitte nochmal prüfen." }
        val json = JSONObject(raw.substring(start, end + 1))
        fun optional(key: String) = (json.opt(key) as? String)?.trim()?.takeUnless { it.isBlank() || it == "null" }
        fun required(key: String) = requireNotNull(optional(key)) { "Antwort war unvollständig. Bitte nochmal prüfen." }
        val deadline = json.optJSONObject("frist")
        require(json.has("antwort_noetig") && json.opt("antwort_noetig") is Boolean)
        return LetterAnalysis(
            ampel = Ampel.fromRaw(optional("ampel")),
            titel = required("titel"), absender = optional("absender"),
            datum = Fristen.parse(optional("datum")), aktenzeichen = optional("aktenzeichen"),
            kurz = required("kurz"), verlangt = optional("verlangt"),
            frist = deadline?.let { Frist(Fristen.parse(it.optString("datum")), it.optString("hinweis", "Frist selbst prüfen.")) },
            ton = optional("ton") ?: "neutral", tonnotiz = optional("tonnotiz") ?: "Keine Angabe.",
            risiko = required("risiko"), antwortNoetig = json.getBoolean("antwort_noetig"),
            empfehlung = required("empfehlung"),
            stichwortVorschlaege = json.optJSONArray("stichwort_vorschlaege")?.let { array ->
                (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.trim()?.takeIf(String::isNotBlank) }.distinct().take(8)
            }.orEmpty(),
            sprache = optional("sprache") ?: "de",
        )
    }

    fun encode(a: LetterAnalysis): String = JSONObject().apply {
        put("ampel", a.ampel.name.lowercase()); put("titel", a.titel); put("absender", a.absender)
        put("datum", a.datum?.let(Fristen::format)); put("aktenzeichen", a.aktenzeichen)
        put("kurz", a.kurz); put("verlangt", a.verlangt); put("risiko", a.risiko)
        put("empfehlung", a.empfehlung); put("sprache", a.sprache)
        put("ton", a.ton); put("tonnotiz", a.tonnotiz); put("antwort_noetig", a.antwortNoetig)
        put("stichwort_vorschlaege", org.json.JSONArray(a.stichwortVorschlaege))
        put("frist", a.frist?.let { JSONObject().put("datum", it.datum?.let(Fristen::format)).put("hinweis", it.hinweis) })
    }.toString()
}

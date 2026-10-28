package de.briefampel.app.ui

import de.briefampel.app.BuildConfig
import de.briefampel.app.domain.Fristen
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Only presentation data crosses into the offline renderer. API credentials never do. */
object ClaudeUiJson {
    fun encode(s: UiState): String = JSONObject().apply {
        put("screen", s.screen.name); put("sheet", s.sheet?.name ?: JSONObject.NULL)
        put("showIntro", s.showIntro); put("example", s.example); put("dark", s.dark)
        put("hasInput", s.input.hasContent); put("pages", s.input.pages)
        put("inputLabel", if (s.input.attachments.isEmpty()) "Text · ${s.input.text.length} Zeichen" else "bereit")
        put("files", JSONArray().apply { s.input.attachments.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        put("loading", s.loading); put("importing", s.importing); put("writing", s.writing)
        put("error", s.error ?: JSONObject.NULL); put("configured", s.configured)
        put("personalApi", s.personalApi); put("region", BuildConfig.AI_REGION)
        put("keywords", s.keywords); put("tone", s.tone.label); put("reply", s.reply)
        put("senderLabel", listOf(s.sender.name, s.sender.address.replace('\n', ' ')).filter(String::isNotBlank).joinToString(" · "))
        put("checkedAt", s.checkedAt)
        s.result?.analyse?.let { a ->
            put("analysis", JSONObject().apply {
                put("signal", a.ampel.name); put("title", a.titel); put("sender", a.absender ?: JSONObject.NULL)
                put("date", a.datum?.let(Fristen::format) ?: JSONObject.NULL); put("reference", a.aktenzeichen ?: JSONObject.NULL)
                put("summary", a.kurz); put("wants", a.verlangt ?: JSONObject.NULL)
                put("tone", a.ton); put("toneNote", a.tonnotiz); put("risk", a.risiko)
                put("recommendation", a.empfehlung); put("replyNeeded", a.antwortNoetig)
                put("deadlineNote", a.frist?.hinweis ?: JSONObject.NULL)
            })
            val date = a.frist?.datum
            put("deadlineDays", date?.let { Fristen.tageBis(it, LocalDate.now(ZoneId.of("Europe/Berlin"))) } ?: JSONObject.NULL)
            put("deadlineLabel", date?.let(Fristen::format) ?: JSONObject.NULL)
        }
    }.toString()
}

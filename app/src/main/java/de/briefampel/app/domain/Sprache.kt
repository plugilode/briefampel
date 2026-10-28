package de.briefampel.app.domain

import java.util.Locale

/** Sprachen der Oberfläche und der Zusammenfassung. Der Antwortbrief bleibt in der Sprache des Briefs. */
enum class Sprache(val code: String, val anzeige: String, val locale: Locale, val fuerKi: String, val nameFuerKi: String) {
    DE("de", "Deutsch", Locale.GERMAN, "deutscher", "Deutsch"),
    EN("en", "English", Locale.ENGLISH, "englischer", "Englisch"),
    ES("es", "Español", Locale.forLanguageTag("es"), "spanischer", "Spanisch");

    /** Text in dieser Sprache. Der deutsche Text ist zugleich der Schlüssel in [Uebersetzung]. */
    fun text(de: String, vararg args: Any?): String {
        val roh = when (this) {
            DE -> de
            EN -> Uebersetzung.en[de] ?: de
            ES -> Uebersetzung.es[de] ?: de
        }
        return if (args.isEmpty()) roh else String.format(locale, roh, *args)
    }

    fun analysen(anzahl: Int) = if (anzahl == 1) text("1 Analyse") else text("%d Analysen", anzahl)

    companion object {
        fun von(code: String?): Sprache? = entries.firstOrNull { it.code == code }
        /** Vorschlag beim ersten Start: die Sprache des Handys, sonst Deutsch. */
        fun vomGeraet(): Sprache = von(Locale.getDefault().language) ?: DE
    }
}

/** Markiert einen deutschen Text, der erst bei der Anzeige übersetzt wird. */
fun tr(de: String) = de

package de.briefampel.app.domain

import java.time.LocalDate

/** Ergebnis der Briefanalyse, Felder wie in Spezifikation 4.1. */
data class LetterAnalysis(
    val ampel: Ampel,
    val titel: String,
    val absender: String?,
    val datum: LocalDate?,
    val aktenzeichen: String?,
    val kurz: String,
    val verlangt: String?,
    val frist: Frist?,
    val ton: String,
    val tonnotiz: String,
    val risiko: String,
    val antwortNoetig: Boolean,
    val empfehlung: String,
    val stichwortVorschlaege: List<String>,
    val sprache: String,
)

data class Frist(val datum: LocalDate?, val hinweis: String)

/** Ein geprüfter Brief: Analyse plus der Volltext, der standardmäßig verborgen bleibt. */
data class GepruefterBrief(
    val analyse: LetterAnalysis,
    val volltext: String,
    val seiten: Int,
)

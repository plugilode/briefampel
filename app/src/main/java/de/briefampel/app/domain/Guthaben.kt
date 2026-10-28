package de.briefampel.app.domain

/**
 * Preise und Regeln fürs Guthaben (siehe ABRECHNUNG.md).
 * Eine Analyse = ein geprüfter Brief plus bis zu [ANTWORTEN_JE_ANALYSE] Antwortentwürfe.
 */
object Guthaben {
    const val START = 3
    const val ANTWORTEN_JE_ANALYSE = 3

    data class Paket(val id: String, val analysen: Int, val preis: String, val jeAnalyse: String, val marke: String? = null)

    val pakete = listOf(
        Paket("analysen_10", 10, "1,99 €", "≈ 20 ct je Brief"),
        Paket("analysen_30", 30, "4,99 €", "≈ 17 ct je Brief", marke = "BELIEBT"),
        Paket("analysen_100", 100, "9,99 €", "≈ 10 ct je Brief", marke = "AM GÜNSTIGSTEN"),
    )

    /** Wie viele Antworten zu einem Brief noch ohne neue Analyse gehen. */
    fun freieAntworten(bezahlteAnalysen: Int, geschriebeneAntworten: Int): Int =
        (bezahlteAnalysen * ANTWORTEN_JE_ANALYSE - geschriebeneAntworten).coerceAtLeast(0)

    fun analysenText(anzahl: Int) = if (anzahl == 1) "1 Analyse" else "$anzahl Analysen"
}

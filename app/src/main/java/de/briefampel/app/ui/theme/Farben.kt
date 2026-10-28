package de.briefampel.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import de.briefampel.app.domain.Ampel

/**
 * Konzept „Nur die Ampel hat Farbe“: Grund, Flächen und Text sind neutral,
 * Farbe kommt ausschließlich aus den drei Lampen.
 */
@Immutable
data class BriefampelFarben(
    val grund: Color,
    val flaeche: Color,
    val linie: Color,
    val tinte: Color,
    val tinteLeise: Color,
    val tinteHinweis: Color,
    val knopf: Color,
    val aufKnopf: Color,
    val papier: Color,
    val aufPapier: Color,
    val rotText: Color,
    val gelbText: Color,
    val gruenText: Color,
    val istDunkel: Boolean,
) {
    fun textFarbe(ampel: Ampel): Color = when (ampel) {
        Ampel.ROT -> rotText
        Ampel.GELB -> gelbText
        Ampel.GRUEN -> gruenText
    }
}

/** Lampen- und Figurfarben: in beiden Modi gleich, die Ampel bleibt immer eine echte Ampel. */
object Signal {
    val Rot = Color(0xFFFF5A47)
    val Gelb = Color(0xFFF5B544)
    val Gruen = Color(0xFF3DD68C)

    val Kreide = Color(0xFFF3F0E8)
    val Augenweiss = Color(0xFFFBF8F1)
    val Pupille = Color(0xFF17191C)
    val Wange = Color(0xFFFF806E)
    val Hutkrempe = Color(0xFFE4DFD4)
    val GehaeuseOben = Color(0xFF23262B)
    val GehaeuseUnten = Color(0xFF0E0F11)
    val GehaeuseKante = Color(0xFF2A2D32)

    fun farbe(ampel: Ampel): Color = when (ampel) {
        Ampel.ROT -> Rot
        Ampel.GELB -> Gelb
        Ampel.GRUEN -> Gruen
    }
}

val DunkleFarben = BriefampelFarben(
    grund = Color(0xFF121417),
    flaeche = Color(0xFF1A1D21),
    linie = Color(0xFF2A2E34),
    tinte = Color(0xFFF3F0E8),
    tinteLeise = Color(0xFFA3A8AF),
    tinteHinweis = Color(0xFF8E949B),
    knopf = Color(0xFFF3F0E8),
    aufKnopf = Color(0xFF121417),
    papier = Color(0xFFF3F0E8),
    aufPapier = Color(0xFF1E2126),
    rotText = Signal.Rot,
    gelbText = Signal.Gelb,
    gruenText = Signal.Gruen,
    istDunkel = true,
)

val HelleFarben = BriefampelFarben(
    grund = Color(0xFFECEEF1),
    flaeche = Color(0xFFFFFFFF),
    linie = Color(0xFFD3D6DB),
    tinte = Color(0xFF15181C),
    tinteLeise = Color(0xFF4E545C),
    tinteHinweis = Color(0xFF5E646C),
    knopf = Color(0xFF15181C),
    aufKnopf = Color(0xFFF3F0E8),
    papier = Color(0xFFFFFFFF),
    aufPapier = Color(0xFF1E2126),
    // Dunklere Textvarianten, damit sie auf hellem Grund lesbar bleiben (Kontrast ≥ 4.5:1).
    rotText = Color(0xFFB8321F),
    gelbText = Color(0xFF8A5200),
    gruenText = Color(0xFF15703F),
    istDunkel = false,
)

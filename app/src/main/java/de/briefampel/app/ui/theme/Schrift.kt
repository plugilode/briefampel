package de.briefampel.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.briefampel.app.R

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, opsz: Float) = Font(
    R.font.bricolage_grotesque,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun sourceSans(weight: Int) = Font(
    R.font.source_sans_3,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Bricolage = FontFamily(bricolage(600, 48f), bricolage(700, 48f), bricolage(800, 96f))
val SourceSans = FontFamily(sourceSans(400), sourceSans(600), sourceSans(700))
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Typo-Skala: großer Sprung zwischen Anzeige und Text, Mono nur für Daten. */
object Schrift {
    val Anzeige = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.035).em)
    val Wortmarke = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, letterSpacing = (-0.02).em)
    val Titel = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em)
    val ZahlGross = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val Text = TextStyle(fontFamily = SourceSans, fontWeight = FontWeight.Normal, fontSize = 16.5.sp, lineHeight = 24.sp)
    val TextKlein = TextStyle(fontFamily = SourceSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val Knopf = TextStyle(fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val KnopfKlein = TextStyle(fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Fuss = TextStyle(fontFamily = SourceSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Marke = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, letterSpacing = 0.1.em)
    val Daten = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
}

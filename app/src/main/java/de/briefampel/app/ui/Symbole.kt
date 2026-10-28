package de.briefampel.app.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** Schlanke Strich-Symbole im Stil des Designs, statt Emoji oder Standard-Icon-Satz. */
object Symbole {
    val Scannen = strich("Scannen", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3", "M7 12h10")
    val Datei = strich("Datei", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5")
    val Einfuegen = strich("Einfügen", "M9 3h6v3H9z", "M15 4.5h2a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6.5a2 2 0 0 1 2-2h2", "M9 12h6M9 16h4")
    val Person = strich("Person", "M12 4a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Zurueck = strich("Zurück", "M15 6l-6 6 6 6")
    val Weiter = strich("Weiter", "M9 6l6 6-6 6")
    val Pfeil = strich("Pfeil", "M5 12h14M13 6l6 6-6 6")
    val Stift = strich("Stift", "M4 20l4-1L19 8l-3-3L5 16z", "M14 7l3 3")
    val Haken = strich("Haken", "M5 12.5l4.5 4.5L19 7.5")
    val Umschlag = strich("Umschlag", "M3 10l9-6 9 6v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z", "M3 10l9 6 9-6")
    val Teilen = strich("Teilen", "M12 15V3M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6")
    val Kopieren = strich("Kopieren", "M9 9h10v12H9z", "M5 15V3h10")
    val Plus = strich("Plus", "M12 5v14M5 12h14")
    val Schliessen = strich("Schließen", "M6 6l12 12M18 6L6 18")

    private fun strich(name: String, vararg pfade: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            pfade.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.9f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}

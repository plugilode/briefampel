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
    val Zahnrad = strich(
        "Einstellungen",
        "M12 15a3 3 0 1 0 0-6a3 3 0 0 0 0 6z",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33a1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
    )
    val Verlauf = strich("Verlauf", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 7v5l3 2")
    val Fingerabdruck = strich(
        "Fingerabdruck",
        "M4.5 9.5a8 8 0 0 1 15 0",
        "M6.5 12a5.5 5.5 0 0 1 11 0c0 2.5-.5 4.5-1.5 6.5",
        "M9 19c.8-1.6 1.2-3.4 1.2-5.5a1.8 1.8 0 0 1 3.6 0c0 1.2-.1 2.3-.4 3.4",
    )
    val Ruecktaste = strich("Löschen", "M9 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9l-5-6z", "M12 10l4 4M16 10l-4 4")
    val Schloss = strich("Schloss", "M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")

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

package de.briefampel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.briefampel.app.domain.Sprache
import de.briefampel.app.ui.figur.AmpelFreundin
import de.briefampel.app.ui.figur.Stimmung
import de.briefampel.app.ui.theme.*

val LocalSprache = staticCompositionLocalOf { Sprache.DE }

/** Deutscher Text, angezeigt in der gewählten Sprache. Platzhalter wie bei String.format. */
@Composable
@ReadOnlyComposable
internal fun sag(de: String, vararg args: Any?): String = LocalSprache.current.text(de, *args)

@Composable
@ReadOnlyComposable
internal fun analysenText(anzahl: Int): String = LocalSprache.current.analysen(anzahl)

private val gruss = mapOf(Sprache.DE to "Hallo", Sprache.EN to "Hello", Sprache.ES to "Hola")
private val frage = mapOf(Sprache.DE to "Welche Sprache?", Sprache.EN to "Which language?", Sprache.ES to "¿Qué idioma?")

/**
 * Allererster Bildschirm: Sprache wählen, bevor die Ampel etwas erzählt.
 * Die Frage steht in allen drei Sprachen, die Handysprache ist vorgewählt.
 */
@Composable
internal fun SprachWahl(vorschlag: Sprache, gewaehlt: (Sprache) -> Unit) {
    val c = LocalFarben.current
    Box(Modifier.fillMaxSize().background(c.grund).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 40.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AmpelFreundin(Stimmung.GRUEN, "Briefampel", lampe = 46.dp, hutLuepfen = 1)
                Spacer(Modifier.width(22.dp))
                Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Sprache.entries.forEach { sp ->
                        Text("${gruss.getValue(sp)}.", style = Schrift.Anzeige, color = if (sp == vorschlag) c.tinte else c.tinteLeise)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(Sprache.entries.joinToString(" · ") { frage.getValue(it) }, style = Schrift.TextKlein, color = c.tinteLeise)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Vorschlag zuerst: Wer die Handysprache will, tippt einfach oben.
                (listOf(vorschlag) + Sprache.entries.filter { it != vorschlag }).forEach { sp ->
                    SprachKnopf(sp, hervorgehoben = sp == vorschlag) { gewaehlt(sp) }
                }
            }
        }
    }
}

@Composable
private fun SprachKnopf(sp: Sprache, hervorgehoben: Boolean, onClick: () -> Unit) {
    val c = LocalFarben.current
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (hervorgehoben) c.knopf else Color.Transparent, contentColor = if (hervorgehoben) c.aufKnopf else c.tinte,
        border = if (hervorgehoben) null else BorderStroke(1.dp, c.linie),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sp.anzeige, style = Schrift.KnopfKlein.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(sp.code.uppercase(), style = Schrift.Marke, color = if (hervorgehoben) c.aufKnopf.copy(alpha = 0.6f) else c.tinteHinweis)
            Spacer(Modifier.width(12.dp))
            Icon(Symbole.Weiter, null, Modifier.size(18.dp))
        }
    }
}

/** Umschalter in den Einstellungen: drei Felder, die gewählte Sprache ist gefüllt. */
@Composable
internal fun SprachSchalter(aktuell: Sprache, waehlen: (Sprache) -> Unit) {
    val c = LocalFarben.current
    Row(Modifier.fillMaxWidth().background(c.flaeche, RoundedCornerShape(26.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Sprache.entries.forEach { sp ->
            val an = sp == aktuell
            Box(
                Modifier.weight(1f).height(44.dp).background(if (an) c.knopf else Color.Transparent, CircleShape)
                    .selectable(an, role = Role.RadioButton) { waehlen(sp) },
                contentAlignment = Alignment.Center,
            ) {
                Text(sp.anzeige, style = Schrift.KnopfKlein.copy(fontWeight = if (an) FontWeight.Bold else FontWeight.SemiBold), color = if (an) c.aufKnopf else c.tinteLeise)
            }
        }
    }
}

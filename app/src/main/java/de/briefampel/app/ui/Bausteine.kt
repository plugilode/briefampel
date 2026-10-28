package de.briefampel.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.briefampel.app.domain.Sender
import de.briefampel.app.ui.theme.LocalFarben
import de.briefampel.app.ui.theme.Schrift

enum class PillenArt { HAUPT, NEBEN }

/** Pillenförmiger Knopf. Haupt = kreideweiß gefüllt, Neben = feine Kontur. Gibt beim Drücken leicht nach. */
@Composable
internal fun Pille(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    art: PillenArt = PillenArt.HAUPT,
    symbol: ImageVector? = null,
    enabled: Boolean = true,
    hoehe: Dp = if (art == PillenArt.HAUPT) 64.dp else 56.dp,
) {
    val c = LocalFarben.current
    val interaktion = remember { MutableInteractionSource() }
    val gedrueckt by interaktion.collectIsPressedAsState()
    val skala by animateFloatAsState(if (gedrueckt) 0.97f else 1f, spring(0.5f, 700f), label = "Druck")
    val haupt = art == PillenArt.HAUPT
    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { scaleX = skala; scaleY = skala }
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = hoehe),
        enabled = enabled,
        shape = CircleShape,
        color = if (haupt) c.knopf else Color.Transparent,
        contentColor = if (haupt) c.aufKnopf else c.tinte,
        border = if (haupt) null else BorderStroke(1.dp, c.linie),
        interactionSource = interaktion,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(text, style = if (haupt) Schrift.Knopf else Schrift.KnopfKlein.copy(fontSize = 16.sp))
        }
    }
}

/** Kompatible Kurzform für die Sheets. */
@Composable
internal fun ActionButton(text: String, click: () -> Unit, primary: Boolean = true, enabled: Boolean = true) =
    Pille(text, click, art = if (primary) PillenArt.HAUPT else PillenArt.NEBEN, enabled = enabled, hoehe = 54.dp)

/** Runder Symbolknopf (Zurück, Einstellungen). */
@Composable
internal fun RundKnopf(symbol: ImageVector, beschreibung: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LocalFarben.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = c.tinte,
        border = BorderStroke(1.dp, c.linie),
        modifier = Modifier.size(44.dp).alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(symbol, contentDescription = beschreibung, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
internal fun Eyebrow(text: String, color: Color = LocalFarben.current.tinteHinweis, modifier: Modifier = Modifier) =
    Text(text, style = Schrift.Marke, color = color, modifier = modifier)

/** Eine Zeile der Faktenliste: Marke links in Mono, Aussage rechts. */
@Composable
internal fun FaktZeile(label: String, text: String, betont: Boolean = false, letzte: Boolean = false) {
    val c = LocalFarben.current
    Column {
        HorizontalDivider(color = c.linie)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text(label, style = Schrift.Marke, color = if (betont) c.tinte else c.tinteHinweis, modifier = Modifier.width(100.dp).padding(top = 4.dp))
            Text(text, style = Schrift.Text, color = c.tinte, fontWeight = if (betont) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
        }
        if (letzte) HorizontalDivider(color = c.linie)
    }
}

@Composable
internal fun Feld(
    wert: String,
    aendern: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    minLines: Int = 1,
    singleLine: Boolean = false,
) {
    val c = LocalFarben.current
    OutlinedTextField(
        wert, aendern, modifier = modifier, enabled = enabled, label = { Text(label) }, minLines = minLines, singleLine = singleLine,
        textStyle = Schrift.Text, shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.tinte, unfocusedTextColor = c.tinte, disabledTextColor = c.tinteLeise,
            focusedContainerColor = c.flaeche, unfocusedContainerColor = c.flaeche, disabledContainerColor = c.flaeche,
            focusedBorderColor = c.tinteLeise, unfocusedBorderColor = c.linie, disabledBorderColor = c.linie,
            focusedLabelColor = c.tinteLeise, unfocusedLabelColor = c.tinteHinweis, cursorColor = c.tinte,
        ),
    )
}

@Composable
internal fun SenderFields(sender: Sender, change: (Sender) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Feld(sender.name, { change(sender.copy(name = it.take(200))) }, sag("Name"), enabled = enabled, singleLine = true)
        Feld(sender.address, { change(sender.copy(address = it.take(500))) }, sag("Anschrift"), enabled = enabled, minLines = 2)
        Feld(sender.email, { change(sender.copy(email = it.take(200))) }, sag("E-Mail (optional)"), enabled = enabled, singleLine = true)
    }
}

@Composable
internal fun FehlerKarte(text: String, schliessen: () -> Unit) {
    val c = LocalFarben.current
    Surface(
        color = c.flaeche,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(1.dp, c.rotText.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = Schrift.TextKlein, color = c.tinte, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            RundKnopfFlach("OK", schliessen)
        }
    }
}

@Composable
private fun RundKnopfFlach(text: String, onClick: () -> Unit) {
    val c = LocalFarben.current
    Surface(onClick = onClick, color = Color.Transparent, contentColor = c.tinte, shape = CircleShape, modifier = Modifier.padding(4.dp).heightIn(min = 44.dp)) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text(text, style = Schrift.KnopfKlein) }
    }
}

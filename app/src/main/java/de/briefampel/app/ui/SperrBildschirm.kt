package de.briefampel.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.briefampel.app.ui.figur.AmpelFreundin
import de.briefampel.app.ui.figur.Stimmung
import de.briefampel.app.ui.theme.Bricolage
import de.briefampel.app.ui.theme.LocalFarben
import de.briefampel.app.ui.theme.LocalWenigBewegung
import de.briefampel.app.ui.theme.Schrift

private enum class Schritt { ERSTE, BESTAETIGEN, BIOMETRIE }

/**
 * Lokale Anmeldung: beim ersten Mal eine PIN festlegen (optional Biometrie), danach entsperren.
 * @param biometrie startet die Abfrage per Fingerabdruck/Gesicht, null wenn das Gerät das nicht kann.
 */
@Composable
internal fun SperrBildschirm(s: UiState, model: BriefampelViewModel, biometrie: (() -> Unit)?) {
    val c = LocalFarben.current
    val sprache = LocalSprache.current
    val einrichten = s.sperre == Sperre.EINRICHTEN
    var schritt by remember(s.sperre) { mutableStateOf(Schritt.ERSTE) }
    var eingabe by remember(s.sperre) { mutableStateOf("") }
    var erste by remember(s.sperre) { mutableStateOf("") }
    var meldung by remember(s.sperre) { mutableStateOf<String?>(null) }
    var vergessen by remember { mutableStateOf(false) }
    val wackeln = remember { Animatable(0f) }
    val wenig = LocalWenigBewegung.current
    val angezeigt = meldung ?: s.pinMeldung

    LaunchedEffect(angezeigt) {
        if (angezeigt != null && !wenig) wackeln.animateTo(0f, keyframes { durationMillis = 360; -14f at 60; 12f at 130; -8f at 200; 4f at 270 })
    }
    // Erst nach dem Kurz-Intro nach dem Fingerabdruck fragen, sonst liegt der Systemdialog über der Animation.
    LaunchedEffect(s.sperre, s.kurzIntro) {
        if (s.sperre == Sperre.GESPERRT && s.biometrie && !s.kurzIntro) biometrie?.invoke()
    }

    fun ziffer(z: Char) {
        if (eingabe.length >= 6 || schritt == Schritt.BIOMETRIE) return
        meldung = null
        eingabe += z
        if (eingabe.length < 6) return
        val pin = eingabe
        eingabe = ""
        when {
            !einrichten -> model.pinPruefen(pin)
            schritt == Schritt.ERSTE -> { erste = pin; schritt = Schritt.BESTAETIGEN }
            pin != erste -> { meldung = sprache.text("Die PINs stimmen nicht überein. Bitte neu festlegen."); erste = ""; schritt = Schritt.ERSTE }
            biometrie != null -> schritt = Schritt.BIOMETRIE
            else -> model.pinEinrichten(pin)
        }
    }

    Box(
        Modifier.fillMaxSize().background(c.grund)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                AmpelFreundin(
                    if (angezeigt != null) Stimmung.ROT else Stimmung.WARTET,
                    sag("Briefampel bewacht deine Briefe"),
                    lampe = 34.dp,
                    hutLuepfen = schritt.ordinal,
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f).padding(top = 22.dp)) {
                    val (kopf, unter) = when {
                        !einrichten -> sag("Schön, dass\ndu da bist.") to sag("Gib deine PIN ein. Deine Briefe bleiben nur für dich.")
                        schritt == Schritt.ERSTE -> sag("Deine Briefe,\nnur für dich.") to sag("Leg eine 6-stellige PIN fest. Sie schützt die App und deinen Verlauf.")
                        schritt == Schritt.BESTAETIGEN -> sag("Nochmal,\nzur Sicherheit.") to sag("Gib dieselbe PIN noch einmal ein.")
                        else -> sag("Mit Finger oder\nGesicht öffnen?") to sag("Dann reicht ein Blick oder ein Tippen. Die PIN bleibt als Ersatz.")
                    }
                    AnimatedContent(kopf, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "Kopf") {
                        Text(it, style = Schrift.Anzeige.copy(fontSize = 32.sp, lineHeight = 33.sp), color = c.tinte)
                    }
                    Text(unter, style = Schrift.TextKlein, color = c.tinteLeise, modifier = Modifier.padding(top = 12.dp))
                }
            }

            Spacer(Modifier.height(36.dp))
            if (schritt == Schritt.BIOMETRIE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pille(sag("Ja, gern"), { model.pinEinrichten(erste); model.biometrie(true) }, symbol = Symbole.Fingerabdruck)
                    Pille(sag("Nur mit PIN"), { model.pinEinrichten(erste); model.biometrie(false) }, art = PillenArt.NEBEN)
                }
            } else {
                Row(
                    Modifier.graphicsLayer { translationX = wackeln.value * density }
                        .semantics { contentDescription = sprache.text("%d von 6 Ziffern eingegeben", eingabe.length) },
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    repeat(6) { i ->
                        val voll = i < eingabe.length
                        val farbe by animateColorAsState(if (voll) c.tinte else Color.Transparent, label = "Punkt")
                        Box(Modifier.size(14.dp).background(farbe, CircleShape).border(1.5.dp, if (voll) c.tinte else c.linie, CircleShape))
                    }
                }
                Text(
                    angezeigt ?: " ", style = Schrift.TextKlein, color = c.rotText, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp).heightIn(min = 42.dp),
                )
                Ziffernblock(
                    ziffer = ::ziffer,
                    zurueck = { if (eingabe.isNotEmpty()) eingabe = eingabe.dropLast(1) },
                    biometrie = if (!einrichten && s.biometrie) biometrie else null,
                )
                if (!einrichten) {
                    Text(
                        sag("PIN vergessen?"), style = Schrift.KnopfKlein, color = c.tinteLeise,
                        modifier = Modifier.padding(top = 18.dp).clip(CircleShape).clickable { vergessen = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (vergessen) AlertDialog(
        onDismissRequest = { vergessen = false },
        title = { Text(sag("PIN vergessen?")) },
        text = { Text(sag("Ohne PIN kommt niemand an deinen Verlauf – auch du nicht. Wir löschen die PIN und alle gespeicherten Briefe. Danach legst du eine neue PIN fest.")) },
        confirmButton = { TextButton(onClick = { vergessen = false; model.pinVergessen() }) { Text(sag("Löschen und neu anlegen"), color = c.rotText) } },
        dismissButton = { TextButton(onClick = { vergessen = false }) { Text(sag("Abbrechen"), color = c.tinte) } },
        containerColor = c.flaeche, titleContentColor = c.tinte, textContentColor = c.tinteLeise,
    )
}

@Composable
private fun Ziffernblock(ziffer: (Char) -> Unit, zurueck: () -> Unit, biometrie: (() -> Unit)?) {
    val reihen = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reihen.forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { reihe.forEach { z -> Taste(z.toString(), sag("Ziffer %s", z)) { ziffer(z) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (biometrie != null) Taste(null, sag("Mit Fingerabdruck oder Gesicht entsperren"), Symbole.Fingerabdruck, onClick = biometrie)
            else Spacer(Modifier.size(72.dp))
            Taste("0", sag("Ziffer %s", 0)) { ziffer('0') }
            Taste(null, sag("Letzte Ziffer löschen"), Symbole.Ruecktaste, onClick = zurueck)
        }
    }
}

@Composable
private fun Taste(text: String?, beschreibung: String, symbol: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val c = LocalFarben.current
    Surface(
        onClick = onClick, shape = CircleShape, color = if (text != null) c.flaeche else Color.Transparent, contentColor = c.tinte,
        border = if (text != null) BorderStroke(1.dp, c.linie) else null,
        modifier = Modifier.size(72.dp).semantics { contentDescription = beschreibung },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text != null) Text(text, style = Schrift.Anzeige.copy(fontFamily = Bricolage, fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = 0.sp))
            else symbol?.let { Icon(it, null, Modifier.size(26.dp)) }
        }
    }
}

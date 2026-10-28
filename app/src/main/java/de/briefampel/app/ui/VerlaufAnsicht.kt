package de.briefampel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.briefampel.app.data.GespeicherteAntwort
import de.briefampel.app.data.VerlaufEintrag
import de.briefampel.app.domain.Fristen
import de.briefampel.app.ui.figur.AmpelFreundin
import de.briefampel.app.ui.figur.Stimmung
import de.briefampel.app.ui.theme.LocalFarben
import de.briefampel.app.ui.theme.Schrift
import de.briefampel.app.ui.theme.Signal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone = ZoneId.of("Europe/Berlin")
internal fun tagDes(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

/** Alle geprüften Briefe, neueste zuerst. Nur auf diesem Gerät, verschlüsselt. */
@Composable
internal fun VerlaufAnsicht(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    var allesLoeschen by remember { mutableStateOf(false) }
    val heute = LocalDate.now(zone)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Eyebrow(sag("MEINE BRIEFE · NUR AUF DIESEM HANDY"), modifier = Modifier.padding(top = 4.dp))
        Text(sag("Alles, was ich\nfür dich gelesen\nhabe."), style = Schrift.Anzeige.copy(fontSize = 34.sp, lineHeight = 35.sp), color = c.tinte, modifier = Modifier.padding(top = 14.dp))
        Text(
            when (s.verlauf.size) { 0 -> sag("Noch leer."); 1 -> sag("1 Brief · verschlüsselt gespeichert · Originale werden nicht aufbewahrt"); else -> sag("%d Briefe · verschlüsselt gespeichert · Originale werden nicht aufbewahrt", s.verlauf.size) },
            style = Schrift.TextKlein, color = c.tinteLeise, modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (s.verlauf.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmpelFreundin(Stimmung.WARTET, sag("Briefampel wartet auf deinen ersten Brief"), lampe = 30.dp)
                Spacer(Modifier.width(18.dp))
                Text(sag("Sobald ich einen Brief für dich geprüft habe, liegt er hier – mit Ampel, Frist und deinen Antworten."), style = Schrift.Text, color = c.tinteLeise, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            Pille(sag("Brief prüfen"), model::back, symbol = Symbole.Scannen)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.verlauf.forEach { e -> Eintrag(e, heute) { model.eintragOeffnen(e.id) } }
            }
            Text(
                sag("Alle Briefe löschen"), style = Schrift.KnopfKlein, color = c.rotText, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).clip(CircleShape)
                    .selectable(false, role = Role.Button) { allesLoeschen = true }.padding(vertical = 14.dp),
            )
        }
    }
    if (allesLoeschen) AlertDialog(
        onDismissRequest = { allesLoeschen = false },
        title = { Text(sag("Alle Briefe löschen?")) },
        text = { Text(sag("Alle %d geprüften Briefe und deine Antworten verschwinden von diesem Handy. Das lässt sich nicht rückgängig machen.", s.verlauf.size)) },
        confirmButton = { TextButton(onClick = { allesLoeschen = false; model.verlaufLoeschen() }) { Text(sag("Alles löschen"), color = c.rotText) } },
        dismissButton = { TextButton(onClick = { allesLoeschen = false }) { Text(sag("Behalten"), color = c.tinte) } },
        containerColor = c.flaeche, titleContentColor = c.tinte, textContentColor = c.tinteLeise,
    )
}

@Composable
private fun Eintrag(e: VerlaufEintrag, heute: LocalDate, oeffnen: () -> Unit) {
    val c = LocalFarben.current
    val a = e.analyse
    val signal = Signal.farbe(a.ampel)
    Surface(onClick = oeffnen, color = c.flaeche, contentColor = c.tinte, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, c.linie)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).shadow(8.dp, CircleShape, ambientColor = signal, spotColor = signal).background(signal, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(a.titel, style = Schrift.KnopfKlein.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                a.absender?.let {
                    Text(it, style = Schrift.Daten.copy(fontSize = 12.sp), color = c.tinteHinweis, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val frist = a.frist?.datum
                val zeilen = listOfNotNull(
                    sag("geprüft %s", tagDes(e.geprueftAm).format(DateTimeFormatter.ofPattern("dd.MM."))),
                    frist?.let { sag("Frist %s", Fristen.restText(it, heute, LocalSprache.current)) },
                    e.antworten.size.takeIf { it > 0 }?.let { if (it == 1) sag("1 Antwort") else sag("%d Antworten", it) },
                )
                Text(
                    zeilen.joinToString(" · "), style = Schrift.TextKlein,
                    color = if (frist != null && Fristen.istDringend(frist, heute) && !frist.isBefore(heute)) c.rotText else c.tinteLeise,
                )
            }
            Icon(Symbole.Weiter, null, tint = c.tinteHinweis, modifier = Modifier.size(18.dp))
        }
    }
}

/** Die zu einem Brief gespeicherten Antworten, auf Papier wie im Antwort-Bildschirm. */
@Composable
internal fun GespeicherteAntworten(antworten: List<GespeicherteAntwort>, kopieren: (String) -> Unit, teilen: (String) -> Unit) {
    val c = LocalFarben.current
    val datum = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(sag("DEINE ANTWORTEN · %d", antworten.size))
        antworten.asReversed().forEach { a ->
            Surface(color = c.papier, shape = RoundedCornerShape(6.dp), shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${sag(a.ton).uppercase(LocalSprache.current.locale)} · ${Instant.ofEpochMilli(a.am).atZone(zone).format(datum)}", style = Schrift.Marke, color = c.aufPapier.copy(alpha = 0.6f))
                    Text(a.text, style = Schrift.Text.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.aufPapier, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KleinKnopf(sag("Kopieren"), Symbole.Kopieren) { kopieren(a.text) }
                        KleinKnopf(sag("Teilen"), Symbole.Teilen) { teilen(a.text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KleinKnopf(text: String, symbol: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalFarben.current
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, contentColor = c.aufPapier, border = BorderStroke(1.dp, c.aufPapier.copy(alpha = 0.25f)), modifier = Modifier.heightIn(min = 44.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = Schrift.KnopfKlein)
        }
    }
}

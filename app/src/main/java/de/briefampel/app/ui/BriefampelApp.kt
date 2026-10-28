package de.briefampel.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.*
import de.briefampel.app.domain.*
import de.briefampel.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LegacyComposeApp(model: BriefampelViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    BriefampelTheme(dunkel = state.dark) {
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.import(it) }
        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }?.let { model.import(it) }
            }
        }
        val scan: () -> Unit = {
            val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(10)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(context as Activity)
                .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { model.error("Scannen ist hier nicht verfügbar. Bitte ein Foto oder PDF über Datei auswählen.") }
        }
        BackHandler(state.sheet != null || state.screen != Screen.INPUT || state.loading || state.writing) { model.back() }
        val reduced = LocalWenigBewegung.current
        LaunchedEffect(state.screen, state.result) {
            if (state.screen == Screen.RESULT && !state.example && !reduced) {
                val vibrator = context.getSystemService(Vibrator::class.java)
                val duration = when (state.result?.analyse?.ampel) { Ampel.ROT -> 65L; Ampel.GELB -> 40L; else -> 20L }
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        BriefampelLayout(state, model, snackbar, scan,
            file = { files.launch(arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")) },
            copy = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Antwortbrief", state.reply))
                scope.launch { snackbar.showSnackbar("Antwort kopiert") }
            }, share = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, state.reply) }, "Antwort teilen"))
            })
    }
}

/** Same screen layout for the live activity and host-rendered screenshots. */
@Composable
internal fun BriefampelLayout(
    state: UiState,
    model: BriefampelViewModel,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    scan: () -> Unit = {},
    file: () -> Unit = {},
    copy: () -> Unit = {},
    share: () -> Unit = {},
    initialScroll: Int = 0,
    initialSheetScroll: Int = 0,
) {
        val colors = LocalFarben.current
        Scaffold(
            containerColor = colors.grund,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { Column(Modifier.fillMaxWidth().background(colors.grund).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Sortiert und entwirft. Ersetzt keine Rechtsberatung. Fristen immer selbst gegenprüfen.", style = Schrift.Fuss, color = colors.tinteHinweis)
            } },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    AppHeader(state, model)
                    state.error?.let { ErrorCard(it, model::clearError) }
                    AnimatedContent(state.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "Ansicht", modifier = Modifier.weight(1f)) { screen ->
                        when (screen) {
                            Screen.INPUT -> InputScreen(state, model, scan, file)
                            Screen.RESULT -> ResultScreen(state, model, initialScroll)
                            Screen.REPLY -> ReplyScreen(state, model, copy, share, initialScroll)
                        }
                    }
                }
            }
        }
        AppSheets(state, model, initialSheetScroll)
}

@Composable
private fun AppHeader(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (s.screen != Screen.INPUT) TextButton(onClick = model::back, enabled = !s.importing) { Text("‹ Zurück", color = c.tinte) }
        else Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(Signal.Rot, Signal.Gelb, Signal.Gruen).forEach { Box(Modifier.size(5.dp).background(it, CircleShape)) }
            }
            Text("Briefampel", style = Schrift.Wortmarke, color = c.tinte)
        }
        if (s.screen != Screen.INPUT) Spacer(Modifier.weight(1f))
        if (!largeText) TextButton(onClick = { model.sheet(Sheet.SETTINGS) }, enabled = !s.loading && !s.writing && !s.importing) { Text("Einstellungen", style = Schrift.KnopfKlein, color = c.tinteLeise) }
        }
        if (largeText) TextButton(onClick = { model.sheet(Sheet.SETTINGS) }, enabled = !s.loading && !s.writing && !s.importing) { Text("Einstellungen", style = Schrift.KnopfKlein, color = c.tinteLeise) }
    }
}

@Composable
private fun InputScreen(s: UiState, model: BriefampelViewModel, scan: () -> Unit, file: () -> Unit) {
    val c = LocalFarben.current
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TrafficLight(null, Modifier.width(110.dp).height(275.dp), reading = s.loading)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Eyebrow(if (s.loading) "EINEN MOMENT" else "DEINE POST. WENIGER KOPFKINO.")
                Spacer(Modifier.height(12.dp))
                Text(if (s.loading) "Ich lese.\nDu atmest." else "Gib her.\nIch lese\nzuerst.", style = Schrift.Anzeige, color = c.tinte)
                Spacer(Modifier.height(12.dp))
                Text(if (s.loading) "Ich schaue nach, worum es geht und ob du etwas tun musst." else "Ich bin da. Du siehst nur die Ampel – und was zu tun ist.", style = Schrift.TextKlein, color = c.tinteLeise)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!s.configured) {
            Surface(shape = RoundedCornerShape(16.dp), color = c.flaeche) {
                Column(Modifier.padding(16.dp)) {
                    Eyebrow("VORSCHAU · KI NOCH NICHT VERBUNDEN")
                    Text(if (s.personalApiSupported) "Sieh dir die Beispiele an oder hinterlege deinen Gemini-Schlüssel in den Einstellungen." else "Sieh dir die Beispiele an. Für eigene Briefe muss der KI-Dienst eingerichtet werden.", style = Schrift.TextKlein, color = c.tinteLeise, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (s.input.hasContent || s.importing) InputCard(s, model)
        Spacer(Modifier.height(12.dp))
        if (s.loading) {
            ActionButton("Prüfung abbrechen", model::stop, primary = false)
        } else {
            ActionButton(if (s.importing) "Dateien werden vorbereitet …" else "⌑  Brief scannen", scan, enabled = !s.importing)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ActionButton("＋ Datei", file, primary = false, enabled = !s.importing) }
                Box(Modifier.weight(1f)) { ActionButton("≡ Text einfügen", { model.sheet(Sheet.PASTE) }, primary = false, enabled = !s.importing) }
            }
            if (s.input.hasContent) {
                Spacer(Modifier.height(16.dp))
                ActionButton("Ampel prüfen", model::analyze, enabled = !s.importing)
            }
        }
        Spacer(Modifier.height(24.dp))
        Eyebrow("ERST MAL AUSPROBIEREN · BEISPIELE")
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ampel.entries.forEach { a ->
                OutlinedButton(onClick = { model.example(a) }, enabled = !s.loading && !s.importing) {
                    Box(Modifier.size(7.dp).background(Signal.farbe(a), CircleShape))
                    Text("  ${when(a) { Ampel.ROT -> "Mahnung"; Ampel.GELB -> "Rückfrage"; Ampel.GRUEN -> "Termin" }}", style = Schrift.KnopfKlein, color = c.tinte)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Oder ein PDF direkt aus deiner Mail-App mit Briefampel teilen.", style = Schrift.TextKlein, color = c.tinteLeise)
        Text(if (s.personalApi) "${s.remaining} Prüfungen heute · API-Kosten über dein Google-Projekt" else "${s.remaining} Prüfungen heute frei · Original bleibt zunächst zu", style = Schrift.Fuss, color = c.tinteHinweis, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
    }
}

@Composable
private fun InputCard(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Surface(color = c.flaeche, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, c.linie)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("DEIN BRIEF · NOCH NICHT GESENDET")
            if (s.input.text.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Text · ${s.input.text.length} Zeichen", style = Schrift.TextKlein, color = c.tinte, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.setText("") }, enabled = !s.loading && !s.importing) { Text("Entfernen") }
            }
            s.input.attachments.forEach { f -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${f.name}\n${f.pages} Seite(n)", style = Schrift.TextKlein, color = c.tinte, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.remove(f.id) }, enabled = !s.loading && !s.importing) { Text("Entfernen") }
            } }
            if (s.importing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.tinteLeise)
            if (s.input.hasContent && !s.loading && !s.importing) TextButton(onClick = { model.sheet(Sheet.ORIGINAL) }) { Text("Original ansehen", color = c.tinteLeise) }
        }
    }
}

@Composable
private fun ResultScreen(s: UiState, model: BriefampelViewModel, initialScroll: Int = 0) {
    val result = s.result ?: return
    val a = result.analyse
    val c = LocalFarben.current
    val color = c.textFarbe(a.ampel)
    val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(color.copy(alpha = .11f), c.grund), endY = 850f)).verticalScroll(rememberScrollState(initialScroll)).padding(24.dp)) {
        Eyebrow(if (s.example) "BEISPIEL · KEIN EIGENER BRIEF" else "GEPRÜFT ${Fristen.format(today)}")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrafficLight(a.ampel, Modifier.width(95.dp).height(274.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Eyebrow(when(a.ampel) { Ampel.ROT -> "ROT · UNANGENEHM"; Ampel.GELB -> "GELB · ETWAS ZU TUN"; Ampel.GRUEN -> "GRÜN · HARMLOS" }, color)
                Text(when(a.ampel) { Ampel.ROT -> "Lieber\nnicht\nlesen."; Ampel.GELB -> "Kümmern.\nAber ganz\nsachlich."; Ampel.GRUEN -> "Ganz\nentspannt\nlesen." }, style = Schrift.Anzeige, color = color, modifier = Modifier.padding(top = 12.dp))
                Text(when(a.ampel) { Ampel.ROT -> "Ich hab ihn für dich gelesen. Hier steht, was du wissen musst."; Ampel.GELB -> "Du musst etwas tun. Hier ist der Überblick."; Ampel.GRUEN -> "Nichts Unangenehmes drin. Das Wichtigste steht hier." }, style = Schrift.TextKlein, color = c.tinteLeise, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(a.titel, style = Schrift.Titel, color = c.tinte)
        Text(listOfNotNull(a.absender, a.datum?.let(Fristen::format), a.aktenzeichen?.let { "Az. $it" }).joinToString(" · "), style = Schrift.Daten, color = c.tinteLeise, modifier = Modifier.padding(top = 10.dp))
        Text(a.kurz, style = Schrift.Text, color = c.tinte, modifier = Modifier.padding(top = 16.dp))
        a.frist?.let { deadline ->
            Surface(color = c.flaeche, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, c.linie), modifier = Modifier.padding(top = 22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Eyebrow("FRIST · LAUT BRIEF")
                    deadline.datum?.let { date ->
                        val days = Fristen.tageBis(date, today)
                        val urgent = Fristen.istDringend(date, today)
                        Text(Fristen.restText(date, today), style = Schrift.Titel, color = if (urgent) c.rotText else c.tinte, modifier = Modifier.padding(top = 8.dp))
                        Text(Fristen.format(date), style = Schrift.ZahlGross, color = c.tinte, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(14) { i -> Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(2.dp)).background(if (i >= 14 - days.coerceIn(0, 14).toInt()) (if (urgent) Signal.Rot else Signal.Gelb) else c.linie)) }
                        }
                    } ?: Text("Datum unklar – bitte prüfen", style = Schrift.Titel, color = c.gelbText, modifier = Modifier.padding(top = 8.dp))
                    Text(deadline.hinweis, style = Schrift.TextKlein, color = c.tinteLeise)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        a.verlangt?.let { FactRow("WOLLEN", it) }
        FactRow("TON", "${a.ton.replaceFirstChar(Char::uppercase)}. ${a.tonnotiz}")
        FactRow("WENN NICHTS", a.risiko)
        FactRow("MEIN RAT", a.empfehlung)
        if (!a.antwortNoetig) {
            Surface(color = c.flaeche, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(top = 18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Eyebrow("ANTWORT")
                    Text("✓ Nicht nötig", style = Schrift.Titel, color = c.gruenText, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        ActionButton("✉  Brief doch selbst lesen · ${result.seiten} Seite(n)", { model.sheet(Sheet.ORIGINAL) }, primary = false)
        Spacer(Modifier.height(10.dp))
        ActionButton(if (a.antwortNoetig) "✎  Antwort schreiben" else "Trotzdem eine Antwort schreiben", model::openReply, enabled = !s.example)
        if (s.example) Text("Lade zuerst deinen eigenen Brief hoch, um eine Antwort zu schreiben.", style = Schrift.Fuss, color = c.tinteLeise, modifier = Modifier.padding(top = 8.dp))
        TextButton(onClick = if (s.example) model::back else model::newLetter, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("${if (s.example) "Eigenen" else "Neuen"} Brief prüfen", color = c.tinteLeise) }
    }
}

@Composable
private fun ReplyScreen(s: UiState, model: BriefampelViewModel, copy: () -> Unit, share: () -> Unit, initialScroll: Int = 0) {
    val c = LocalFarben.current
    var senderOpen by remember { mutableStateOf(false) }
    val scroll = rememberScrollState(initialScroll)
    val previewScroll = rememberScrollState()
    var followDraft by remember { mutableStateOf(true) }
    val reduced = LocalWenigBewegung.current
    LaunchedEffect(previewScroll) {
        previewScroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) followDraft = false }
    }
    LaunchedEffect(s.writing) { if (s.writing) followDraft = true }
    LaunchedEffect(s.reply) {
        if (s.writing && followDraft) {
            if (reduced) previewScroll.scrollTo(previewScroll.maxValue) else previewScroll.animateScrollTo(previewScroll.maxValue)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("AN ${s.result?.analyse?.absender ?: "[EMPFÄNGER]"}")
        Text("Was soll rein?", style = Schrift.Anzeige, color = c.tinte)
        Text("Stichworte reichen. Den Rest schreibe ich – und erfinde nichts dazu.", style = Schrift.Text, color = c.tinteLeise)
        Eyebrow("VORSCHLÄGE · ANTIPPEN")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.result?.analyse?.stichwortVorschlaege?.forEach { keyword ->
                FilterChip(selected = s.keywords.lines().any { it.trim() == keyword }, onClick = { model.toggleKeyword(keyword) }, enabled = !s.writing,
                    label = { Text(keyword, style = Schrift.KnopfKlein) })
            }
        }
        OutlinedTextField(s.keywords, model::keywords, enabled = !s.writing, label = { Text("Deine Stichworte") }, minLines = 4, modifier = Modifier.fillMaxWidth(), textStyle = Schrift.Text, shape = RoundedCornerShape(18.dp))
        Eyebrow("TON")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tone.entries.forEach { tone -> FilterChip(selected = s.tone == tone, onClick = { model.tone(tone) }, enabled = !s.writing, label = { Text(tone.label, style = Schrift.KnopfKlein) }) }
        }
        OutlinedButton(onClick = { senderOpen = !senderOpen }, modifier = Modifier.fillMaxWidth(), enabled = !s.writing, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Eyebrow("ABSENDER · NUR AUF DIESEM HANDY")
                Text(s.sender.name.ifBlank { "Absender ergänzen (optional)" } + if (senderOpen) "  −" else "  +", style = Schrift.TextKlein, color = c.tinte)
            }
        }
        AnimatedVisibility(senderOpen) { SenderFields(s.sender, model::sender, enabled = !s.writing) }
        if (s.writing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SCHREIBT · ${s.reply.split(Regex("\\s+")).count { it.isNotBlank() }} WÖRTER", style = Schrift.Marke, color = c.tinteLeise, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = model::stop) { Text("■ Stopp") }
            }
        } else ActionButton(if (s.reply.isEmpty()) "Antwort schreiben" else "Neu formulieren", model::write)
        if (s.reply.isNotBlank()) {
            Surface(color = c.papier, shape = RoundedCornerShape(6.dp)) {
                SelectionContainer { Text(s.reply, style = Schrift.Text, color = c.aufPapier, modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(previewScroll).padding(22.dp)) }
            }
            if (s.writing && !followDraft) TextButton(onClick = { followDraft = true }) { Text("Aktuellen Text zeigen ↓", color = c.tinteLeise) }
            Text(if (s.writing) "Der Entwurf wächst. Du kannst jederzeit stoppen." else "Entwurf prüfen und Platzhalter ergänzen, bevor du ihn versendest.", style = Schrift.Fuss, color = c.tinteLeise)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ActionButton("Kopieren", copy, primary = false, enabled = !s.writing) }
                Box(Modifier.weight(1f)) { ActionButton("Teilen", share, primary = false, enabled = !s.writing) }
            }
        }
    }
}

@Composable
internal fun SenderFields(sender: Sender, change: (Sender) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(sender.name, { change(sender.copy(name = it.take(200))) }, label = { Text("Name") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sender.address, { change(sender.copy(address = it.take(500))) }, label = { Text("Anschrift") }, enabled = enabled, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sender.email, { change(sender.copy(email = it.take(200))) }, label = { Text("E-Mail (optional)") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ActionButton(text: String, click: () -> Unit, primary: Boolean = true, enabled: Boolean = true) {
    val c = LocalFarben.current
    val modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
    if (primary) Button(onClick = click, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = c.knopf, contentColor = c.aufKnopf)) { Text(text, style = Schrift.KnopfKlein) }
    else OutlinedButton(onClick = click, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, c.linie), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) { Text(text, style = Schrift.KnopfKlein, color = if (enabled) c.tinte else c.tinteHinweis) }
}

@Composable
internal fun Eyebrow(text: String, color: Color = LocalFarben.current.tinteLeise) { Text(text, style = Schrift.Marke, color = color) }

@Composable
private fun FactRow(label: String, text: String) {
    val c = LocalFarben.current
    HorizontalDivider(color = c.linie)
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Eyebrow(label)
        Text(text, style = Schrift.Text, color = c.tinte, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ErrorCard(text: String, dismiss: () -> Unit) {
    val c = LocalFarben.current
    Surface(color = c.flaeche, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.rotText.copy(alpha = .5f)), modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = Schrift.TextKlein, color = c.tinte, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = dismiss) { Text("OK", color = c.tinte) }
        }
    }
}

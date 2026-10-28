package de.briefampel.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.briefampel.app.domain.*
import de.briefampel.app.ui.figur.AmpelFreundin
import de.briefampel.app.ui.figur.Stimmung
import de.briefampel.app.ui.figur.alsStimmung
import de.briefampel.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Einstieg der App: verbindet Systemdienste (Scanner, Dateien, Zwischenablage, Vibration) mit der Oberfläche. */
@Composable
fun BriefampelApp(model: BriefampelViewModel) {
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
        BackHandler(state.showIntro || state.sheet != null || state.screen != Screen.INPUT || state.loading || state.writing) { model.back() }
        val wenig = LocalWenigBewegung.current
        LaunchedEffect(state.screen, state.result) {
            val ampel = state.result?.analyse?.ampel
            if (state.screen == Screen.RESULT && ampel != null && !wenig) vibriere(context.getSystemService(Vibrator::class.java), ampel)
        }
        val biometrieMoeglich = remember(context) {
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }
        val biometrie: (() -> Unit)? = if (!biometrieMoeglich || context !is FragmentActivity) null else ({
            BiometricPrompt(context, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = model.entsperrt()
            }).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Briefampel entsperren")
                    .setSubtitle("Mit Fingerabdruck oder Gesicht")
                    .setNegativeButtonText("PIN eingeben")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build(),
            )
        })
        BriefampelLayout(
            state, model, snackbar, scan,
            file = { files.launch(arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")) },
            copy = { text ->
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Antwortbrief", text))
                scope.launch { snackbar.showSnackbar("Antwort kopiert") }
            },
            share = { text ->
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Antwort teilen"))
            },
            biometrie = biometrie,
        )
    }
}

/** Grün: ein weicher Tick. Gelb: zwei kurze. Rot: ein festerer Impuls. */
private fun vibriere(vibrator: Vibrator?, ampel: Ampel) {
    vibrator ?: return
    val effekt = when (ampel) {
        Ampel.GRUEN -> VibrationEffect.createOneShot(18, 90)
        Ampel.GELB -> VibrationEffect.createWaveform(longArrayOf(0, 24, 90, 24), intArrayOf(0, 140, 0, 140), -1)
        Ampel.ROT -> VibrationEffect.createOneShot(60, 230)
    }
    vibrator.vibrate(effekt)
}

/** Dasselbe Layout für die laufende App und die gerenderten Screenshots. */
@Composable
internal fun BriefampelLayout(
    state: UiState,
    model: BriefampelViewModel,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    scan: () -> Unit = {},
    file: () -> Unit = {},
    copy: (String) -> Unit = {},
    share: (String) -> Unit = {},
    initialScroll: Int = 0,
    initialSheetScroll: Int = 0,
    biometrie: (() -> Unit)? = null,
) {
    val c = LocalFarben.current
    val lampen = remember { mutableStateMapOf<Ampel, Offset>() }
    val ergebnis = state.result?.analyse?.ampel?.takeIf { state.screen == Screen.RESULT }
    var letzteAmpel by remember { mutableStateOf(Ampel.GELB) }
    if (ergebnis != null) letzteAmpel = ergebnis
    val schein by animateFloatAsState(if (ergebnis != null) 1f else 0f, tween(1400, easing = Bewegung.Ruhig), label = "Schein")

    Box(Modifier.fillMaxSize().background(c.grund)) {
        // Der Farbschein der leuchtenden Lampe färbt den Raum ganz leicht ein.
        if (schein > 0f) Canvas(Modifier.fillMaxSize()) {
            val mitte = lampen[letzteAmpel] ?: return@Canvas
            val farbe = Signal.farbe(letzteAmpel)
            val r = size.width * 1.1f
            drawRect(Brush.radialGradient(0f to farbe.copy(alpha = 0.30f * schein), 0.3f to farbe.copy(alpha = 0.09f * schein), 0.62f to Color.Transparent, center = mitte, radius = r))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Kopfzeile(state, model)
            state.error?.let { FehlerKarte(it, model::clearError) }
            AnimatedContent(
                state.screen,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(180)) },
                label = "Ansicht",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) { screen ->
                Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    when (screen) {
                        Screen.INPUT -> Eingang(state, model, scan, file)
                        Screen.RESULT -> Ergebnis(state, model, initialScroll, copy, share) { a, o -> lampen[a] = o }
                        Screen.REPLY -> Antwort(state, model, { copy(state.reply) }, { share(state.reply) }, initialScroll)
                        Screen.VERLAUF -> VerlaufAnsicht(state, model)
                    }
                }
            }
            Text(
                "Sortiert und entwirft. Ersetzt keine Rechtsberatung – Fristen selbst gegenprüfen.",
                style = Schrift.Fuss, color = c.tinteHinweis,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp))
    }
    AppSheets(state, model, initialSheetScroll)
    when {
        state.showIntro -> Intro(model::finishIntro)
        state.sperre != Sperre.OFFEN -> SperrBildschirm(state, model, biometrie)
    }
}

@Composable
private fun Kopfzeile(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.screen == Screen.INPUT) {
            Column(
                Modifier.background(Color(0xFF0B0C0E), RoundedCornerShape(8.dp)).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) { listOf(Signal.Rot, Signal.Gelb, Signal.Gruen).forEach { Box(Modifier.size(6.dp).background(it, CircleShape)) } }
            Spacer(Modifier.width(10.dp))
            Text("Briefampel", style = Schrift.Wortmarke, color = c.tinte)
        } else {
            RundKnopf(Symbole.Zurueck, "Zurück", model::back, enabled = !s.importing)
        }
        Spacer(Modifier.weight(1f))
        if (s.screen == Screen.INPUT) {
            RundKnopf(Symbole.Verlauf, "Verlauf", model::verlaufOeffnen, enabled = !s.loading && !s.importing)
            Spacer(Modifier.width(10.dp))
        }
        RundKnopf(Symbole.Person, "Einstellungen", { model.sheet(Sheet.SETTINGS) }, enabled = !s.loading && !s.writing && !s.importing)
    }
}

// ---------------------------------------------------------------- Eingang

private val zahlwoerter = listOf("Eine", "Zwei", "Drei", "Vier", "Fünf", "Sechs", "Sieben", "Acht", "Neun", "Zehn", "Elf", "Zwölf")

@Composable
private fun Eingang(s: UiState, model: BriefampelViewModel, scan: () -> Unit, file: () -> Unit) {
    val c = LocalFarben.current
    val seiten = s.input.attachments.sumOf { it.pages }
    val kopf = when {
        s.loading -> "Moment.\nIch lese\nfür dich."
        seiten > 0 -> "${zahlwoerter.getOrElse(seiten - 1) { "$seiten" }}\n${if (seiten == 1) "Seite" else "Seiten"}.\nBereit."
        s.input.text.isNotBlank() -> "Dein\nText.\nBereit."
        else -> "Gib her.\nIch lese\nzuerst."
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                AmpelFreundin(
                    stimmung = if (s.loading) Stimmung.LIEST else Stimmung.WARTET,
                    beschreibung = if (s.loading) "Briefampel liest gerade" else "Briefampel, eine freundliche Ampel mit Gesicht und Hut",
                    hutLuepfen = s.input.attachments.size + if (s.input.text.isNotBlank()) 1 else 0,
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f).padding(top = 30.dp)) {
                    AnimatedContent(kopf, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }, label = "Kopf") {
                        Text(it, style = Schrift.Anzeige, color = c.tinte)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (s.loading) LeseStatus()
                    else Text(
                        if (s.input.hasContent) "Ich schaue rein, du musst es nicht." else "Ich bin da. Du siehst nur die Ampel – und was zu tun ist.",
                        style = Schrift.TextKlein, color = c.tinteLeise,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            if (s.input.hasContent || s.importing) {
                ImUmschlag(s, model)
                Spacer(Modifier.height(20.dp))
            }
            if (!s.configured) {
                Surface(color = c.flaeche, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow("VORSCHAU · KI NOCH NICHT VERBUNDEN")
                        Text(
                            if (s.personalApiSupported) "Sieh dir die Beispiele an oder hinterlege deinen Gemini-Schlüssel in den Einstellungen."
                            else "Sieh dir die Beispiele an. Für eigene Briefe muss der KI-Dienst eingerichtet werden.",
                            style = Schrift.TextKlein, color = c.tinteLeise,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Eyebrow("ODER ERST MAL EIN BEISPIEL")
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ampel.entries.forEach { a ->
                    Surface(
                        onClick = { model.example(a) }, enabled = !s.loading && !s.importing, shape = CircleShape,
                        color = Color.Transparent, contentColor = c.tinte, border = BorderStroke(1.dp, c.linie),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Signal.farbe(a), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(when (a) { Ampel.ROT -> "Mahnung"; Ampel.GELB -> "Rückfrage"; Ampel.GRUEN -> "Termin" }, style = Schrift.KnopfKlein)
                        }
                    }
                }
            }
            Text(
                if (s.personalApi) "${s.remaining} Prüfungen heute · API-Kosten über dein Google-Projekt"
                else "${s.remaining} Prüfungen heute frei · oder ein PDF direkt aus der Mail-App teilen",
                style = Schrift.Fuss, color = c.tinteHinweis, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                s.loading -> Pille("Prüfung abbrechen", model::stop, art = PillenArt.NEBEN)
                s.input.hasContent -> {
                    Pille("Ampel prüfen", model::analyze, enabled = !s.importing)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pille("Seite dazu", file, Modifier.weight(1f), PillenArt.NEBEN, Symbole.Plus, enabled = !s.importing)
                        Pille("Text einfügen", { model.sheet(Sheet.PASTE) }, Modifier.weight(1f), PillenArt.NEBEN, Symbole.Einfuegen, enabled = !s.importing)
                    }
                }
                else -> {
                    Pille(if (s.importing) "Wird vorbereitet …" else "Brief scannen", scan, symbol = Symbole.Scannen, enabled = !s.importing)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pille("Datei", file, Modifier.weight(1f), PillenArt.NEBEN, Symbole.Datei, enabled = !s.importing)
                        Pille("Text einfügen", { model.sheet(Sheet.PASTE) }, Modifier.weight(1f), PillenArt.NEBEN, Symbole.Einfuegen, enabled = !s.importing)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeseStatus() {
    val c = LocalFarben.current
    val wenig = LocalWenigBewegung.current
    val lauf by rememberInfiniteTransition(label = "Status").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "Lauf")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LIEST · PRÜFT DEN TON · SUCHT FRISTEN", style = Schrift.Marke, color = c.tinteLeise)
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(c.linie)) {
            Box(Modifier.fillMaxWidth(if (wenig) 0.4f else lauf).fillMaxHeight().background(c.tinte))
        }
    }
}

/** Die eingelegten Seiten als kleiner Papierstapel, noch nicht gesendet. */
@Composable
private fun ImUmschlag(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Surface(color = c.flaeche, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, c.linie)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Papierstapel(minOf(3, s.input.attachments.sumOf { it.pages } + if (s.input.text.isNotBlank()) 1 else 0).coerceAtLeast(1))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("IM UMSCHLAG · NOCH NICHT GESENDET")
                if (s.input.text.isNotBlank()) Eintrag("Text · ${s.input.text.length} Zeichen", "Text entfernen", !s.loading && !s.importing) { model.setText("") }
                s.input.attachments.forEach { f ->
                    Eintrag("${f.name} · ${f.pages} ${if (f.pages == 1) "Seite" else "Seiten"}", "${f.name} entfernen", !s.loading && !s.importing) { model.remove(f.id) }
                }
                if (s.importing) LeseStatus()
                if (s.input.hasContent && !s.loading && !s.importing) {
                    Text(
                        "Original ansehen", style = Schrift.KnopfKlein, color = c.tinteLeise,
                        modifier = Modifier.clip(CircleShape).selectable(false, role = Role.Button) { model.sheet(Sheet.ORIGINAL) }.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Eintrag(text: String, entfernen: String, enabled: Boolean, weg: () -> Unit) {
    val c = LocalFarben.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Schrift.Daten, color = c.tinte, modifier = Modifier.weight(1f))
        Surface(onClick = weg, enabled = enabled, shape = CircleShape, color = Color.Transparent, contentColor = c.tinteLeise, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Symbole.Schliessen, entfernen, Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun Papierstapel(anzahl: Int) {
    val c = LocalFarben.current
    Box(Modifier.size(width = 84.dp, height = 88.dp)) {
        listOf(-7f to 0.dp, 2f to 14.dp, 8f to 28.dp).take(anzahl).forEach { (winkel, x) ->
            Column(
                Modifier.offset(x = x, y = 6.dp).rotate(winkel).size(48.dp, 66.dp)
                    .shadow(6.dp, RoundedCornerShape(4.dp)).background(c.papier, RoundedCornerShape(4.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(0.6f, 1f, 0.85f, 0.92f).forEach { Box(Modifier.fillMaxWidth(it).height(3.dp).background(Color(0xFFD4D0C7), CircleShape)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Ergebnis

private val wochentag = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)

@Composable
private fun Ergebnis(
    s: UiState, model: BriefampelViewModel, initialScroll: Int,
    copy: (String) -> Unit, share: (String) -> Unit, lampenMitte: (Ampel, Offset) -> Unit,
) {
    val r = s.result ?: return
    val a = r.analyse
    val c = LocalFarben.current
    val farbe = c.textFarbe(a.ampel)
    val heute = LocalDate.now(ZoneId.of("Europe/Berlin"))
    val eintrag = s.verlauf.firstOrNull { it.id == s.eintragId }
    var loeschen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(initialScroll)).padding(horizontal = 20.dp).padding(bottom = 140.dp)) {
            Eyebrow(
                when {
                    s.example -> "BEISPIEL · KEIN EIGENER BRIEF"
                    s.ausVerlauf && eintrag != null -> "AUS DEINEM VERLAUF · GEPRÜFT ${Fristen.format(tagDes(eintrag.geprueftAm))}"
                    else -> "GEPRÜFT ${heute.format(DateTimeFormatter.ofPattern("dd.MM."))}"
                },
                if (s.example) c.gelbText else c.tinteHinweis,
                Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.Top) {
                AmpelFreundin(
                    a.ampel.alsStimmung(),
                    when (a.ampel) { Ampel.ROT -> "Ampel: Rot – lieber nicht lesen"; Ampel.GELB -> "Ampel: Gelb – kümmern, aber sachlich"; Ampel.GRUEN -> "Ampel: Grün – harmlos" },
                    lampe = 52.dp,
                    lampenMitte = lampenMitte,
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f).padding(top = 24.dp)) {
                    Eyebrow(when (a.ampel) { Ampel.ROT -> "ROT · ICH KÜMMERE MICH"; Ampel.GELB -> "GELB · HANDELN, KEIN ÄRGER"; Ampel.GRUEN -> "GRÜN · HARMLOS" }, farbe)
                    Text(
                        when (a.ampel) { Ampel.ROT -> "Lieber\nnicht\nlesen."; Ampel.GELB -> "Kümmern.\nAber ganz\nsachlich."; Ampel.GRUEN -> "Ganz\nentspannt\nlesen." },
                        style = Schrift.Anzeige.copy(fontSize = 42.sp, lineHeight = 41.sp), color = farbe, modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        when (a.ampel) {
                            Ampel.ROT -> "Ich hab ihn für dich gelesen. Keine Panik – hier steht alles, was du wissen musst."
                            Ampel.GELB -> "Du musst etwas tun – Ärger gibt es keinen."
                            Ampel.GRUEN -> "Nichts Unangenehmes drin. Versprochen."
                        },
                        style = Schrift.TextKlein, color = c.tinteLeise, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Text(a.titel, style = Schrift.Titel.copy(fontSize = 27.sp, lineHeight = 30.sp), color = c.tinte, modifier = Modifier.padding(top = 30.dp))
            Text(
                listOfNotNull(a.absender, a.datum?.let(Fristen::format), a.aktenzeichen?.let { "Az. $it" }).joinToString(" · "),
                style = Schrift.Daten, color = c.tinteHinweis, modifier = Modifier.padding(top = 10.dp),
            )
            Text(a.kurz, style = Schrift.Text.copy(fontSize = 18.sp, lineHeight = 26.sp), color = c.tinte, modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(24.dp))
            FristKarte(a, heute)
            Spacer(Modifier.height(20.dp))
            a.verlangt?.let { FaktZeile("WOLLEN", it) }
            FaktZeile("TON", "${a.ton.replaceFirstChar(Char::uppercase)}. ${a.tonnotiz}")
            FaktZeile("WENN NICHTS", a.risiko)
            FaktZeile("MEIN RAT", a.empfehlung, betont = true, letzte = true)
            Spacer(Modifier.height(20.dp))
            if (s.ausVerlauf) Text(
                "Das Original bewahre ich nicht auf – im Verlauf liegen nur Ergebnis und Antworten.",
                style = Schrift.TextKlein, color = c.tinteHinweis,
            ) else VersiegelterBrief(r.seiten) { model.sheet(Sheet.ORIGINAL) }
            eintrag?.antworten?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(24.dp))
                GespeicherteAntworten(it, copy, share)
            }
            if (s.ausVerlauf && eintrag != null) Text(
                "Aus dem Verlauf löschen", style = Schrift.KnopfKlein, color = c.rotText, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(CircleShape)
                    .selectable(false, role = Role.Button) { loeschen = true }.padding(vertical = 14.dp),
            )
            Text(
                "${if (s.example) "Eigenen" else "Neuen"} Brief prüfen", style = Schrift.KnopfKlein, color = c.tinteLeise, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(CircleShape)
                    .selectable(false, role = Role.Button) { if (s.example) model.back() else model.newLetter() }.padding(vertical = 14.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to c.grund))
                .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (a.antwortNoetig) {
                Pille("Antwort schreiben", model::openReply, symbol = Symbole.Stift, enabled = !s.example)
            } else {
                Pille("Brief jetzt lesen", { model.sheet(Sheet.ORIGINAL) }, symbol = Symbole.Umschlag)
                Text(
                    "Antwort nicht nötig – trotzdem eine?", style = Schrift.KnopfKlein, color = if (s.example) c.tinteHinweis else c.tinteLeise,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).selectable(false, enabled = !s.example, role = Role.Button, onClick = model::openReply).padding(vertical = 12.dp),
                )
            }
            if (s.example) Text("Lade zuerst deinen eigenen Brief hoch, um eine Antwort zu schreiben.", style = Schrift.Fuss, color = c.tinteLeise, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
    if (loeschen && eintrag != null) AlertDialog(
        onDismissRequest = { loeschen = false },
        title = { Text("Diesen Brief löschen?") },
        text = { Text("„${a.titel}“ und ${if (eintrag.antworten.isEmpty()) "das Ergebnis" else "deine Antworten dazu"} verschwinden von diesem Handy.") },
        confirmButton = { TextButton(onClick = { loeschen = false; model.eintragLoeschen(eintrag.id) }) { Text("Löschen", color = c.rotText) } },
        dismissButton = { TextButton(onClick = { loeschen = false }) { Text("Behalten", color = c.tinte) } },
        containerColor = c.flaeche, titleContentColor = c.tinte, textContentColor = c.tinteLeise,
    )
}

@Composable
private fun FristKarte(a: LetterAnalysis, heute: LocalDate) {
    val c = LocalFarben.current
    val frist = a.frist
    val karte = Modifier.fillMaxWidth()
    when {
        frist?.datum != null -> Surface(color = c.flaeche, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, c.linie), modifier = karte) {
            val datum = frist.datum
            val tage = Fristen.tageBis(datum, heute)
            val dringend = Fristen.istDringend(datum, heute)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (tage > 0) Row(verticalAlignment = Alignment.Bottom) {
                        Text("$tage", style = Schrift.ZahlGross.copy(fontSize = 76.sp, lineHeight = 70.sp, letterSpacing = (-2).sp), color = if (dringend) c.rotText else c.tinte)
                        Text(if (tage == 1L) "Tag" else "Tage", style = Schrift.Titel.copy(fontSize = 22.sp), color = c.tinte, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                    } else Text(Fristen.restText(datum, heute), style = Schrift.Titel, color = c.rotText)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Eyebrow("FRIST · LAUT BRIEF")
                        Text("${datum.format(wochentag).trimEnd('.')} ${Fristen.format(datum)}", style = Schrift.ZahlGross.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = c.tinte, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                val rest = tage.coerceIn(0, 13).toInt()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(14) { i ->
                        val zelle = when {
                            i > 13 - rest -> if (dringend) Signal.Rot else Signal.Gelb
                            i == 13 - rest -> c.tinte
                            else -> c.linie
                        }
                        Box(Modifier.weight(1f).height(18.dp).background(zelle, RoundedCornerShape(3.dp)))
                    }
                }
                Text(frist.hinweis, style = Schrift.TextKlein, color = c.tinteLeise)
            }
        }
        frist != null -> Surface(color = c.flaeche, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, c.linie), modifier = karte) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("FRIST")
                Text("Datum unklar – bitte prüfen", style = Schrift.Titel, color = c.gelbText)
                Text(frist.hinweis, style = Schrift.TextKlein, color = c.tinteLeise)
            }
        }
        !a.antwortNoetig -> Surface(color = c.flaeche, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, c.linie), modifier = karte) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbole.Haken, null, tint = c.gruenText, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Eyebrow("ANTWORT")
                    Text("Nicht nötig", style = Schrift.Titel.copy(fontSize = 22.sp), color = c.tinte)
                }
            }
        }
    }
}

/** Der Originalbrief bleibt versiegelt, bis man ihn bewusst öffnet. */
@Composable
private fun VersiegelterBrief(seiten: Int, oeffnen: () -> Unit) {
    val c = LocalFarben.current
    Surface(
        onClick = oeffnen, color = Color.Transparent, contentColor = c.tinte, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(c.linie, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))), cornerRadius = CornerRadius(20.dp.toPx()))
        },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            de.briefampel.app.ui.figur.Briefumschlag(Modifier.size(64.dp, 46.dp), siegel = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Brief doch selbst lesen", style = Schrift.KnopfKlein.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Bold))
                Text("$seiten ${if (seiten == 1) "Seite" else "Seiten"} · bleibt zu, bis du willst", style = Schrift.TextKlein, color = c.tinteHinweis)
            }
            Icon(Symbole.Weiter, null, tint = c.tinteHinweis, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------- Antwort

@Composable
private fun Antwort(s: UiState, model: BriefampelViewModel, copy: () -> Unit, share: () -> Unit, initialScroll: Int) {
    val c = LocalFarben.current
    var absenderOffen by remember { mutableStateOf(false) }
    val scroll = rememberScrollState(initialScroll)
    val vorschau = rememberScrollState()
    var folgen by remember { mutableStateOf(true) }
    val wenig = LocalWenigBewegung.current
    LaunchedEffect(vorschau) { vorschau.interactionSource.interactions.collect { if (it is DragInteraction.Start) folgen = false } }
    LaunchedEffect(s.writing) { if (s.writing) folgen = true }
    LaunchedEffect(s.reply) {
        if (s.writing && folgen) { if (wenig) vorschau.scrollTo(vorschau.maxValue) else vorschau.animateScrollTo(vorschau.maxValue) }
    }
    val ampel = s.result?.analyse?.ampel
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ampel?.let { Box(Modifier.size(10.dp).background(Signal.farbe(it), CircleShape)); Spacer(Modifier.width(8.dp)) }
            Eyebrow("AN ${(s.result?.analyse?.absender ?: "[EMPFÄNGER]").uppercase(Locale.GERMAN)}")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Was soll rein?", style = Schrift.Anzeige.copy(fontSize = 36.sp, lineHeight = 36.sp), color = c.tinte)
            Text("Stichworte reichen. Den Rest schreibe ich – und erfinde nichts dazu.", style = Schrift.Text, color = c.tinteLeise)
        }
        s.result?.analyse?.stichwortVorschlaege?.takeIf { it.isNotEmpty() }?.let { vorschlaege ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow("VORSCHLÄGE · ANTIPPEN")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vorschlaege.forEach { v ->
                        val an = s.keywords.lines().any { it.trim() == v }
                        Surface(
                            onClick = { model.toggleKeyword(v) }, enabled = !s.writing, shape = CircleShape,
                            color = if (an) c.knopf else Color.Transparent, contentColor = if (an) c.aufKnopf else c.tinte,
                            border = if (an) null else BorderStroke(1.dp, c.linie), modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (an) { Icon(Symbole.Haken, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                                Text(v, style = Schrift.KnopfKlein)
                            }
                        }
                    }
                }
            }
        }
        Feld(s.keywords, model::keywords, "Deine Stichworte", enabled = !s.writing, minLines = 4)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("TON")
            Row(Modifier.fillMaxWidth().background(c.flaeche, RoundedCornerShape(26.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Tone.entries.forEach { t ->
                    val an = s.tone == t
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(CircleShape).background(if (an) c.knopf else Color.Transparent)
                            .selectable(an, enabled = !s.writing, role = Role.RadioButton) { model.tone(t) },
                        contentAlignment = Alignment.Center,
                    ) { Text(t.label, style = Schrift.KnopfKlein.copy(fontSize = 14.5.sp, fontWeight = if (an) FontWeight.Bold else FontWeight.SemiBold), color = if (an) c.aufKnopf else c.tinteLeise) }
                }
            }
        }
        Surface(
            onClick = { absenderOffen = !absenderOffen }, enabled = !s.writing, color = Color.Transparent, contentColor = c.tinte,
            shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, c.linie),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Eyebrow("ABSENDER · NUR AUF DIESEM HANDY")
                    Text(listOf(s.sender.name, s.sender.address.lines().joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Absender ergänzen (optional)" }, style = Schrift.TextKlein)
                }
                Icon(if (absenderOffen) Symbole.Schliessen else Symbole.Plus, null, tint = c.tinteHinweis, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(absenderOffen) { SenderFields(s.sender, model::sender, enabled = !s.writing) }

        if (s.writing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Puls()
                Spacer(Modifier.width(10.dp))
                Text("SCHREIBT · ${s.reply.split(Regex("\\s+")).count { it.isNotBlank() }} WÖRTER", style = Schrift.Marke, color = c.tinte, modifier = Modifier.weight(1f))
                Pille("Stopp", model::stop, Modifier, PillenArt.NEBEN, hoehe = 44.dp)
            }
        } else {
            Pille(if (s.reply.isEmpty()) "Antwort schreiben" else "Neu formulieren", model::write, symbol = Symbole.Stift)
        }
        if (s.reply.isNotBlank()) {
            Briefpapier(s.reply, s.writing, vorschau)
            if (s.writing && !folgen) Text("Aktuellen Text zeigen", style = Schrift.KnopfKlein, color = c.tinteLeise, modifier = Modifier.clip(CircleShape).selectable(false, role = Role.Button) { folgen = true }.padding(12.dp))
            Text(if (s.writing) "Der Entwurf wächst. Du kannst jederzeit stoppen." else "Entwurf prüfen und Platzhalter in [ ] ergänzen, bevor du ihn versendest.", style = Schrift.Fuss, color = c.tinteLeise)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pille("Kopieren", copy, Modifier.weight(1f), PillenArt.NEBEN, Symbole.Kopieren, enabled = !s.writing)
                Pille("Teilen", share, Modifier.weight(1f), PillenArt.NEBEN, Symbole.Teilen, enabled = !s.writing)
            }
        }
    }
}

@Composable
private fun Puls() {
    val c = LocalFarben.current
    val wenig = LocalWenigBewegung.current
    val a by rememberInfiniteTransition(label = "Puls").animateFloat(0.25f, 1f, infiniteRepeatable(tween(500), androidx.compose.animation.core.RepeatMode.Reverse), label = "Alpha")
    Box(Modifier.size(8.dp).background(c.tinte.copy(alpha = if (wenig) 1f else a), CircleShape))
}

/** Der eigene Antwortbrief entsteht auf echtem Papier – im Gegensatz zum versiegelten Eingangsbrief. */
@Composable
private fun Briefpapier(text: String, schreibt: Boolean, scroll: androidx.compose.foundation.ScrollState) {
    val c = LocalFarben.current
    val wenig = LocalWenigBewegung.current
    val blinken by rememberInfiniteTransition(label = "Cursor").animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "Blinken")
    val inhalt = buildAnnotatedString {
        append(text)
        if (schreibt) withStyle(SpanStyle(color = c.aufPapier.copy(alpha = if (wenig || blinken < 0.5f) 1f else 0f))) { append(" ▍") }
    }
    Surface(color = c.papier, shape = RoundedCornerShape(6.dp), shadowElevation = 16.dp) {
        SelectionContainer {
            Text(
                inhalt, style = Schrift.Text.copy(fontSize = 15.sp, lineHeight = 23.sp), color = c.aufPapier,
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(scroll).padding(horizontal = 22.dp, vertical = 24.dp),
            )
        }
    }
}

package de.briefampel.app.ui

import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import de.briefampel.app.BuildConfig
import de.briefampel.app.ui.theme.*
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheets(s: UiState, model: BriefampelViewModel, initialScroll: Int = 0) {
    val sheet = s.sheet ?: return
    val c = LocalFarben.current
    // Layoutlib doesn't capture the separate Android Dialog window. Render the very same
    // content and Material container in the preview's own window; the device uses the modal.
    if (LocalInspectionMode.current) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f)), contentAlignment = Alignment.BottomCenter) {
            Surface(color = c.flaeche, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BottomSheetDefaults.DragHandle()
                    SheetContent(s, model, sheet, initialScroll)
                }
            }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = { model.sheet(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.flaeche,
    ) {
        SheetContent(s, model, sheet, initialScroll)
    }
}

@Composable
private fun SheetContent(s: UiState, model: BriefampelViewModel, sheet: Sheet, initialScroll: Int) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).imePadding().verticalScroll(rememberScrollState(initialScroll)).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (sheet) {
                Sheet.CONSENT -> Consent(s, model)
                Sheet.PRIVACY -> Privacy(s, model)
                Sheet.SETTINGS -> Settings(s, model)
                Sheet.PASTE -> Paste(s, model)
                Sheet.ORIGINAL -> Original(s, model)
            }
        }
}

@Composable
private fun Consent(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Eyebrow("EINMAL, BEVOR ICH LESE")
    Text("Dein Brief bleibt\ndein Brief.", style = Schrift.Anzeige, color = c.tinte)
    Text(if (s.personalApi) "Damit ich ihn zusammenfassen kann, wird dein Brief direkt an die Google Gemini Developer API gesendet. Das gilt auch für Bilder, PDFs und enthaltene persönliche Daten." else "Damit ich ihn zusammenfassen kann, wird dein Brief direkt an die Google Gemini Developer API gesendet. Das gilt auch für Bilder, PDFs und enthaltene persönliche Daten.", style = Schrift.Text, color = c.tinte)
    Info("GEHT AN", if (s.personalApi) "Google Gemini Developer API · keine festgelegte EU-Region" else "Google Gemini Developer API · keine festgelegte EU-Region")
    Info("AUF DEINEM HANDY", "Absenderdaten und Einwilligung werden lokal gespeichert. Original und Entwurf bleiben nur für die aktuelle Sitzung im Arbeitsspeicher.")
    Info("BEIM ANBIETER", "Für die Verarbeitung und Aufbewahrung gelten die Bedingungen des eingerichteten Google-Dienstes. Es gibt keine Zusage, dass keinerlei technische Protokolle entstehen.")
    Info("KOSTET", if (s.personalApi) "Die API-Nutzung läuft über dein Google-Projekt und kann Kosten verursachen. Das App-Limit erlaubt drei erfolgreiche Prüfungen täglich; Antwortentwürfe zählen zusätzlich bei Google." else "Drei Prüfungen pro Tag sind in dieser Version frei.")
    if (s.personalApi) Info("DEIN API-PROJEKT", "Welche Datenbedingungen gelten, hängt von deinem Google-Projekt und der Region ab. Prüfe diese vor dem Senden persönlicher Briefe; teste zunächst mit geschwärzten oder erfundenen Daten.")
    Info("IST NICHT", "Rechtsberatung. Die KI kann sich irren. Fristen und Entwürfe prüfst du selbst.")
    ActionButton("Verstanden, ${if (s.input.hasContent) "Brief prüfen" else "zustimmen"}", model::consent)
    TextButton(onClick = { model.sheet(Sheet.PRIVACY) }) { Text("Datenschutz in Ruhe lesen", color = c.tinteLeise) }
    TextButton(onClick = { model.sheet(null) }) { Text("Noch nicht", color = c.tinteLeise) }
}

@Composable
private fun Privacy(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val uri = LocalUriHandler.current
    Eyebrow("DATEN & KONTROLLE")
    Text("Was mit deinem\nBrief passiert.", style = Schrift.Titel, color = c.tinte)
    Text("Vor der Zustimmung wird kein Brief an die KI gesendet. Nach deiner Zustimmung werden nur die zur Prüfung ausgewählten Inhalte übertragen; beim Antworten zusätzlich deine Stichworte, der gewählte Ton und die eingetragenen Absenderdaten.", style = Schrift.Text, color = c.tinte)
    Info("VERARBEITUNG", if (s.personalApi) "Direkt über die Google Gemini Developer API mit deinem eigenen API-Projekt. Die App legt dabei keine EU-Verarbeitungsregion fest. Es gelten die Daten- und Abrechnungsbedingungen deines API-Projekts." else "Google Gemini über Firebase AI Logic, Region ${BuildConfig.AI_REGION}. Die Region und das Modell müssen vom Betreiber passend eingerichtet sein.")
    Info("LOKAL", "Die App speichert keinen Briefverlauf. Importierte Briefe und Entwürfe bleiben im Arbeitsspeicher. PDFs werden zur Seitenprüfung kurz im privaten Cache geöffnet und danach gelöscht. Android, der Dateianbieter und der Dokument-Scanner können eigene temporäre Kopien verwalten.")
    Info("EINSTELLUNGEN", "Absender, Darstellungsmodus, Tageszähler und Zustimmung bleiben lokal auf diesem Gerät. App-Backups sind deaktiviert. Du kannst Absenderdaten löschen und die Zustimmung für künftige Anfragen widerrufen.")
    if (s.personalApiSupported) Info("PERSÖNLICHER API-SCHLÜSSEL", "Nur die Testversion unterstützt einen eigenen Schlüssel. Er wird mit einem Schlüssel aus Android Keystore verschlüsselt auf diesem Gerät gespeichert und nur zur Anmeldung bei der Gemini API übertragen. Du kannst ihn in den Einstellungen löschen.")
    Info("KI & GRENZEN", "KI-Antworten können Fehler enthalten. Fristen im Original und fertige Entwürfe immer prüfen. Das lokale Tageslimit ist kein serverseitiger Kostenschutz.")
    TextButton(onClick = { uri.openUri("https://firebase.google.com/support/privacy") }) { Text("Google: Datenschutz und Verarbeitung") }
    if (s.personalApiSupported) TextButton(onClick = { uri.openUri("https://ai.google.dev/gemini-api/terms") }) { Text("Gemini API: Daten- und Nutzungsbedingungen") }
    Text("Diese technische Übersicht ersetzt keine veröffentlichte Datenschutzerklärung des Betreibers. Diese Vorschau ist noch nicht für den Play Store freigegeben.", style = Schrift.TextKlein, color = c.tinteLeise)
    ActionButton("Zurück", { model.sheet(Sheet.SETTINGS) }, primary = false)
}

@Composable
private fun Settings(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Eyebrow("DEINE BRIEFAMPEL")
    Text("Alles in Ruhe.", style = Schrift.Anzeige, color = c.tinte)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Dunkle Darstellung", style = Schrift.Text, color = c.tinte, modifier = Modifier.weight(1f))
        Switch(checked = s.dark, onCheckedChange = { model.dark() })
    }
    HorizontalDivider(color = c.linie)
    Eyebrow("KI-VERBINDUNG")
    Text(when { s.personalApi -> "Dein Gemini-Schlüssel ist hinterlegt. Die Verbindung wird bei deiner nächsten Prüfung getestet."; s.configured -> "Firebase ist eingerichtet. Die Verbindung wird bei deiner nächsten Prüfung verwendet."; s.personalApiSupported -> "Hinterlege deinen eigenen Gemini-API-Schlüssel, um auf diesem Handy ohne Firebase zu testen."; else -> "Die Vorschau funktioniert. Eigene Briefe können erst nach Einrichtung von Firebase analysiert werden." }, style = Schrift.Text, color = c.tinte)
    if (s.personalApiSupported) PersonalApiSettings(s, model)
    else if (!s.configured) Text("Einrichtung für den Entwickler: README.md im Projekt.", style = Schrift.TextKlein, color = c.tinteLeise)
    Info("VERFÜGBAR", "${s.remaining} von 3 Prüfungen heute · Tageswechsel nach Europe/Berlin")
    Info("ZUSTIMMUNG", if (s.consent) "Erteilt. Gilt für Analysen und Antwortentwürfe." else "Noch nicht erteilt. Vor einer KI-Anfrage fragen wir nach.")
    if (s.consent) ActionButton("Zustimmung widerrufen", model::revokeConsent, primary = false)
    else if (s.configured) ActionButton("Verarbeitung ansehen", { model.sheet(Sheet.CONSENT) }, primary = false)
    ActionButton("Datenschutz lesen", { model.sheet(Sheet.PRIVACY) }, primary = false)
    HorizontalDivider(color = c.linie)
    Eyebrow("ABSENDER · NUR AUF DIESEM HANDY")
    SenderFields(s.sender, model::sender)
    TextButton(onClick = model::forgetSender) { Text("Absenderdaten löschen", color = c.tinteLeise) }
    Text("Briefampel ${BuildConfig.VERSION_NAME} · Vorschau", style = Schrift.Fuss, color = c.tinteLeise)
    Eyebrow("BEISPIELE")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        de.briefampel.app.domain.Ampel.entries.forEach { signal ->
            OutlinedButton(onClick = { model.example(signal) }) { Text(when (signal) { de.briefampel.app.domain.Ampel.ROT -> "Mahnung"; de.briefampel.app.domain.Ampel.GELB -> "Rückfrage"; else -> "Termin" }) }
        }
    }
    ActionButton("Intro erneut ansehen", model::replayIntro, primary = false)
    ActionButton("Fertig", { model.sheet(null) })
}

@Composable
private fun PersonalApiSettings(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf("") }
    LaunchedEffect(s.apiMessage) { if (s.apiMessage?.startsWith("Schlüssel gespeichert") == true) key = "" }
    Eyebrow("GEMINI API · PERSÖNLICHER PIXEL-TEST")
    OutlinedTextField(key, { key = it.take(512) }, label = { Text("Gemini-API-Schlüssel") }, singleLine = true,
        enabled = !s.configuringApi, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    ActionButton(if (s.configuringApi) "Wird gespeichert …" else "Schlüssel speichern", { model.savePersonalKey(key) }, enabled = key.trim().length >= 20 && !s.configuringApi)
    if (s.personalApi) ActionButton("API-Schlüssel löschen", model::clearPersonalKey, primary = false, enabled = !s.configuringApi)
    s.apiMessage?.let { Text(it, style = Schrift.TextKlein, color = c.tinteLeise) }
    TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text("Schlüssel in Google AI Studio erstellen") }
    Text("Der Schlüssel bleibt auf diesem Gerät verschlüsselt gespeichert. Die API wird über dein Google-Projekt abgerechnet. Keine EU-Region festgelegt. Für erste Tests bitte Beispieldaten verwenden.", style = Schrift.TextKlein, color = c.tinteLeise)
}

@Composable
private fun Paste(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val context = LocalContext.current
    var text by remember { mutableStateOf(s.input.text) }
    var notice by remember { mutableStateOf<String?>(null) }
    Eyebrow("E-MAIL, BRIEF ODER NACHRICHT")
    Text("Gib mir den Text.", style = Schrift.Titel, color = c.tinte)
    Text("Er bleibt hier, bis du „Ampel prüfen“ auswählst.", style = Schrift.TextKlein, color = c.tinteLeise)
    OutlinedTextField(text, { if (it.length <= 60_000) { text = it; notice = null } else notice = "Maximal 60.000 Zeichen. Bitte kürzen." }, label = { Text("Brieftext") }, minLines = 6, maxLines = 12, textStyle = Schrift.Text, modifier = Modifier.fillMaxWidth())
    Text("${text.length} / 60.000 Zeichen", style = Schrift.Daten, color = c.tinteLeise)
    notice?.let { Text(it, style = Schrift.TextKlein, color = c.rotText) }
    ActionButton("Aus Zwischenablage einfügen", {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
        when {
            value.isNullOrBlank() -> notice = "Kein Text in der Zwischenablage. Bilder bitte über Datei auswählen."
            value.length > 60_000 -> notice = "Der Text ist zu lang. Bitte vor dem Einfügen auf 60.000 Zeichen kürzen."
            else -> { text = value; notice = null }
        }
    }, primary = false)
    ActionButton("Text übernehmen", { model.setText(text); model.sheet(null) }, enabled = text.isNotBlank())
}

@Composable
private fun Original(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Eyebrow(if (s.example) "BEISPIEL · ORIGINALTEXT" else "DEIN ORIGINAL · BEWUSST GEÖFFNET")
    Text("Hier ist dein Brief.", style = Schrift.Titel, color = c.tinte)
    val text = if (s.example) s.result?.volltext.orEmpty() else s.input.text
    if (text.isNotBlank()) Surface(color = c.papier) {
        SelectionContainer { Text(text, style = Schrift.Text, color = c.aufPapier, modifier = Modifier.fillMaxWidth().padding(18.dp)) }
    }
    if (!s.example) {
        val pages = remember(s.input.attachments) { s.input.attachments.flatMap { f -> (0 until f.pages).map { f to it } } }
        var page by remember { mutableIntStateOf(0) }
        val selected = pages.getOrNull(page)
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var failure by remember { mutableStateOf(false) }
        LaunchedEffect(selected) {
            bitmap = null; failure = false
            if (selected != null) {
                try { bitmap = model.inputReader.preview(selected.first, selected.second) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failure = true }
            }
        }
        if (pages.isNotEmpty()) {
            Text("Seite ${page + 1} / ${pages.size} · ${selected?.first?.name.orEmpty()}", style = Schrift.Daten, color = c.tinteLeise)
            bitmap?.let { Image(it.asImageBitmap(), "Original Seite ${page + 1}", Modifier.fillMaxWidth().background(c.papier), contentScale = ContentScale.FillWidth) }
                ?: Text(if (failure) "Vorschau konnte nicht geladen werden. Bitte die Originaldatei im Dateianbieter öffnen." else "Seite wird geladen …", style = Schrift.Text, color = c.tinte)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text("‹ Vorherige") }
                OutlinedButton(onClick = { page++ }, enabled = page < pages.lastIndex) { Text("Nächste ›") }
            }
        }
    }
    ActionButton("Brief wieder schließen", { model.sheet(null) })
}

@Composable
private fun Info(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Eyebrow(label)
        Text(text, style = Schrift.TextKlein, color = LocalFarben.current.tinte)
    }
}

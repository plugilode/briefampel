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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
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
                Sheet.PAKETE -> Pakete(s, model)
                Sheet.ANMELDEN -> Anmelden(s, model)
            }
        }
}

@Composable
private fun Consent(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Eyebrow(sag("EINMAL, BEVOR ICH LESE"))
    Text(sag("Dein Brief bleibt\ndein Brief."), style = Schrift.Anzeige, color = c.tinte)
    Text(if (s.serverModus) sag("Damit ich ihn zusammenfassen kann, wird dein Brief über den Briefampel-Server in Frankfurt an Google Gemini gesendet. Das gilt auch für Bilder, PDFs und enthaltene persönliche Daten. Der Server speichert nichts davon.") else sag("Damit ich ihn zusammenfassen kann, wird dein Brief direkt an die Google Gemini Developer API gesendet. Das gilt auch für Bilder, PDFs und enthaltene persönliche Daten."), style = Schrift.Text, color = c.tinte)
    Info(sag("GEHT AN"), if (s.serverModus) sag("Briefampel-Server (Frankfurt) · danach Google Gemini") else sag("Google Gemini Developer API · keine festgelegte EU-Region"))
    Info(sag("AUF DEINEM HANDY"), sag("Absenderdaten, Einwilligung und „Meine Briefe“ (verschlüsselt) werden lokal gespeichert. Originale bleiben nur für die aktuelle Sitzung im Arbeitsspeicher."))
    Info(sag("BEIM ANBIETER"), sag("Für die Verarbeitung und Aufbewahrung gelten die Bedingungen des eingerichteten Google-Dienstes. Es gibt keine Zusage, dass keinerlei technische Protokolle entstehen."))
    Info(sag("KOSTET"), sag("1 Analyse aus deinem Guthaben (noch %d). Darin sind bis zu 3 Antwortentwürfe zu diesem Brief enthalten. Schlägt die Prüfung fehl, wird nichts abgezogen.", s.guthaben) +
        if (s.personalApi) " " + sag("In dieser Testversion läuft die Abrechnung bei Google über dein eigenes API-Projekt.") else "")
    if (s.personalApi) Info(sag("DEIN API-PROJEKT"), sag("Welche Datenbedingungen gelten, hängt von deinem Google-Projekt und der Region ab. Prüfe diese vor dem Senden persönlicher Briefe; teste zunächst mit geschwärzten oder erfundenen Daten."))
    Info(sag("IST NICHT"), sag("Rechtsberatung. Die KI kann sich irren. Fristen und Entwürfe prüfst du selbst."))
    ActionButton(if (s.input.hasContent) sag("Verstanden, Brief prüfen") else sag("Verstanden, zustimmen"), model::consent)
    TextButton(onClick = { model.sheet(Sheet.PRIVACY) }) { Text(sag("Datenschutz in Ruhe lesen"), color = c.tinteLeise) }
    TextButton(onClick = { model.sheet(null) }) { Text(sag("Noch nicht"), color = c.tinteLeise) }
}

@Composable
private fun Privacy(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val uri = LocalUriHandler.current
    Eyebrow(sag("DATEN & KONTROLLE"))
    Text(sag("Was mit deinem\nBrief passiert."), style = Schrift.Titel, color = c.tinte)
    Text(sag("Vor der Zustimmung wird kein Brief an die KI gesendet. Nach deiner Zustimmung werden nur die zur Prüfung ausgewählten Inhalte übertragen; beim Antworten zusätzlich deine Stichworte, der gewählte Ton und die eingetragenen Absenderdaten."), style = Schrift.Text, color = c.tinte)
    Info(sag("VERARBEITUNG"), if (s.personalApi) sag("Direkt über die Google Gemini Developer API mit deinem eigenen API-Projekt. Die App legt dabei keine EU-Verarbeitungsregion fest. Es gelten die Daten- und Abrechnungsbedingungen deines API-Projekts.") else sag("Über den Briefampel-Server (Firebase, Frankfurt) an Google Gemini. Der Server speichert keine Briefinhalte, nur dein Guthaben und Zähler."))
    Info(sag("VERLAUF"), sag("Ergebnisse geprüfter Briefe und deine Antworten werden im Verlauf gespeichert: nur auf diesem Handy, AES-256-verschlüsselt mit einem Schlüssel aus dem Android Keystore, geschützt durch deine PIN. Originalseiten werden nicht gespeichert. Einträge bleiben, bis du sie löschst. „PIN vergessen“ löscht den ganzen Verlauf."))
    Info(sag("LOKAL"), sag("Importierte Originale bleiben nur im Arbeitsspeicher. PDFs werden zur Seitenprüfung kurz im privaten Cache geöffnet und danach gelöscht. Android, der Dateianbieter und der Dokument-Scanner können eigene temporäre Kopien verwalten."))
    Info(sag("EINSTELLUNGEN"), sag("Absender, Darstellung, Sprache und Zustimmung bleiben lokal auf diesem Gerät. App-Backups sind deaktiviert. Du kannst Absenderdaten löschen und die Zustimmung für künftige Anfragen widerrufen."))
    if (s.personalApiSupported) Info(sag("PERSÖNLICHER API-SCHLÜSSEL"), sag("Nur die Testversion unterstützt einen eigenen Schlüssel. Er wird mit einem Schlüssel aus Android Keystore verschlüsselt auf diesem Gerät gespeichert und nur zur Anmeldung bei der Gemini API übertragen. Du kannst ihn in den Einstellungen löschen."))
    Info(sag("KI & GRENZEN"), sag("KI-Antworten können Fehler enthalten. Fristen im Original und fertige Entwürfe immer prüfen."))
    TextButton(onClick = { uri.openUri("https://firebase.google.com/support/privacy") }) { Text(sag("Google: Datenschutz und Verarbeitung")) }
    if (s.personalApiSupported) TextButton(onClick = { uri.openUri("https://ai.google.dev/gemini-api/terms") }) { Text(sag("Gemini API: Daten- und Nutzungsbedingungen")) }
    Text(sag("Diese technische Übersicht ersetzt keine veröffentlichte Datenschutzerklärung des Betreibers. Diese Vorschau ist noch nicht für den Play Store freigegeben."), style = Schrift.TextKlein, color = c.tinteLeise)
    ActionButton(sag("Zurück"), { model.sheet(Sheet.SETTINGS) }, primary = false)
}

@Composable
private fun Settings(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Eyebrow(sag("DEINE BRIEFAMPEL"))
    Text(sag("Alles in Ruhe."), style = Schrift.Anzeige, color = c.tinte)
    Eyebrow(sag("SPRACHE · LANGUAGE · IDIOMA"))
    SprachSchalter(s.sprache, model::sprache)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(sag("Dunkle Darstellung"), style = Schrift.Text, color = c.tinte, modifier = Modifier.weight(1f))
        Switch(checked = s.dark, onCheckedChange = { model.dark() })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(sag("Intro-Ton beim Start"), style = Schrift.Text, color = c.tinte, modifier = Modifier.weight(1f))
        Switch(checked = s.introTon, onCheckedChange = model::introTon)
    }
    Text(sag("Spielt nicht, wenn das Handy stumm ist oder gerade Musik läuft."), style = Schrift.TextKlein, color = c.tinteLeise)
    HorizontalDivider(color = c.linie)
    Eyebrow(sag("SPERRE & MEINE BRIEFE"))
    val context = androidx.compose.ui.platform.LocalContext.current
    val vorschau = LocalInspectionMode.current
    val biometrieMoeglich = remember(context) {
        !vorschau && androidx.biometric.BiometricManager.from(context).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
    if (biometrieMoeglich) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(sag("Mit Fingerabdruck oder Gesicht entsperren"), style = Schrift.Text, color = c.tinte, modifier = Modifier.weight(1f))
        Switch(checked = s.biometrie, onCheckedChange = model::biometrie)
    }
    Text(if (s.verlauf.size == 1) sag("1 Brief im Verlauf · verschlüsselt auf diesem Handy · bleibt, bis du ihn löschst.") else sag("%d Briefe im Verlauf · verschlüsselt auf diesem Handy · bleiben, bis du sie löschst.", s.verlauf.size), style = Schrift.TextKlein, color = c.tinteLeise)
    ActionButton(sag("Meine Briefe ansehen"), model::verlaufOeffnen, primary = false)
    ActionButton(sag("Jetzt sperren"), model::sperren, primary = false)
    HorizontalDivider(color = c.linie)
    if (s.serverModus) Konto(s, model)
    else Eyebrow(sag("KI-VERBINDUNG"))
    if (!s.serverModus) Text(when { s.personalApi -> sag("Dein Gemini-Schlüssel ist hinterlegt. Die Verbindung wird bei deiner nächsten Prüfung getestet."); s.configured -> sag("Firebase ist eingerichtet. Die Verbindung wird bei deiner nächsten Prüfung verwendet."); s.personalApiSupported -> sag("Hinterlege deinen eigenen Gemini-API-Schlüssel, um auf diesem Handy ohne Firebase zu testen."); else -> sag("Die Vorschau funktioniert. Eigene Briefe können erst nach Einrichtung von Firebase analysiert werden.") }, style = Schrift.Text, color = c.tinte)
    if (s.serverModus) Unit
    else if (s.personalApiSupported) PersonalApiSettings(s, model)
    else if (!s.configured) Text(sag("Einrichtung für den Entwickler: README.md im Projekt."), style = Schrift.TextKlein, color = c.tinteLeise)
    Info(sag("GUTHABEN"), sag("%s · 1 Analyse = ein Brief geprüft + bis zu 3 Antworten · verfällt nicht", analysenText(s.guthaben)))
    ActionButton(sag("Pakete und Preise"), { model.sheet(Sheet.PAKETE) }, primary = false)
    if (BuildConfig.DEBUG && !s.serverModus) TextButton(onClick = model::testGuthaben) { Text(sag("Testversion: +10 Analysen"), color = c.tinteLeise) }
    Info(sag("ZUSTIMMUNG"), if (s.consent) sag("Erteilt. Gilt für Analysen und Antwortentwürfe.") else sag("Noch nicht erteilt. Vor einer KI-Anfrage fragen wir nach."))
    if (s.consent) ActionButton(sag("Zustimmung widerrufen"), model::revokeConsent, primary = false)
    else if (s.configured) ActionButton(sag("Verarbeitung ansehen"), { model.sheet(Sheet.CONSENT) }, primary = false)
    ActionButton(sag("Datenschutz lesen"), { model.sheet(Sheet.PRIVACY) }, primary = false)
    HorizontalDivider(color = c.linie)
    Eyebrow(sag("ABSENDER · NUR AUF DIESEM HANDY"))
    SenderFields(s.sender, model::sender)
    TextButton(onClick = model::forgetSender) { Text(sag("Absenderdaten löschen"), color = c.tinteLeise) }
    Text("Briefampel ${BuildConfig.VERSION_NAME}${if (s.serverModus) "" else " · " + sag("Vorschau")}", style = Schrift.Fuss, color = c.tinteLeise)
    Eyebrow(sag("BEISPIELE"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        de.briefampel.app.domain.Ampel.entries.forEach { signal ->
            OutlinedButton(onClick = { model.example(signal) }) { Text(when (signal) { de.briefampel.app.domain.Ampel.ROT -> sag("Mahnung"); de.briefampel.app.domain.Ampel.GELB -> sag("Rückfrage"); else -> sag("Termin") }) }
        }
    }
    ActionButton(sag("Intro erneut ansehen"), model::replayIntro, primary = false)
    ActionButton(sag("Fertig"), { model.sheet(null) })
}

@Composable
private fun PersonalApiSettings(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf("") }
    LaunchedEffect(s.apiMessage) { if (s.apiMessage == s.sprache.text("Schlüssel gespeichert. Die Verbindung wird bei der nächsten Prüfung getestet.")) key = "" }
    Eyebrow(sag("GEMINI API · PERSÖNLICHER PIXEL-TEST"))
    OutlinedTextField(key, { key = it.take(512) }, label = { Text(sag("Gemini-API-Schlüssel")) }, singleLine = true,
        enabled = !s.configuringApi, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    ActionButton(if (s.configuringApi) sag("Wird gespeichert …") else sag("Schlüssel speichern"), { model.savePersonalKey(key) }, enabled = key.trim().length >= 20 && !s.configuringApi)
    if (s.personalApi) ActionButton(sag("API-Schlüssel löschen"), model::clearPersonalKey, primary = false, enabled = !s.configuringApi)
    s.apiMessage?.let { Text(it, style = Schrift.TextKlein, color = c.tinteLeise) }
    TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text(sag("Schlüssel in Google AI Studio erstellen")) }
    Text(sag("Der Schlüssel bleibt auf diesem Gerät verschlüsselt gespeichert. Die API wird über dein Google-Projekt abgerechnet. Keine EU-Region festgelegt. Für erste Tests bitte Beispieldaten verwenden."), style = Schrift.TextKlein, color = c.tinteLeise)
}

@Composable
private fun Paste(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val context = LocalContext.current
    var text by remember { mutableStateOf(s.input.text) }
    var notice by remember { mutableStateOf<String?>(null) }
    val sprache = LocalSprache.current
    Eyebrow(sag("E-MAIL, BRIEF ODER NACHRICHT"))
    Text(sag("Gib mir den Text."), style = Schrift.Titel, color = c.tinte)
    Text(sag("Er bleibt hier, bis du „Ampel prüfen“ auswählst."), style = Schrift.TextKlein, color = c.tinteLeise)
    OutlinedTextField(text, { if (it.length <= 60_000) { text = it; notice = null } else notice = sprache.text("Maximal 60.000 Zeichen. Bitte kürzen.") }, label = { Text("Brieftext") }, minLines = 6, maxLines = 12, textStyle = Schrift.Text, modifier = Modifier.fillMaxWidth())
    Text(sag("%d / 60.000 Zeichen", text.length), style = Schrift.Daten, color = c.tinteLeise)
    notice?.let { Text(it, style = Schrift.TextKlein, color = c.rotText) }
    ActionButton(sag("Aus Zwischenablage einfügen"), {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
        when {
            value.isNullOrBlank() -> notice = sprache.text("Kein Text in der Zwischenablage. Bilder bitte über Datei auswählen.")
            value.length > 60_000 -> notice = sprache.text("Der Text ist zu lang. Bitte vor dem Einfügen auf 60.000 Zeichen kürzen.")
            else -> { text = value; notice = null }
        }
    }, primary = false)
    ActionButton(sag("Text übernehmen"), { model.setText(text); model.sheet(null) }, enabled = text.isNotBlank())
}

@Composable
private fun Original(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    Eyebrow(if (s.example) sag("BEISPIEL · ORIGINALTEXT") else sag("DEIN ORIGINAL · BEWUSST GEÖFFNET"))
    Text(sag("Hier ist dein Brief."), style = Schrift.Titel, color = c.tinte)
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
            Text(sag("Seite %d / %d · %s", page + 1, pages.size, selected?.first?.name.orEmpty()), style = Schrift.Daten, color = c.tinteLeise)
            bitmap?.let { Image(it.asImageBitmap(), sag("Original Seite %d", page + 1), Modifier.fillMaxWidth().background(c.papier), contentScale = ContentScale.FillWidth) }
                ?: Text(if (failure) sag("Vorschau konnte nicht geladen werden. Bitte die Originaldatei im Dateianbieter öffnen.") else sag("Seite wird geladen …"), style = Schrift.Text, color = c.tinte)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text(sag("‹ Vorherige")) }
                OutlinedButton(onClick = { page++ }, enabled = page < pages.lastIndex) { Text(sag("Nächste ›")) }
            }
        }
    }
    ActionButton(sag("Brief wieder schließen"), { model.sheet(null) })
}

@Composable
private fun Info(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Eyebrow(label)
        Text(text, style = Schrift.TextKlein, color = LocalFarben.current.tinte)
    }
}

/** Pakete statt Abo: Man zahlt nur, wenn Post kommt. Kaufen wird mit dem Store-Start freigeschaltet. */
@Composable
private fun Pakete(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    var gewaehlt by remember { mutableStateOf("analysen_30") }
    Eyebrow(sag("DEIN GUTHABEN · %s", analysenText(s.guthaben).uppercase(s.sprache.locale)))
    Text(if (s.guthaben == 0) sag("Mehr Briefe,\nweniger Sorgen.") else sag("Mehr Briefe\nprüfen."), style = Schrift.Anzeige, color = c.tinte)
    Text(sag("Kein Abo. Du zahlst nur, wenn Post kommt – und das Guthaben verfällt nicht."), style = Schrift.Text, color = c.tinteLeise)
    de.briefampel.app.domain.Guthaben.pakete.forEach { p ->
        val an = p.id == gewaehlt
        Surface(
            onClick = { gewaehlt = p.id }, shape = RoundedCornerShape(22.dp),
            color = if (an) c.knopf else c.flaeche, contentColor = if (an) c.aufKnopf else c.tinte,
            border = if (an) null else BorderStroke(1.dp, c.linie), modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    p.marke?.let { Text(sag(it), style = Schrift.Marke, color = if (an) c.aufKnopf.copy(alpha = 0.7f) else c.gelbText) }
                    Text(sag("%d Analysen", p.analysen), style = Schrift.Titel.copy(fontSize = 22.sp))
                    Text(sag(p.jeAnalyse), style = Schrift.TextKlein, color = if (an) c.aufKnopf.copy(alpha = 0.75f) else c.tinteLeise)
                }
                Text(s.preise[p.id] ?: sag(p.preis), style = Schrift.ZahlGross.copy(fontSize = 20.sp))
            }
        }
    }
    Info(sag("EINE ANALYSE"), sag("Ein Brief geprüft – Ampel, Zusammenfassung, Frist – plus bis zu 3 Antwortentwürfe dazu."))
    Info(sag("GRATIS"), if (s.serverModus) sag("Jedes neue Google-Konto startet mit 3 Analysen zum Ausprobieren.") else sag("Jede neue Installation startet mit 3 Analysen zum Ausprobieren."))
    val activity = LocalContext.current.findActivity()
    if (s.serverModus) {
        val paket = de.briefampel.app.domain.Guthaben.pakete.first { it.id == gewaehlt }
        s.kaufMeldung?.let { Text(it, style = Schrift.Text, color = c.tinte) }
        if (!s.angemeldet) ActionButton(if (s.anmeldenLaeuft) sag("Anmelden …") else sag("Mit Google anmelden"), { activity?.let(model::anmelden) }, enabled = !s.anmeldenLaeuft)
        else ActionButton(if (s.kaufLaeuft) sag("Google Play öffnet …") else sag("%d Analysen für %s kaufen", paket.analysen, s.preise[paket.id] ?: sag(paket.preis)),
            { activity?.let { model.kaufen(it, paket.id) } }, enabled = !s.kaufLaeuft)
        Text(sag("Bezahlt wird sicher über Google Play. Das Guthaben gehört zu deinem Google-Konto und geht beim Handywechsel nicht verloren."), style = Schrift.TextKlein, color = c.tinteLeise)
    } else {
        ActionButton(sag("Kaufen kommt mit dem Store-Start"), {}, enabled = false)
        Text(sag("In dieser Testversion ist noch kein Kauf möglich. Bezahlt wird später sicher über Google Play."), style = Schrift.TextKlein, color = c.tinteLeise)
        if (BuildConfig.DEBUG) ActionButton(sag("Testversion: +10 Analysen"), model::testGuthaben, primary = false)
    }
    ActionButton(sag("Schließen"), { model.sheet(null) }, primary = false)
}

/** Konto in den Einstellungen: wer angemeldet ist, und abmelden. */
@Composable
private fun Konto(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val activity = LocalContext.current.findActivity()
    Eyebrow(sag("DEIN KONTO"))
    if (s.angemeldet) {
        Text(sag("Angemeldet als %s. Dein Guthaben ist dort gesichert, deine Briefe bleiben nur auf diesem Handy.", s.email ?: sag("Google-Konto")), style = Schrift.Text, color = c.tinte)
        ActionButton(sag("Abmelden"), model::abmelden, primary = false)
    } else {
        Text(sag("Noch nicht angemeldet. Vor deiner ersten Prüfung meldest du dich einmal mit Google an."), style = Schrift.Text, color = c.tinte)
        ActionButton(if (s.anmeldenLaeuft) sag("Anmelden …") else sag("Mit Google anmelden"), { activity?.let(model::anmelden) }, primary = false, enabled = !s.anmeldenLaeuft)
    }
}

/** Einmal anmelden, bevor der erste Brief zum Server geht. Kurz, ehrlich, ohne Druck. */
@Composable
private fun Anmelden(s: UiState, model: BriefampelViewModel) {
    val c = LocalFarben.current
    val activity = LocalContext.current.findActivity()
    Eyebrow(sag("EINMAL ANMELDEN · DANN GEHT'S LOS"))
    Text(sag("Dein Guthaben\nbraucht ein Zuhause."), style = Schrift.Anzeige, color = c.tinte)
    Text(sag("Mit deinem Google-Konto bekommst du %s geschenkt, und gekaufte Pakete gehen beim Handywechsel nicht verloren.", analysenText(de.briefampel.app.domain.Guthaben.START)), style = Schrift.Text, color = c.tinte)
    Info(sag("DEINE BRIEFE"), sag("Bleiben nur auf diesem Handy. Das Konto kennt nur dein Guthaben, nicht deine Post."))
    ActionButton(if (s.anmeldenLaeuft) sag("Anmelden …") else sag("Mit Google anmelden"), { activity?.let(model::anmelden) }, enabled = !s.anmeldenLaeuft)
    ActionButton(sag("Später"), { model.sheet(null) }, primary = false)
}

internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

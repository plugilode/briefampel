package de.briefampel.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import de.briefampel.app.data.LetterInput
import de.briefampel.app.data.LlmProvider
import de.briefampel.app.data.PersonalApi
import de.briefampel.app.domain.*
import de.briefampel.app.ui.theme.BriefampelTheme
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.time.ZoneId

/** Captures use the production Compose layout, example data, and read-only local dependencies. */
private fun today() = LocalDate.now(ZoneId.of("Europe/Berlin"))
private fun base() = UiState(personalApiSupported = true)
private fun result(signal: Ampel, dark: Boolean = true) = base().copy(
    screen = Screen.RESULT, result = Beispiele.fuer(signal, today()), example = true, dark = dark,
)
private val sender = Sender("Erika Muster", "Musterstraße 12\n50667 Köln", "erika@example.org")
private fun reply(written: Boolean = false, writing: Boolean = false) = base().copy(
    screen = Screen.REPLY, result = Beispiele.fuer(Ampel.ROT, today()), input = LetterInput("Beispielbrief"),
    configured = true, personalApi = true, consent = true, sender = sender,
    keywords = "Forderung bestreiten\nVertrag und Rechnung anfordern\nIch kenne den genannten Vertrag nicht.",
    writing = writing,
    reply = if (written || writing) """
        Erika Muster
        Musterstraße 12
        50667 Köln

        Inkasso Nordwest GmbH
        Hafenstraße 3
        28217 Bremen

        Köln, ${Fristen.format(today())}

        Ihr Zeichen INK-55821/26 – Ihre Zahlungsaufforderung

        Sehr geehrte Damen und Herren,

        die von Ihnen geltend gemachte Forderung bestreite ich. Der von Ihnen genannte Vertrag ist mir nicht bekannt.

        Bitte übersenden Sie mir eine Kopie des zugrunde liegenden Vertrags sowie der zugehörigen Rechnung, damit ich Ihre Forderung prüfen kann.

        Mit freundlichen Grüßen

        Erika Muster
    """.trimIndent() else "",
)

@Composable
private fun Capture(state: UiState, bottom: Boolean = false, sheetBottom: Boolean = false) {
    val context = LocalContext.current
    val model = remember(context) {
        BriefampelViewModel(PreviewApplication(context), object : LlmProvider {
            override suspend fun analyze(input: LetterInput, today: LocalDate): LetterAnalysis = error("No network in screenshots")
            override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate) = emptyFlow<String>()
        }, false, object : PersonalApi {
            override val supported = true
            override val configured = false
            override fun save(key: String) = error("Read-only screenshot")
            override fun clear() = Unit
        })
    }
    BriefampelTheme(dunkel = state.dark) { BriefampelLayout(state, model, initialScroll = if (bottom) Int.MAX_VALUE else 0, initialSheetScroll = if (sheetBottom) Int.MAX_VALUE else 0) }
}

@PreviewTest @Preview(name = "01_Start", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun StartScreenshot() = Capture(base())

@PreviewTest @Preview(name = "02_Brief_bereit", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun ImportScreenshot() = Capture(base().copy(configured = true, personalApi = true, input = LetterInput("Bitte reichen Sie den angeforderten Beleg bis zum 12.11.2026 nach.")))

@PreviewTest @Preview(name = "03_Pruefung", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun LoadingScreenshot() = Capture(base().copy(configured = true, personalApi = true, loading = true, input = LetterInput("Beispielbrief")))

@PreviewTest @Preview(name = "04_Ergebnis_Rot", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun RedScreenshot() = Capture(result(Ampel.ROT))

@PreviewTest @Preview(name = "05_Ergebnis_Gelb", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun YellowScreenshot() = Capture(result(Ampel.GELB))

@PreviewTest @Preview(name = "06_Ergebnis_Gruen", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun GreenScreenshot() = Capture(result(Ampel.GRUEN))

@PreviewTest @Preview(name = "07_Antwort_Stichworte", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun ReplyScreenshot() = Capture(reply())

@PreviewTest @Preview(name = "08_Antwort_schreibt", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun WritingScreenshot() = Capture(reply(writing = true), bottom = true)

@PreviewTest @Preview(name = "09_Antwort_fertig", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun DraftScreenshot() = Capture(reply(written = true), bottom = true)

@PreviewTest @Preview(name = "10_Text_einfuegen", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun PasteScreenshot() = Capture(base().copy(sheet = Sheet.PASTE))

@PreviewTest @Preview(name = "11_Einwilligung", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun ConsentScreenshot() = Capture(base().copy(sheet = Sheet.CONSENT, personalApi = true, configured = true, input = LetterInput("Beispielbrief")))

@PreviewTest @Preview(name = "12_Einstellungen_Gemini", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun SettingsScreenshot() = Capture(base().copy(sheet = Sheet.SETTINGS))

@PreviewTest @Preview(name = "13_Datenschutz", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun PrivacyScreenshot() = Capture(base().copy(sheet = Sheet.PRIVACY, personalApi = true, configured = true))

@PreviewTest @Preview(name = "14_Originalbrief", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun OriginalScreenshot() = Capture(result(Ampel.ROT).copy(sheet = Sheet.ORIGINAL))

@PreviewTest @Preview(name = "15_Fehler", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun ErrorScreenshot() = Capture(base().copy(configured = true, personalApi = true, error = "Keine Verbindung. Bitte Internet prüfen und erneut versuchen."))

@PreviewTest @Preview(name = "16_Heller_Modus", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun LightScreenshot() = Capture(result(Ampel.GELB, dark = false))

@PreviewTest @Preview(name = "17_Ergebnis_Rot_Details", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun RedDetailsScreenshot() = Capture(result(Ampel.ROT), bottom = true)

@PreviewTest @Preview(name = "18_Ergebnis_Gelb_Details", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun YellowDetailsScreenshot() = Capture(result(Ampel.GELB), bottom = true)

@PreviewTest @Preview(name = "19_Ergebnis_Gruen_Details", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun GreenDetailsScreenshot() = Capture(result(Ampel.GRUEN), bottom = true)

@PreviewTest @Preview(name = "20_Einstellungen_Absender", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun SenderSettingsScreenshot() = Capture(base().copy(sheet = Sheet.SETTINGS, sender = sender), sheetBottom = true)

@PreviewTest @Preview(name = "21_Datenschutz_unten", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun PrivacyBottomScreenshot() = Capture(base().copy(sheet = Sheet.PRIVACY, personalApi = true, configured = true), sheetBottom = true)

@PreviewTest @Preview(name = "22_Einwilligung_Bestaetigung", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun ConsentBottomScreenshot() = Capture(base().copy(sheet = Sheet.CONSENT, personalApi = true, configured = true, input = LetterInput("Beispielbrief")), sheetBottom = true)

@PreviewTest @Preview(name = "23_Originalbrief_unten", widthDp = 412, heightDp = 915, locale = "de", showSystemUi = true)
@Composable fun OriginalBottomScreenshot() = Capture(result(Ampel.ROT).copy(sheet = Sheet.ORIGINAL), sheetBottom = true)

/** No preferences, keys, files, or network from the actual user are read while rendering. */
private class PreviewApplication(context: Context) : Application() {
    init { attachBaseContext(context) }
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = PreviewPreferences
}

private object PreviewPreferences : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getString(key: String?, defValue: String?): String? = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int) = defValue
    override fun getLong(key: String?, defValue: Long) = defValue
    override fun getFloat(key: String?, defValue: Float) = defValue
    override fun getBoolean(key: String?, defValue: Boolean) = defValue
    override fun contains(key: String?) = false
    override fun edit(): SharedPreferences.Editor = error("Read-only screenshot")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

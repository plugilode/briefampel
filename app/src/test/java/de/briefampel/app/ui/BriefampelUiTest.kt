package de.briefampel.app.ui

import de.briefampel.app.data.OffenerZugang
import de.briefampel.app.data.FluechtigerVerlauf
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class BriefampelUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().putString("sprache", "de").commit()
    }

    @Test fun `erster Start zeigt erst die Sprachwahl und danach das lange Intro`() {
        RuntimeEnvironment.getApplication().getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().commit()
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = null)
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Deutsch").performClick()
        compose.waitForIdle()
        assertTrue(model.state.value.showIntro)
        compose.onNodeWithText("Überspringen").assertExists()
    }

    @Test fun `nach dem Update kommt nach der Sprachwahl das kurze Intro`() {
        RuntimeEnvironment.getApplication().getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().putBoolean("intro_seen", true).commit()
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = null)
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Liest zuerst. Für dich.").assertDoesNotExist()
        // Uhr anhalten, sonst spult der Test die 2-Sekunden-Animation sofort durch.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Deutsch").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertTrue(model.state.value.kurzIntro)
        compose.onNodeWithText("Liest zuerst. Für dich.").assertExists()
    }

    @Test fun `own text import stays local and configuration is clearly disclosed`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.finishIntro()
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Text einfügen").performClick()
        compose.onNodeWithText("Brieftext").performTextInput("Dies ist mein eigener Brief.")
        compose.onNodeWithText("Text übernehmen").performScrollTo().performClick()
        compose.onNodeWithText("Text · 28 Zeichen").performScrollTo().assertExists()
        compose.onNodeWithText("Ampel prüfen").performClick()
        compose.onNodeWithText("Hinterlege deinen eigenen Gemini-API-Schlüssel, um auf diesem Handy ohne Firebase zu testen.").assertExists()
    }

    @Test fun `example keeps original hidden until user opens it and disables replies`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.finishIntro()
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Mahnung").performScrollTo().performClick()
        compose.onNodeWithText("BEISPIEL · KEIN EIGENER BRIEF").assertExists()
        compose.onNodeWithText("LETZTE AUFFORDERUNG VOR GERICHTLICHEM MAHNVERFAHREN", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Antwort schreiben").assertIsNotEnabled()
        compose.onNodeWithText("Brief doch selbst lesen").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("LETZTE AUFFORDERUNG VOR GERICHTLICHEM MAHNVERFAHREN", substring = true).assertExists()
    }

    @Test fun `light theme and sender settings are accessible`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.finishIntro()
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Testname")
        compose.onNodeWithText("Absenderdaten löschen").performScrollTo().performClick()
        compose.onNodeWithText("Fertig").performScrollTo().performClick()
        compose.onNodeWithText("Briefampel").assertExists()
    }

    @Test fun `large font input remains scrollable at 360dp`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.finishIntro()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { BriefampelApp(model) }
        }
        compose.onNodeWithText("Text einfügen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Einstellungen").assertIsDisplayed()
    }

    @Test fun `consent result and reply stop flow is connected with a test provider`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(streamDelay = 60_000), true, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.finishIntro()
        model.setText("Dies ist mein eigener Brief.")
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Ampel prüfen").performClick()
        compose.onNodeWithText("Verstanden, Brief prüfen").performScrollTo().performClick()
        compose.onNodeWithText("Antwort schreiben").performClick()
        compose.onNodeWithText("Deine Stichworte").performScrollTo().performTextInput("Vertrag anfordern")
        compose.onNodeWithText("Antwort schreiben").performScrollTo().performClick()
        compose.onNodeWithText("Stopp").performScrollTo().performClick()
        compose.onNodeWithText("Sehr geehrte Damen und Herren,").assertExists()
        compose.onNodeWithText("Kopieren").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Neu formulieren").performScrollTo().assertIsEnabled()
    }

    @Test fun `personal Gemini setup is visible and deleting a key resets consent`() {
        val keys = FakePersonalApi()
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), null, keys, Dispatchers.Main.immediate, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.finishIntro()
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Gemini-API-Schlüssel").performTextInput("fake-key-local-ui-tests-only")
        compose.onNodeWithText("Schlüssel speichern").performScrollTo().performClick()
        compose.onNodeWithText("Schlüssel gespeichert. Die Verbindung wird bei der nächsten Prüfung getestet.").assertExists()
        compose.onNodeWithText("Verarbeitung ansehen").performScrollTo().performClick()
        compose.onNodeWithText("Verstanden, zustimmen").performScrollTo().performClick()
        assertTrue(model.state.value.consent)
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("API-Schlüssel löschen").performScrollTo().performClick()
        compose.onNodeWithText("Persönlicher Schlüssel gelöscht.").assertExists()
        compose.onNodeWithText("Schlüssel speichern").assertIsNotEnabled()
        assertFalse(model.state.value.consent)
    }
}

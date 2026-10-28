package de.briefampel.app.ui

import android.content.Context
import androidx.compose.ui.test.*
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
        RuntimeEnvironment.getApplication().getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `own text import stays local and configuration is clearly disclosed`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false)
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("≡ Text einfügen").performScrollTo().performClick()
        compose.onNodeWithText("Brieftext").performTextInput("Dies ist mein eigener Brief.")
        compose.onNodeWithText("Text übernehmen").performScrollTo().performClick()
        compose.onNodeWithText("Text · 28 Zeichen").performScrollTo().assertExists()
        compose.onNodeWithText("Ampel prüfen").performScrollTo().performClick()
        compose.onNodeWithText("Hinterlege deinen eigenen Gemini-API-Schlüssel, um auf diesem Handy ohne Firebase zu testen.").assertExists()
    }

    @Test fun `example keeps original hidden until user opens it and disables replies`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false)
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("  Mahnung").performScrollTo().performClick()
        compose.onNodeWithText("BEISPIEL · KEIN EIGENER BRIEF").assertExists()
        compose.onNodeWithText("LETZTE AUFFORDERUNG VOR GERICHTLICHEM MAHNVERFAHREN", substring = true).assertDoesNotExist()
        compose.onNodeWithText("✎  Antwort schreiben").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("✉  Brief doch selbst lesen · 3 Seite(n)").performScrollTo().performClick()
        compose.onNodeWithText("LETZTE AUFFORDERUNG VOR GERICHTLICHEM MAHNVERFAHREN", substring = true).assertExists()
    }

    @Test fun `light theme and sender settings are accessible`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false)
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Testname")
        compose.onNodeWithText("Absenderdaten löschen").performScrollTo().performClick()
        compose.onNodeWithText("Fertig").performScrollTo().performClick()
        compose.onNodeWithText("Briefampel").assertExists()
    }

    @Test fun `large font input remains scrollable at 360dp`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { BriefampelApp(model) }
        }
        compose.onNodeWithText("≡ Text einfügen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
    }

    @Test fun `consent result and reply stop flow is connected with a test provider`() {
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(streamDelay = 60_000), true)
        model.setText("Dies ist mein eigener Brief.")
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Ampel prüfen").performScrollTo().performClick()
        compose.onNodeWithText("Verstanden, Brief prüfen").performScrollTo().performClick()
        compose.onNodeWithText("✎  Antwort schreiben").performScrollTo().performClick()
        compose.onNodeWithText("Deine Stichworte").performScrollTo().performTextInput("Vertrag anfordern")
        compose.onNodeWithText("Antwort schreiben").performScrollTo().performClick()
        compose.onNodeWithText("■ Stopp").performScrollTo().performClick()
        compose.onNodeWithText("Sehr geehrte Damen und Herren,").assertExists()
        compose.onNodeWithText("Kopieren").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Neu formulieren").performScrollTo().assertIsEnabled()
    }

    @Test fun `personal Gemini setup is visible and deleting a key resets consent`() {
        val keys = FakePersonalApi()
        val model = BriefampelViewModel(RuntimeEnvironment.getApplication(), FakeProvider(), null, keys, Dispatchers.Main.immediate)
        compose.setContent { BriefampelApp(model) }
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Gemini-API-Schlüssel").performTextInput("fake-key-local-ui-tests-only")
        compose.onNodeWithText("Schlüssel speichern").performScrollTo().performClick()
        compose.onNodeWithText("Schlüssel gespeichert. Die Verbindung wird bei der nächsten Prüfung getestet.").assertExists()
        compose.onNodeWithText("Verarbeitung ansehen").performScrollTo().performClick()
        compose.onNodeWithText("Verstanden, zustimmen").performScrollTo().performClick()
        assertTrue(model.state.value.consent)
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("API-Schlüssel löschen").performScrollTo().performClick()
        compose.onNodeWithText("Persönlicher Schlüssel gelöscht.").assertExists()
        compose.onNodeWithText("Schlüssel speichern").assertIsNotEnabled()
        assertFalse(model.state.value.consent)
    }
}

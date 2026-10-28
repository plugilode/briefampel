package de.briefampel.app.ui

import android.app.Application
import android.content.Context
import de.briefampel.app.data.*
import de.briefampel.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class BriefampelViewModelTest {
    private lateinit var app: Application
    private lateinit var fake: FakeProvider
    private lateinit var model: BriefampelViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().commit()
        fake = FakeProvider()
        model = BriefampelViewModel(app, fake, configured = true, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
    }
    @After fun cleanup() { model.stop(); Dispatchers.resetMain() }

    @Test fun `analysis cannot upload before consent and cancel does not upload`() = runTest(dispatcher) {
        model.setText("Mein Testbrief")
        model.analyze()
        assertEquals(Sheet.CONSENT, model.state.value.sheet)
        runCurrent()
        assertEquals(0, fake.analyses)
        model.sheet(null)
        runCurrent()
        assertEquals(0, fake.analyses)
        assertEquals(3, model.state.value.guthaben)
    }

    @Test fun `successful analysis consumes one use and cache consumes none`() = runTest(dispatcher) {
        model.setText("Mein Testbrief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(1, fake.analyses)
        assertEquals(Screen.RESULT, model.state.value.screen)
        assertEquals(2, model.state.value.guthaben)
        assertFalse(model.state.value.example)
        assertNull(model.state.value.sheet)
        model.back(); model.analyze(); runCurrent()
        assertEquals(1, fake.analyses)
        assertEquals(2, model.state.value.guthaben)
    }

    @Test fun `failed analysis preserves input and quota for explicit retry`() = runTest(dispatcher) {
        fake.fail = true
        model.setText("Mein Testbrief")
        model.analyze(); model.consent(); runCurrent()
        assertNotNull(model.state.value.error)
        assertEquals("Mein Testbrief", model.state.value.input.text)
        assertEquals(3, model.state.value.guthaben)
        assertFalse(model.state.value.loading)
        fake.fail = false
        model.analyze(); runCurrent()
        assertEquals(Screen.RESULT, model.state.value.screen)
        assertEquals(2, model.state.value.guthaben)
    }

    @Test fun `stopping reply retains partial text and regeneration is a fresh request`() = runTest(dispatcher) {
        analyze()
        model.openReply(); model.keywords("Vertrag anfordern"); model.write(); runCurrent()
        assertTrue(model.state.value.writing)
        assertEquals("Sehr geehrte Damen und Herren,", model.state.value.reply)
        model.stop(); runCurrent()
        assertFalse(model.state.value.writing)
        assertEquals("Sehr geehrte Damen und Herren,", model.state.value.reply)
        model.write(); runCurrent()
        assertEquals(2, fake.drafts)
        advanceTimeBy(1001); runCurrent()
        assertEquals("Sehr geehrte Damen und Herren,\nbitte senden Sie den Vertrag.", model.state.value.reply)
        assertFalse(model.state.value.writing)
    }

    @Test fun `immediate restart cannot be reset by the cancelled stream`() = runTest(dispatcher) {
        analyze(); model.openReply(); model.keywords("Vertrag anfordern")
        model.write(); runCurrent()
        model.stop(); model.write(); runCurrent()
        assertTrue(model.state.value.writing)
        assertEquals(2, fake.drafts)
        advanceTimeBy(1001); runCurrent()
        assertFalse(model.state.value.writing)
    }

    @Test fun `missing keywords and example replies never call AI`() = runTest(dispatcher) {
        model.example(Ampel.ROT)
        model.openReply(); model.write(); runCurrent()
        assertEquals(Screen.RESULT, model.state.value.screen)
        assertEquals(0, fake.drafts)
        analyze(); model.openReply(); model.write(); runCurrent()
        assertEquals("Ein paar Stichworte brauche ich.", model.state.value.error)
        assertEquals(0, fake.drafts)
    }

    @Test fun `revocation blocks next upload and discards cached analysis`() = runTest(dispatcher) {
        analyze(); model.back(); model.revokeConsent(); model.analyze(); runCurrent()
        assertEquals(Sheet.CONSENT, model.state.value.sheet)
        assertEquals(1, fake.analyses)
        model.consent(); runCurrent()
        assertEquals(2, fake.analyses)
    }

    @Test fun `new letter clears session content and sender remains local`() = runTest(dispatcher) {
        analyze(); model.sender(Sender("Testname", "Testanschrift", "")); model.keywords("Test")
        model.newLetter()
        assertFalse(model.state.value.input.hasContent)
        assertNull(model.state.value.result)
        assertEquals("", model.state.value.keywords)
        assertEquals("Testname", BriefampelViewModel(app, fake, true, zugang = OffenerZugang, verlauf = FluechtigerVerlauf()).state.value.sender.name)
        model.forgetSender()
        assertEquals(Sender(), BriefampelViewModel(app, fake, true, zugang = OffenerZugang, verlauf = FluechtigerVerlauf()).state.value.sender)
    }

    @Test fun `unconfigured builds never call provider`() = runTest(dispatcher) {
        val unconfigured = BriefampelViewModel(app, fake, false, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        unconfigured.setText("Brief")
        unconfigured.analyze(); runCurrent()
        assertEquals(Sheet.SETTINGS, unconfigured.state.value.sheet)
        assertEquals(0, fake.analyses)
    }

    @Test fun `returning from example cannot confuse the original of an own letter`() {
        model.setText("Mein eigener Brief")
        model.example(Ampel.ROT); model.back()
        assertFalse(model.state.value.example)
        assertNull(model.state.value.result)
        assertEquals("Mein eigener Brief", model.state.value.input.text)
    }

    @Test fun `empty credit opens the packages instead of a fourth analysis`() = runTest(dispatcher) {
        repeat(3) { index ->
            model.setText("Brief $index"); model.analyze()
            if (!model.state.value.consent) model.consent()
            runCurrent(); model.back()
        }
        model.setText("Vierter Brief"); model.analyze(); runCurrent()
        assertEquals(3, fake.analyses)
        assertEquals(0, model.state.value.guthaben)
        assertEquals(Sheet.PAKETE, model.state.value.sheet)
    }

    @Test fun `personal key activates Gemini without Firebase and changes require new consent`() = runTest(dispatcher) {
        val keys = FakePersonalApi()
        val personal = BriefampelViewModel(app, fake, null, keys, dispatcher, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        assertFalse(personal.state.value.configured)
        personal.consent()
        personal.savePersonalKey("local-fake-key-for-tests-only"); runCurrent()
        assertTrue(personal.state.value.configured)
        assertTrue(personal.state.value.personalApi)
        assertFalse(personal.state.value.consent)
        personal.setText("Ein Brief"); personal.analyze()
        assertEquals(Sheet.CONSENT, personal.state.value.sheet)
        assertEquals(0, fake.analyses)
        personal.consent(); runCurrent()
        assertEquals(1, fake.analyses)
        personal.clearPersonalKey(); runCurrent()
        assertFalse(personal.state.value.personalApi)
        assertFalse(personal.state.value.configured)
        assertFalse(personal.state.value.consent)
    }

    @Test fun `storage failure keeps key unconfigured and exposes no secret in UI state`() = runTest(dispatcher) {
        val keys = FakePersonalApi(fail = true)
        val personal = BriefampelViewModel(app, fake, null, keys, dispatcher, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        val secret = "local-fake-secret-not-to-display"
        personal.savePersonalKey(secret); runCurrent()
        assertFalse(personal.state.value.configured)
        assertFalse(personal.state.value.configuringApi)
        assertNotNull(personal.state.value.apiMessage)
        assertFalse(personal.state.value.toString().contains(secret))
    }

    @Test fun `release-like configuration cannot store a personal key`() = runTest(dispatcher) {
        val keys = FakePersonalApi(supported = false)
        val personal = BriefampelViewModel(app, fake, false, keys, dispatcher, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        personal.savePersonalKey("local-fake-key-for-tests-only"); runCurrent()
        assertFalse(keys.configured)
        assertFalse(personal.state.value.personalApiSupported)
    }

    private suspend fun TestScope.analyze() {
        model.setText("Mein Testbrief"); model.analyze(); model.consent(); runCurrent()
    }
}

internal class FakeProvider(private val streamDelay: Long = 1000) : LlmProvider {
    var analyses = 0
    var drafts = 0
    var fail = false
    override suspend fun analyze(input: LetterInput, today: LocalDate): LetterAnalysis {
        analyses++
        if (fail) error("Testfehler")
        return Beispiele.fuer(Ampel.ROT, today).analyse
    }
    override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate): Flow<String> = flow {
        drafts++
        emit("Sehr geehrte Damen und Herren,")
        delay(streamDelay)
        emit("Sehr geehrte Damen und Herren,\nbitte senden Sie den Vertrag.")
    }
}

internal class FakePersonalApi(override val supported: Boolean = true, private val fail: Boolean = false) : PersonalApi {
    override var configured = false
    override fun save(key: String) { if (fail) error("Deliberate storage failure with $key"); configured = true }
    override fun clear() { configured = false }
}

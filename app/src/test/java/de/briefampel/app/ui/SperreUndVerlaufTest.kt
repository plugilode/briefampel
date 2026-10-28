package de.briefampel.app.ui

import android.app.Application
import android.content.Context
import de.briefampel.app.data.*
import de.briefampel.app.domain.*
import kotlinx.coroutines.*
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
class SperreUndVerlaufTest {
    private lateinit var app: Application
    private lateinit var fake: FakeProvider
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        app = RuntimeEnvironment.getApplication()
        listOf("briefampel_private", "briefampel_zugang").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().putString("sprache", "de").commit() }
        fake = FakeProvider()
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `pin is stored only as salted hash and pauses after five wrong tries`() {
        var uhr = 1_000_000L
        val zugang = ZugangsSpeicher(app) { uhr }
        assertFalse(zugang.eingerichtet)
        zugang.setzePin("482915")
        assertTrue(zugang.eingerichtet)
        val gespeichert = app.getSharedPreferences("briefampel_zugang", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(gespeichert.contains("482915"))
        assertEquals(PinErgebnis.Richtig, zugang.pruefePin("482915"))
        repeat(4) { assertTrue(zugang.pruefePin("000000") is PinErgebnis.Falsch) }
        assertEquals(PinErgebnis.Pause(30), zugang.pruefePin("000000"))
        assertTrue("auch die richtige PIN wartet die Pause ab", zugang.pruefePin("482915") is PinErgebnis.Pause)
        uhr += 31_000
        assertEquals(PinErgebnis.Richtig, zugang.pruefePin("482915"))
        zugang.zuruecksetzen()
        assertFalse(zugang.eingerichtet)
    }

    @Test fun `only six digit pins are accepted`() {
        assertTrue(istGueltigePin("012345"))
        assertFalse(istGueltigePin("12345"))
        assertFalse(istGueltigePin("12345a"))
    }

    @Test fun `history survives encoding with analysis and replies`() {
        val heute = LocalDate.of(2026, 10, 28)
        val eintrag = VerlaufEintrag("a", 123L, Beispiele.fuer(Ampel.ROT, heute).analyse, 3, listOf(GespeicherteAntwort("Sehr geehrte …", "Sachlich", 456L)))
        val zurueck = VerlaufJson.decode(VerlaufJson.encode(listOf(eintrag))).single()
        assertEquals(eintrag, zurueck)
    }

    @Test fun `first start asks for a pin and locking hides the app until the pin is entered`() = runTest(dispatcher) {
        val zugang = ZugangsSpeicher(app)
        val model = BriefampelViewModel(app, fake, true, ioDispatcher = dispatcher, zugang = zugang, verlauf = FluechtigerVerlauf())
        assertEquals(Sperre.EINRICHTEN, model.state.value.sperre)
        model.pinEinrichten("123")
        assertEquals(Sperre.EINRICHTEN, model.state.value.sperre)
        model.pinEinrichten("246810")
        assertEquals(Sperre.OFFEN, model.state.value.sperre)
        model.sperren()
        assertEquals(Sperre.GESPERRT, model.state.value.sperre)
        model.pinPruefen("111111")
        assertEquals(Sperre.GESPERRT, model.state.value.sperre)
        assertTrue(model.state.value.pinMeldung.orEmpty().contains("Falsche PIN"))
        model.pinPruefen("246810")
        assertEquals(Sperre.OFFEN, model.state.value.sperre)
        assertNull(model.state.value.pinMeldung)
    }

    @Test fun `checked letters land once in the history and keep written replies`() = runTest(dispatcher) {
        val speicher = FluechtigerVerlauf()
        val model = BriefampelViewModel(app, fake, true, ioDispatcher = dispatcher, zugang = OffenerZugang, verlauf = speicher)
        model.setText("Mein Testbrief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(1, model.state.value.verlauf.size)
        model.back(); model.analyze(); runCurrent()
        assertEquals("Cache-Treffer erzeugt keinen zweiten Eintrag", 1, model.state.value.verlauf.size)
        model.openReply(); model.keywords("Vertrag anfordern"); model.write(); advanceUntilIdle()
        val eintrag = model.state.value.verlauf.single()
        assertEquals(1, eintrag.antworten.size)
        assertEquals(eintrag, speicher.lade().single())

        model.newLetter(); model.verlaufOeffnen()
        model.eintragOeffnen(eintrag.id)
        assertTrue(model.state.value.ausVerlauf)
        assertEquals(Screen.RESULT, model.state.value.screen)
        model.back()
        assertEquals(Screen.VERLAUF, model.state.value.screen)
        model.eintragLoeschen(eintrag.id); runCurrent()
        assertTrue(model.state.value.verlauf.isEmpty())
        assertTrue(speicher.lade().isEmpty())
    }

    @Test fun `examples never enter the history`() = runTest(dispatcher) {
        val model = BriefampelViewModel(app, fake, true, ioDispatcher = dispatcher, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.example(Ampel.ROT); runCurrent()
        assertTrue(model.state.value.verlauf.isEmpty())
    }

    @Test fun `forgotten pin wipes history and asks for a new pin`() = runTest(dispatcher) {
        val zugang = ZugangsSpeicher(app).apply { setzePin("135790") }
        val speicher = FluechtigerVerlauf(listOf(VerlaufEintrag("x", 1L, Beispiele.fuer(Ampel.GELB, LocalDate.now()).analyse, 1)))
        val model = BriefampelViewModel(app, fake, true, ioDispatcher = dispatcher, zugang = zugang, verlauf = speicher)
        runCurrent()
        assertEquals(Sperre.GESPERRT, model.state.value.sperre)
        assertEquals(1, model.state.value.verlauf.size)
        model.pinVergessen(); runCurrent()
        assertEquals(Sperre.EINRICHTEN, model.state.value.sperre)
        assertTrue(model.state.value.verlauf.isEmpty())
        assertTrue(speicher.lade().isEmpty())
        assertFalse(zugang.eingerichtet)
    }

    @Test fun `fourth reply to the same letter costs one more analysis`() = runTest(dispatcher) {
        val model = BriefampelViewModel(app, fake, true, ioDispatcher = dispatcher, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        assertEquals(Guthaben.START, model.state.value.guthaben)
        model.setText("Mein Testbrief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(2, model.state.value.guthaben)
        model.openReply(); model.keywords("Vertrag anfordern")
        repeat(3) { model.write(); advanceUntilIdle() }
        assertEquals("drei Antworten sind in der Analyse enthalten", 2, model.state.value.guthaben)
        assertEquals(0, model.freieAntworten())
        model.write(); advanceUntilIdle()
        assertEquals(1, model.state.value.guthaben)
        assertEquals(2, model.freieAntworten())
    }

    @Test fun `without credit an extra reply opens the packages and sends nothing`() = runTest(dispatcher) {
        val model = BriefampelViewModel(app, fake, true, ioDispatcher = dispatcher, zugang = OffenerZugang, verlauf = FluechtigerVerlauf())
        model.setText("Brief A"); model.analyze(); model.consent(); runCurrent()
        model.openReply(); model.keywords("Punkt")
        repeat(3) { model.write(); advanceUntilIdle() }
        app.getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().putInt("guthaben", 0).commit()
        val vorher = fake.drafts
        model.write(); runCurrent()
        assertEquals(vorher, fake.drafts)
        assertEquals(Sheet.PAKETE, model.state.value.sheet)
    }
}

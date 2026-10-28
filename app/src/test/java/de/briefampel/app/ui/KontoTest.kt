package de.briefampel.app.ui

import android.app.Activity
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Store-Version mit Server: Anmeldung vor der ersten Prüfung, Guthaben kommt vom Server. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class KontoTest {
    private lateinit var app: Application
    private lateinit var konto: FakeKonto
    private lateinit var fake: FakeProvider
    private lateinit var model: BriefampelViewModel
    private val dispatcher = StandardTestDispatcher()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().putString("sprache", "de").commit()
        konto = FakeKonto()
        fake = FakeProvider()
        model = BriefampelViewModel(app, fake, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = konto)
    }
    @After fun cleanup() { model.stop(); Dispatchers.resetMain() }

    @Test fun `erst anmelden, dann wird sofort geprueft`() = runTest(dispatcher) {
        assertTrue(model.state.value.serverModus)
        assertTrue(model.state.value.configured)
        model.setText("Mein Brief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(Sheet.ANMELDEN, model.state.value.sheet)
        assertEquals(0, fake.analyses)
        model.anmelden(activity); runCurrent()
        assertTrue(model.state.value.angemeldet)
        assertEquals("ana@example.com", model.state.value.email)
        assertEquals(1, fake.analyses)
        assertEquals(Screen.RESULT, model.state.value.screen)
        assertEquals("1,99 €", model.state.value.preise["analysen_10"])
    }

    @Test fun `Guthaben zeigt den Server-Stand und wird lokal nicht abgezogen`() = runTest(dispatcher) {
        konto.angemeldet = true
        // Beim Start mit bestehender Anmeldung holt die App den Stand vom Server.
        model = BriefampelViewModel(app, fake, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = konto)
        runCurrent()
        assertEquals(7, model.state.value.guthaben)
        model.setText("Mein Brief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(Screen.RESULT, model.state.value.screen)
        assertEquals(7, model.state.value.guthaben)
        konto.guthaben.value = 6
        runCurrent()
        assertEquals(6, model.state.value.guthaben)
    }

    @Test fun `leeres Server-Guthaben oeffnet die Pakete, Kauf meldet neuen Stand`() = runTest(dispatcher) {
        konto.angemeldet = true
        val leer = object : LlmProvider by fake {
            override suspend fun analyze(input: LetterInput, today: LocalDate, sprache: Sprache): LetterAnalysis = throw GuthabenLeer()
        }
        model = BriefampelViewModel(app, leer, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = konto)
        model.setText("Mein Brief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(Sheet.PAKETE, model.state.value.sheet)
        assertNull(model.state.value.error)
        model.kaufen(activity, "analysen_30"); runCurrent()
        assertEquals("analysen_30", konto.gekauft)
        assertEquals("Danke! Du hast jetzt 37 Analysen.", model.state.value.kaufMeldung)
        assertEquals(37, model.state.value.guthaben)
    }

    @Test fun `abgelaufene Anmeldung fuehrt zurueck zum Anmelden`() = runTest(dispatcher) {
        konto.angemeldet = true
        val abgelaufen = object : LlmProvider by fake {
            override suspend fun analyze(input: LetterInput, today: LocalDate, sprache: Sprache): LetterAnalysis = throw AnmeldungNoetig()
        }
        model = BriefampelViewModel(app, abgelaufen, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = konto)
        model.setText("Mein Brief")
        model.analyze(); model.consent(); runCurrent()
        assertEquals(Sheet.ANMELDEN, model.state.value.sheet)
        assertFalse(model.state.value.angemeldet)
    }
}

internal class FakeKonto : Konto {
    override var angemeldet = false
    override val email get() = if (angemeldet) "ana@example.com" else null
    override val guthaben = MutableStateFlow<Int?>(null)
    var gekauft: String? = null
    override suspend fun anmelden(activity: Activity): Boolean { angemeldet = true; return true }
    override suspend fun guthabenLaden(): Int { guthaben.value = 7; return 7 }
    override suspend fun preise() = mapOf("analysen_10" to "1,99 €", "analysen_30" to "4,99 €", "analysen_100" to "9,99 €")
    override suspend fun kaufen(activity: Activity, produktId: String): Int { gekauft = produktId; guthaben.value = 37; return 37 }
    override suspend fun offeneKaeufeEinloesen() {}
    override fun abmelden() { angemeldet = false }
}

package de.briefampel.app.ui

import android.app.Application
import android.content.Context
import de.briefampel.app.data.*
import de.briefampel.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class SpracheTest {
    private lateinit var app: Application
    private val dispatcher = StandardTestDispatcher()
    private val sprachen = mutableListOf<Sprache>()
    private val provider = object : LlmProvider {
        override suspend fun analyze(input: LetterInput, today: LocalDate, sprache: Sprache): LetterAnalysis {
            sprachen += sprache
            return Beispiele.fuer(Ampel.GELB, today, sprache).analyse
        }
        override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate): Flow<String> = emptyFlow()
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("briefampel_private", Context.MODE_PRIVATE).edit().clear().putBoolean("consent_v2", true).commit()
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    private fun model() = BriefampelViewModel(app, provider, configured = true, zugang = OffenerZugang, verlauf = FluechtigerVerlauf(), konto = null)

    @Test fun `erster Start fragt nach der Sprache, danach nie wieder`() {
        val erst = model()
        assertTrue(erst.state.value.spracheWaehlen)
        erst.spracheGewaehlt(Sprache.ES)
        assertFalse(erst.state.value.spracheWaehlen)
        val wieder = model()
        assertFalse(wieder.state.value.spracheWaehlen)
        assertEquals(Sprache.ES, wieder.state.value.sprache)
    }

    @Test fun `Pruefung geht in der gewaehlten Sprache an die KI, Wechsel umgeht den Zwischenspeicher`() = runTest(dispatcher) {
        val m = model()
        m.spracheGewaehlt(Sprache.EN)
        m.setText("Brief"); m.analyze(); runCurrent()
        assertEquals("Query about your 2025 tax return", m.state.value.result?.analyse?.titel)
        m.back(); m.sprache(Sprache.ES); m.analyze(); runCurrent()
        assertEquals(listOf(Sprache.EN, Sprache.ES), sprachen)
        assertEquals("Consulta sobre tu declaración de impuestos de 2025", m.state.value.result?.analyse?.titel)
    }

    @Test fun `Meldungen kommen in der gewaehlten Sprache`() {
        val m = model()
        m.spracheGewaehlt(Sprache.ES)
        m.error(errorMessage(java.net.UnknownHostException()))
        assertEquals("Sin conexión. Revisa tu internet e inténtalo de nuevo.", m.state.value.error)
        assertEquals("quedan 3 días", Fristen.restText(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 1), Sprache.ES))
        assertEquals("2 checks", Sprache.EN.analysen(2))
    }

    @Test fun `Tabelle ist vollstaendig und Platzhalter passen`() {
        assertEquals("Doppelte deutsche Schlüssel", Uebersetzung.tabelle.size, Uebersetzung.en.size)
        Uebersetzung.tabelle.forEach { (de, en, es) ->
            val platzhalter = Regex("%[sd]")
            assertEquals(de, platzhalter.findAll(de).map { it.value }.toList(), platzhalter.findAll(en).map { it.value }.toList())
            assertEquals(de, platzhalter.findAll(de).map { it.value }.toList(), platzhalter.findAll(es).map { it.value }.toList())
            assertTrue("Leere Übersetzung: $de", en.isNotBlank() && es.isNotBlank())
        }
    }

    /** Jeder Text, der im Code mit sag(), tr() oder .text() angezeigt wird, muss in der Tabelle stehen. */
    @Test fun `jeder angezeigte Text hat eine Uebersetzung`() {
        val quellen = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(quellen.isNotEmpty())
        val muster = Regex("""(?:\bsag|\btr|\.text|\bsagVm)\("((?:[^"\\]|\\.)*)"""")
        val fehlend = quellen.flatMap { f ->
            muster.findAll(f.readText()).map { it.groupValues[1] }.filterNot { "\${" in it }
                .map { it.replace("\\n", "\n").replace("\\\"", "\"").replace("\\$", "$") }
                .filterNot { it in Uebersetzung.en }.map { "${f.name}: $it" }.toList()
        }
        assertEquals(emptyList<String>(), fehlend)
    }
}

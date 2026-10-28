package de.briefampel.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.briefampel.app.BuildConfig
import de.briefampel.app.data.*
import de.briefampel.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import android.os.SystemClock

enum class Screen { INPUT, RESULT, REPLY, VERLAUF }
enum class Sperre { OFFEN, EINRICHTEN, GESPERRT }
enum class Sheet { CONSENT, PASTE, SETTINGS, PRIVACY, ORIGINAL, PAKETE, ANMELDEN }

data class UiState(
    val screen: Screen = Screen.INPUT,
    val sheet: Sheet? = null,
    val input: LetterInput = LetterInput(),
    val result: GepruefterBrief? = null,
    val example: Boolean = false,
    val loading: Boolean = false,
    val importing: Boolean = false,
    val writing: Boolean = false,
    val error: String? = null,
    val keywords: String = "",
    val tone: Tone = Tone.SACHLICH,
    val sender: Sender = Sender(),
    val reply: String = "",
    val dark: Boolean = true,
    /** Verbleibende Analysen (Startguthaben plus gekaufte Pakete). */
    val guthaben: Int = Guthaben.START,
    val consent: Boolean = false,
    val configured: Boolean = false,
    val personalApi: Boolean = false,
    val personalApiSupported: Boolean = false,
    val configuringApi: Boolean = false,
    val apiMessage: String? = null,
    val showIntro: Boolean = false,
    /** Kurzes Marken-Intro bei jedem weiteren Start (das lange Intro gibt es nur beim ersten Mal). */
    val kurzIntro: Boolean = false,
    val introTon: Boolean = true,
    val checkedAt: String = "",
    val sperre: Sperre = Sperre.OFFEN,
    val biometrie: Boolean = false,
    val pinMeldung: String? = null,
    val verlauf: List<VerlaufEintrag> = emptyList(),
    /** Verlaufseintrag des gerade gezeigten Ergebnisses; Antworten werden dort abgelegt. */
    val eintragId: String? = null,
    val ausVerlauf: Boolean = false,
    val sprache: Sprache = Sprache.DE,
    /** Erster Start: Sprache wählen, bevor irgendetwas anderes kommt. */
    val spracheWaehlen: Boolean = false,
    /** Store-Version: Guthaben und KI laufen über den eigenen Server, mit Google-Konto. */
    val serverModus: Boolean = false,
    val angemeldet: Boolean = false,
    val email: String? = null,
    val anmeldenLaeuft: Boolean = false,
    /** Echte Preise aus Google Play je Paket-ID; leer, solange Play sie nicht geliefert hat. */
    val preise: Map<String, String> = emptyMap(),
    val kaufLaeuft: Boolean = false,
    val kaufMeldung: String? = null,
)

class BriefampelViewModel @JvmOverloads constructor(
    application: Application,
    private val provider: LlmProvider = createLlmProvider(application),
    private val configured: Boolean? = null,
    private val personalApi: PersonalApi = createPersonalApi(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zugang: Zugang = ZugangsSpeicher(application),
    private val verlauf: Verlauf = VerlaufSpeicher(application),
    private val konto: Konto? = erstelleKonto(application),
) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    val inputReader = InputReader(application)
    private fun today() = LocalDate.now(ZoneId.of("Europe/Berlin"))
    private fun isConfigured() = konto != null || (configured ?: personalApi.configured)
    private val mutable = MutableStateFlow(UiState(sender = store.sender, dark = store.dark, consent = store.consent,
        configured = isConfigured(), personalApi = personalApi.configured, personalApiSupported = personalApi.supported,
        showIntro = !store.introSeen,
        kurzIntro = store.introSeen,
        introTon = store.introTon,
        guthaben = store.guthaben,
        sperre = when { !zugang.aktiv -> Sperre.OFFEN; !zugang.eingerichtet -> Sperre.EINRICHTEN; else -> Sperre.GESPERRT },
        biometrie = zugang.biometrie,
        serverModus = konto != null, angemeldet = konto?.angemeldet == true, email = konto?.email,
        sprache = Sprache.von(store.sprache) ?: Sprache.vomGeraet(), spracheWaehlen = store.sprache == null))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var workSerial = 0
    private var cached: Triple<String, Long, LetterAnalysis>? = null
    /** Meldungen entstehen auf Deutsch und werden in der gewählten Sprache gespeichert. */
    private fun sagVm(de: String, vararg args: Any?) = state.value.sprache.text(de, *args)
    private var afterConsent = false
    /** Nach der Anmeldung direkt weiterprüfen, wenn die Anmeldung von „Prüfen“ kam. */
    private var nachAnmeldung = false
    /** Fingerabdruck der zuletzt gespeicherten Prüfung, damit ein Cache-Treffer keinen doppelten Eintrag erzeugt. */
    private var gespeichert: Pair<String, String>? = null

    /** Gespeichert wird erst nach dem Laden, sonst könnte ein früher Eintrag den alten Verlauf überschreiben. */
    private var ladenFehlgeschlagen = false
    /** Nach „alles löschen“ darf ein noch laufendes Laden nichts zurückbringen. */
    private var verworfen = false
    private val laden: Job = viewModelScope.launch {
        val geladen = try { withContext(ioDispatcher) { verlauf.lade() } } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        if (geladen == null) {
            ladenFehlgeschlagen = true
            error(tr("Der Verlauf konnte nicht geöffnet werden. Neue Einträge werden vorerst nicht gespeichert."))
        } else if (!verworfen) {
            // Einträge, die schon vor dem Laden entstanden sind, bleiben vorne erhalten.
            mutable.update { s -> s.copy(verlauf = s.verlauf + geladen.filter { g -> s.verlauf.none { it.id == g.id } }) }
        }
    }

    // ---------------------------------------------------------------- Sperre

    fun pinEinrichten(pin: String) {
        if (!istGueltigePin(pin)) { mutable.update { it.copy(pinMeldung = sagVm("Die PIN braucht genau 6 Ziffern.")) }; return }
        try { zugang.setzePin(pin) } catch (_: Exception) { mutable.update { it.copy(pinMeldung = sagVm("Die PIN konnte nicht gespeichert werden.")) }; return }
        mutable.update { it.copy(sperre = Sperre.OFFEN, pinMeldung = null) }
    }

    fun biometrie(an: Boolean) { zugang.biometrie = an; mutable.update { it.copy(biometrie = an) } }

    fun pinPruefen(pin: String) {
        when (val ergebnis = zugang.pruefePin(pin)) {
            PinErgebnis.Richtig -> mutable.update { it.copy(sperre = Sperre.OFFEN, pinMeldung = null) }
            is PinErgebnis.Falsch -> mutable.update { it.copy(pinMeldung = if (ergebnis.versucheBisPause == 1) sagVm("Falsche PIN. Noch 1 Versuch bis zur Pause.") else sagVm("Falsche PIN. Noch %d Versuche bis zur Pause.", ergebnis.versucheBisPause)) }
            is PinErgebnis.Pause -> mutable.update { it.copy(pinMeldung = sagVm("Zu viele Versuche. Bitte %d Sekunden warten.", ergebnis.sekunden)) }
        }
    }

    fun entsperrt() { if (state.value.sperre == Sperre.GESPERRT) mutable.update { it.copy(sperre = Sperre.OFFEN, pinMeldung = null) } }

    fun sperren() {
        if (!zugang.aktiv || !zugang.eingerichtet || state.value.sperre != Sperre.OFFEN) return
        if (state.value.writing) stop()
        mutable.update { it.copy(sperre = Sperre.GESPERRT, sheet = null, pinMeldung = null) }
    }

    /** PIN vergessen: Sperre und Verlauf werden gelöscht, denn ohne PIN darf niemand an die alten Briefe. */
    fun pinVergessen() {
        zugang.zuruecksetzen()
        gespeichert = null
        mutable.update { it.copy(sperre = Sperre.EINRICHTEN, biometrie = false, pinMeldung = null, verlauf = emptyList(), eintragId = null, ausVerlauf = false,
            screen = Screen.INPUT, result = null, reply = "", keywords = "") }
        verworfen = true
        verlaufSichern(emptyList(), ersetzen = true)
    }

    // ---------------------------------------------------------------- Verlauf

    fun verlaufOeffnen() { mutable.update { it.copy(screen = Screen.VERLAUF, sheet = null, error = null) } }

    fun eintragOeffnen(id: String) {
        val eintrag = state.value.verlauf.firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(screen = Screen.RESULT, result = GepruefterBrief(eintrag.analyse, "", eintrag.seiten), example = false,
            input = LetterInput(), ausVerlauf = true, eintragId = id, reply = "", keywords = "", tone = Tone.SACHLICH, error = null) }
    }

    fun eintragLoeschen(id: String) {
        val neu = state.value.verlauf.filterNot { it.id == id }
        if (gespeichert?.second == id) gespeichert = null
        mutable.update {
            if (it.eintragId == id) it.copy(verlauf = neu, eintragId = null, ausVerlauf = false, screen = Screen.VERLAUF, result = null, reply = "")
            else it.copy(verlauf = neu)
        }
        verlaufSichern(neu)
    }

    fun verlaufLoeschen() {
        gespeichert = null
        mutable.update { it.copy(verlauf = emptyList(), eintragId = null, ausVerlauf = false) }
        verworfen = true
        verlaufSichern(emptyList(), ersetzen = true)
    }

    /** Schreibt den aktuellen Stand, sobald der Verlauf geladen ist. Nach einem Ladefehler wird nichts überschrieben. */
    private fun verlaufSichern(@Suppress("UNUSED_PARAMETER") liste: List<VerlaufEintrag>, ersetzen: Boolean = false) {
        viewModelScope.launch {
            laden.join()
            if (ladenFehlgeschlagen && !ersetzen) return@launch
            try { withContext(ioDispatcher) { verlauf.speichere(state.value.verlauf) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error(tr("Der Verlauf konnte nicht gespeichert werden.")) }
        }
    }

    private fun antwortMerken(text: String, ton: Tone) {
        val id = state.value.eintragId ?: return
        if (text.isBlank() || state.value.example) return
        val neu = state.value.verlauf.map { e ->
            if (e.id != id) return@map e
            // Sind die 3 Antworten dieser Analyse verbraucht, kostet die nächste eine weitere Analyse.
            val neueAnalyse = Guthaben.freieAntworten(e.bezahlteAnalysen, e.antworten.size) == 0
            // Mit Server bucht der Server selbst ab und meldet den neuen Stand.
            if (neueAnalyse && konto == null) store.guthaben = store.guthaben - 1
            e.copy(
                antworten = e.antworten + GespeicherteAntwort(text, ton.label, System.currentTimeMillis()),
                bezahlteAnalysen = e.bezahlteAnalysen + if (neueAnalyse) 1 else 0,
            )
        }
        mutable.update { it.copy(verlauf = neu, guthaben = store.guthaben) }
        verlaufSichern(neu)
    }

    init {
        konto?.let { k ->
            // Der Server ist die Wahrheit; lokal merken wir nur den letzten Stand fürs Anzeigen.
            viewModelScope.launch { k.guthaben.collect { g -> if (g != null) { store.guthaben = g; mutable.update { it.copy(guthaben = g) } } } }
            if (k.angemeldet) kontoAbgleichen()
        }
    }

    private fun kontoAbgleichen() {
        val k = konto ?: return
        viewModelScope.launch {
            try { k.guthabenLaden() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {}
            try { k.offeneKaeufeEinloesen() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {}
            try { val p = k.preise(); mutable.update { it.copy(preise = p) } } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {}
        }
    }

    fun anmelden(activity: android.app.Activity) {
        val k = konto ?: return
        if (state.value.anmeldenLaeuft) return
        mutable.update { it.copy(anmeldenLaeuft = true, error = null) }
        viewModelScope.launch {
            try {
                if (!k.anmelden(activity)) return@launch
                mutable.update { it.copy(angemeldet = true, email = k.email, sheet = if (it.sheet == Sheet.ANMELDEN) null else it.sheet) }
                kontoAbgleichen()
                if (nachAnmeldung) { nachAnmeldung = false; analyze() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { error(errorMessage(e)) }
            finally { mutable.update { it.copy(anmeldenLaeuft = false) } }
        }
    }

    fun abmelden() {
        konto?.abmelden()
        cached = null
        mutable.update { it.copy(angemeldet = false, email = null) }
    }

    fun kaufen(activity: android.app.Activity, produktId: String) {
        val k = konto ?: return
        if (!k.angemeldet) { mutable.update { it.copy(sheet = Sheet.ANMELDEN) }; return }
        if (state.value.kaufLaeuft) return
        mutable.update { it.copy(kaufLaeuft = true, kaufMeldung = null) }
        viewModelScope.launch {
            try {
                val neu = k.kaufen(activity, produktId)
                if (neu != null) mutable.update { it.copy(kaufMeldung = sagVm("Danke! Du hast jetzt %s.", it.sprache.analysen(neu))) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { mutable.update { it.copy(kaufMeldung = sagVm(errorMessage(e))) } }
            finally { mutable.update { it.copy(kaufLaeuft = false) } }
        }
    }

    /** Server-Fehler, die einen eigenen Bildschirm haben, statt einer Fehlermeldung. */
    private fun fehler(e: Exception) {
        when (e) {
            is GuthabenLeer -> mutable.update { it.copy(sheet = Sheet.PAKETE, error = null) }
            is AnmeldungNoetig -> { abmelden(); mutable.update { it.copy(sheet = Sheet.ANMELDEN, error = null) } }
            else -> error(errorMessage(e))
        }
    }

    fun sheet(sheet: Sheet?) { mutable.update { it.copy(sheet = sheet, error = null, kaufMeldung = null) }; if (sheet == null) { afterConsent = false; nachAnmeldung = false } }
    fun error(text: String) { mutable.update { it.copy(error = it.sprache.text(text)) } }

    fun spracheGewaehlt(sprache: Sprache) { store.sprache = sprache.code; mutable.update { it.copy(sprache = sprache, spracheWaehlen = false) } }
    fun sprache(sprache: Sprache) {
        store.sprache = sprache.code
        // Die nächste Prüfung soll in der neuen Sprache zusammenfassen, nicht aus dem Zwischenspeicher kommen.
        cached = null
        mutable.update { it.copy(sprache = sprache) }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun finishIntro() { store.introSeen = true; mutable.update { it.copy(showIntro = false) } }
    /** Antworten, die zum aktuellen Brief noch ohne neue Analyse gehen; null ohne Verlaufseintrag. */
    fun freieAntworten(s: UiState = state.value): Int? =
        s.verlauf.firstOrNull { it.id == s.eintragId }?.let { Guthaben.freieAntworten(it.bezahlteAnalysen, it.antworten.size) }

    /** Nur in der Debug-Testversion: Guthaben zum Ausprobieren auffüllen. */
    fun testGuthaben() {
        if (!BuildConfig.DEBUG || konto != null) return
        store.guthaben = store.guthaben + 10
        mutable.update { it.copy(guthaben = store.guthaben) }
    }

    fun kurzIntroFertig() { mutable.update { it.copy(kurzIntro = false) } }
    fun introTon(an: Boolean) { store.introTon = an; mutable.update { it.copy(introTon = an) } }
    fun replayIntro() { mutable.update { it.copy(showIntro = true, sheet = null) } }
    fun dark() { store.dark = !state.value.dark; mutable.update { it.copy(dark = store.dark) } }
    fun sender(sender: Sender) { store.sender = sender; mutable.update { it.copy(sender = sender) } }
    fun forgetSender() = sender(Sender())
    fun revokeConsent() { store.consent = false; cached = null; mutable.update { it.copy(consent = false) } }
    fun savePersonalKey(raw: String) = changePersonalKey { personalApi.save(raw.trim()) }
    fun clearPersonalKey() = changePersonalKey { personalApi.clear() }
    private fun changePersonalKey(change: () -> Unit) {
        if (!personalApi.supported || state.value.configuringApi || state.value.loading || state.value.writing || state.value.importing) return
        mutable.update { it.copy(configuringApi = true, apiMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { change() }
                cached = null
                afterConsent = false
                store.consent = false
                mutable.update { it.copy(configured = isConfigured(), personalApi = personalApi.configured, consent = false,
                    apiMessage = if (personalApi.configured) sagVm("Schlüssel gespeichert. Die Verbindung wird bei der nächsten Prüfung getestet.") else sagVm("Persönlicher Schlüssel gelöscht.")) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(apiMessage = sagVm("Schlüssel konnte nicht geändert werden. Bitte Eingabe und Gerätespeicher prüfen.")) } }
            finally { mutable.update { it.copy(configuringApi = false) } }
        }
    }
    fun setText(text: String) { mutable.update { it.copy(input = it.input.copy(text = text.take(60_000)), error = null) } }
    fun keywords(text: String) { mutable.update { it.copy(keywords = text.take(8000)) } }
    fun toggleKeyword(keyword: String) = keywords(de.briefampel.app.domain.toggleKeyword(state.value.keywords, keyword))
    fun tone(tone: Tone) { mutable.update { it.copy(tone = tone) } }
    fun remove(id: String) { mutable.update { it.copy(input = it.input.copy(attachments = it.input.attachments.filterNot { f -> f.id == id })) } }

    /** @param sofortPruefen nach dem Scannen oder Auswählen direkt prüfen: Brief rein, Ampel raus, ohne Extra-Tippen. */
    fun import(uris: List<Uri>, sharedText: String? = null, sofortPruefen: Boolean = false) {
        if (state.value.loading || state.value.writing || state.value.importing) { error(tr("Bitte den laufenden Vorgang zuerst beenden.")); return }
        mutable.update { it.copy(importing = true, error = null, screen = Screen.INPUT, sheet = null, result = null, example = false, reply = "", showIntro = false) }
        viewModelScope.launch {
            try {
                require(uris.size <= 12) { tr("Zu viele Dateien. Höchstens 12 Seiten auswählen.") }
                val read = uris.map { inputReader.read(it) }
                val input = state.value.input.let { old ->
                    LetterInput(sharedText?.take(60_001) ?: old.text, (old.attachments + read).distinctBy { it.id })
                }
                input.validate()
                mutable.update { it.copy(input = input) }
                if (sofortPruefen) { mutable.update { it.copy(importing = false) }; analyze() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { error(errorMessage(e)) }
            finally { mutable.update { it.copy(importing = false) } }
        }
    }

    fun example(ampel: Ampel) {
        // Relativ zu heute, damit die Beispielfrist nie in der Vergangenheit liegt.
        mutable.update { it.copy(screen = Screen.RESULT, result = Beispiele.fuer(ampel, today(), it.sprache), example = true, error = null, sheet = null, showIntro = false) }
    }

    fun analyze() {
        if (state.value.loading || state.value.importing || state.value.configuringApi) return
        try { state.value.input.validate() } catch (e: Exception) { error(errorMessage(e)); return }
        if (!isConfigured()) { mutable.update { it.copy(sheet = Sheet.SETTINGS) }; return }
        if (!store.consent) { afterConsent = true; mutable.update { it.copy(sheet = Sheet.CONSENT) }; return }
        val input = state.value.input
        val day = today()
        val cache = cached?.takeIf { it.first == input.fingerprint() + state.value.sprache.code && SystemClock.elapsedRealtime() - it.second < 300_000 }
        if (cache != null) { showAnalysis(cache.third, input); return }
        if (konto != null && !konto.angemeldet) { nachAnmeldung = true; mutable.update { it.copy(sheet = Sheet.ANMELDEN, error = null) }; return }
        if (store.guthaben <= 0) { mutable.update { it.copy(sheet = Sheet.PAKETE, error = null) }; return }
        mutable.update { it.copy(loading = true, error = null) }
        val serial = ++workSerial
        work = viewModelScope.launch {
            try {
                val sprache = state.value.sprache
                val analysis = provider.analyze(input, day, sprache)
                if (serial != workSerial) return@launch
                cached = Triple(input.fingerprint() + sprache.code, SystemClock.elapsedRealtime(), analysis)
                if (konto == null) store.guthaben = store.guthaben - 1
                showAnalysis(analysis, input)
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (serial == workSerial) fehler(e) }
            finally { if (serial == workSerial) mutable.update { it.copy(loading = false) } }
        }
    }

    private fun showAnalysis(analysis: LetterAnalysis, input: LetterInput) {
        val fingerabdruck = input.fingerprint()
        val vorhanden = gespeichert?.takeIf { it.first == fingerabdruck && state.value.verlauf.any { e -> e.id == it.second } }?.second
        val id = vorhanden ?: java.util.UUID.randomUUID().toString()
        val liste = if (vorhanden != null) state.value.verlauf
            else listOf(VerlaufEintrag(id, System.currentTimeMillis(), analysis, input.pages)) + state.value.verlauf
        gespeichert = fingerabdruck to id
        mutable.update { it.copy(screen = Screen.RESULT, result = GepruefterBrief(analysis, input.text, input.pages), example = false,
            reply = "", keywords = "", tone = Tone.SACHLICH, sheet = null, guthaben = store.guthaben,
            verlauf = liste, eintragId = id, ausVerlauf = false) }
        if (vorhanden == null) verlaufSichern(liste)
    }

    fun consent() {
        val pending = afterConsent
        afterConsent = false
        store.consent = true
        mutable.update { it.copy(consent = true, sheet = null) }
        if (pending) analyze()
    }

    fun openReply() {
        if (state.value.example) { error(tr("Lade zuerst deinen eigenen Brief hoch.")); return }
        mutable.update { it.copy(screen = Screen.REPLY, error = null) }
    }

    fun write() {
        val s = state.value
        if (s.writing || s.example || s.result == null) return
        if (s.keywords.isBlank()) { error(tr("Ein paar Stichworte brauche ich.")); return }
        if (!s.consent) { error(tr("Bitte stimme in den Einstellungen zuerst der KI-Verarbeitung zu.")); return }
        if (freieAntworten(s) == 0 && store.guthaben <= 0) {
            mutable.update { it.copy(sheet = Sheet.PAKETE, error = it.sprache.text("Die 3 Antworten zu diesem Brief sind verbraucht. Für weitere brauchst du eine neue Analyse.")) }
            return
        }
        mutable.update { it.copy(writing = true, reply = "", error = null) }
        val serial = ++workSerial
        work = viewModelScope.launch {
            try {
                provider.draft(s.input, s.result.analyse, s.keywords, s.tone, s.sender, today()).collect { text -> if (serial == workSerial) mutable.update { it.copy(reply = text) } }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (serial == workSerial) fehler(e) }
            finally {
                if (serial == workSerial) {
                    mutable.update { it.copy(writing = false) }
                    antwortMerken(state.value.reply, state.value.tone)
                }
            }
        }
    }

    fun stop() {
        val warSchreibend = state.value.writing
        workSerial++; work?.cancel()
        mutable.update { it.copy(loading = false, writing = false) }
        // Auch ein gestoppter Entwurf bleibt erhalten, im Bildschirm und im Verlauf.
        if (warSchreibend) antwortMerken(state.value.reply, state.value.tone)
    }
    fun back() {
        if (state.value.showIntro) { finishIntro(); return }
        if (state.value.sheet != null) { sheet(null); return }
        if (state.value.loading || state.value.writing) { stop(); return }
        mutable.update {
            when {
                it.screen == Screen.REPLY -> it.copy(screen = Screen.RESULT, error = null)
                it.screen == Screen.RESULT && it.ausVerlauf -> it.copy(screen = Screen.VERLAUF, result = null, ausVerlauf = false, eintragId = null, reply = "", error = null)
                else -> it.copy(screen = Screen.INPUT, result = null, example = false, ausVerlauf = false, error = null)
            }
        }
    }
    fun newLetter() {
        stop()
        mutable.update { it.copy(screen = Screen.INPUT, input = LetterInput(), result = null, example = false, keywords = "", reply = "", error = null, eintragId = null, ausVerlauf = false) }
        cached = null
    }
}

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

enum class Screen { INPUT, RESULT, REPLY }
enum class Sheet { CONSENT, PASTE, SETTINGS, PRIVACY, ORIGINAL }

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
    val remaining: Int = 3,
    val consent: Boolean = false,
    val configured: Boolean = false,
    val personalApi: Boolean = false,
    val personalApiSupported: Boolean = false,
    val configuringApi: Boolean = false,
    val apiMessage: String? = null,
    val showIntro: Boolean = false,
    val checkedAt: String = "",
)

class BriefampelViewModel @JvmOverloads constructor(
    application: Application,
    private val provider: LlmProvider = createLlmProvider(application),
    private val configured: Boolean? = null,
    private val personalApi: PersonalApi = createPersonalApi(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    val inputReader = InputReader(application)
    private fun today() = LocalDate.now(ZoneId.of("Europe/Berlin"))
    private fun isConfigured() = configured ?: personalApi.configured
    private val mutable = MutableStateFlow(UiState(sender = store.sender, dark = store.dark, consent = store.consent,
        configured = isConfigured(), personalApi = personalApi.configured, personalApiSupported = personalApi.supported,
        showIntro = !store.introSeen,
        remaining = store.usage.remaining(today().toString())))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var workSerial = 0
    private var cached: Triple<String, Long, LetterAnalysis>? = null
    private var afterConsent = false

    fun sheet(sheet: Sheet?) { mutable.update { it.copy(sheet = sheet, error = null) }; if (sheet == null) afterConsent = false }
    fun error(text: String) { mutable.update { it.copy(error = text) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun finishIntro() { store.introSeen = true; mutable.update { it.copy(showIntro = false) } }
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
                    apiMessage = if (personalApi.configured) "Schlüssel gespeichert. Die Verbindung wird bei der nächsten Prüfung getestet." else "Persönlicher Schlüssel gelöscht.") }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(apiMessage = "Schlüssel konnte nicht geändert werden. Bitte Eingabe und Gerätespeicher prüfen.") } }
            finally { mutable.update { it.copy(configuringApi = false) } }
        }
    }
    fun setText(text: String) { mutable.update { it.copy(input = it.input.copy(text = text.take(60_000)), error = null) } }
    fun keywords(text: String) { mutable.update { it.copy(keywords = text.take(8000)) } }
    fun toggleKeyword(keyword: String) = keywords(de.briefampel.app.domain.toggleKeyword(state.value.keywords, keyword))
    fun tone(tone: Tone) { mutable.update { it.copy(tone = tone) } }
    fun remove(id: String) { mutable.update { it.copy(input = it.input.copy(attachments = it.input.attachments.filterNot { f -> f.id == id })) } }

    fun import(uris: List<Uri>, sharedText: String? = null) {
        if (state.value.loading || state.value.writing || state.value.importing) { error("Bitte den laufenden Vorgang zuerst beenden."); return }
        mutable.update { it.copy(importing = true, error = null, screen = Screen.INPUT, sheet = null, result = null, example = false, reply = "", showIntro = false) }
        viewModelScope.launch {
            try {
                require(uris.size <= 12) { "Zu viele Dateien. Höchstens 12 Seiten auswählen." }
                val read = uris.map { inputReader.read(it) }
                val input = state.value.input.let { old ->
                    LetterInput(sharedText?.take(60_001) ?: old.text, (old.attachments + read).distinctBy { it.id })
                }
                input.validate()
                mutable.update { it.copy(input = input) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { error(errorMessage(e)) }
            finally { mutable.update { it.copy(importing = false) } }
        }
    }

    fun example(ampel: Ampel) {
        // Relativ zu heute, damit die Beispielfrist nie in der Vergangenheit liegt.
        mutable.update { it.copy(screen = Screen.RESULT, result = Beispiele.fuer(ampel, today()), example = true, error = null, sheet = null, showIntro = false) }
    }

    fun analyze() {
        if (state.value.loading || state.value.importing || state.value.configuringApi) return
        try { state.value.input.validate() } catch (e: Exception) { error(errorMessage(e)); return }
        if (!isConfigured()) { mutable.update { it.copy(sheet = Sheet.SETTINGS) }; return }
        if (!store.consent) { afterConsent = true; mutable.update { it.copy(sheet = Sheet.CONSENT) }; return }
        val input = state.value.input
        val day = today()
        val cache = cached?.takeIf { it.first == input.fingerprint() && SystemClock.elapsedRealtime() - it.second < 300_000 }
        if (cache != null) { showAnalysis(cache.third, input); return }
        if (store.usage.remaining(day.toString()) == 0) { error("Für heute sind deine drei Prüfungen aufgebraucht. Morgen geht es weiter."); return }
        mutable.update { it.copy(loading = true, error = null) }
        val serial = ++workSerial
        work = viewModelScope.launch {
            try {
                val analysis = provider.analyze(input, day)
                if (serial != workSerial) return@launch
                cached = Triple(input.fingerprint(), SystemClock.elapsedRealtime(), analysis)
                store.usage = store.usage.consume(day.toString())
                showAnalysis(analysis, input)
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (serial == workSerial) error(errorMessage(e)) }
            finally { if (serial == workSerial) mutable.update { it.copy(loading = false) } }
        }
    }

    private fun showAnalysis(analysis: LetterAnalysis, input: LetterInput) {
        mutable.update { it.copy(screen = Screen.RESULT, result = GepruefterBrief(analysis, input.text, input.pages), example = false,
            reply = "", keywords = "", tone = Tone.SACHLICH, sheet = null, remaining = store.usage.remaining(today().toString())) }
    }

    fun consent() {
        val pending = afterConsent
        afterConsent = false
        store.consent = true
        mutable.update { it.copy(consent = true, sheet = null) }
        if (pending) analyze()
    }

    fun openReply() {
        if (state.value.example) { error("Lade zuerst deinen eigenen Brief hoch."); return }
        mutable.update { it.copy(screen = Screen.REPLY, error = null) }
    }

    fun write() {
        val s = state.value
        if (s.writing || s.example || s.result == null) return
        if (s.keywords.isBlank()) { error("Ein paar Stichworte brauche ich."); return }
        if (!s.consent) { error("Bitte stimme in den Einstellungen zuerst der KI-Verarbeitung zu."); return }
        mutable.update { it.copy(writing = true, reply = "", error = null) }
        val serial = ++workSerial
        work = viewModelScope.launch {
            try {
                provider.draft(s.input, s.result.analyse, s.keywords, s.tone, s.sender, today()).collect { text -> if (serial == workSerial) mutable.update { it.copy(reply = text) } }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (serial == workSerial) error(errorMessage(e)) }
            finally { if (serial == workSerial) mutable.update { it.copy(writing = false) } }
        }
    }

    fun stop() { workSerial++; work?.cancel(); mutable.update { it.copy(loading = false, writing = false) } }
    fun back() {
        if (state.value.showIntro) { finishIntro(); return }
        if (state.value.sheet != null) { sheet(null); return }
        if (state.value.loading || state.value.writing) { stop(); return }
        mutable.update {
            if (it.screen == Screen.REPLY) it.copy(screen = Screen.RESULT, error = null)
            else it.copy(screen = Screen.INPUT, result = null, example = false, error = null)
        }
    }
    fun newLetter() {
        stop()
        mutable.update { it.copy(screen = Screen.INPUT, input = LetterInput(), result = null, example = false, keywords = "", reply = "", error = null) }
        cached = null
    }
}

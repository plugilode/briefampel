package de.briefampel.app.data

import android.content.Context
import de.briefampel.app.domain.*
import java.time.LocalDate

fun createPersonalApi(context: Context): PersonalApi = PersonalKeyStore(context)

/**
 * Mit eigenem Server (KI dort: Mistral oder Gemini, siehe functions/src/index.ts) (google-services.json und briefampel.server gesetzt) läuft alles über den Server,
 * sonst über den persönlichen Schlüssel der Test-APK (briefampel.ki in local.properties).
 */
fun createLlmProvider(context: Context): LlmProvider {
    val server = Dienste.server(context)
    if (server.verfuegbar) return ServerProvider(server)
    return object : LlmProvider {
        private val keys = PersonalKeyStore(context)
        private val direct: LlmProvider by lazy {
            if (keys.anbieter == KiAnbieter.GEMINI) DirectGeminiProvider(key = { keys.read() }) else DirectMistralProvider(key = { keys.read() })
        }

        override suspend fun analyze(input: LetterInput, today: LocalDate, sprache: Sprache) = direct.analyze(input, today, sprache)
        override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate) =
            direct.draft(input, analysis, keywords, tone, sender, today)
    }
}

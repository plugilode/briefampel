package de.briefampel.app.data

import android.content.Context
import de.briefampel.app.domain.*
import java.time.LocalDate

fun createPersonalApi(context: Context): PersonalApi = PersonalKeyStore(context)

fun createLlmProvider(context: Context): LlmProvider = object : LlmProvider {
    private val keys = PersonalKeyStore(context)
    private val direct by lazy { DirectGeminiProvider(key = { keys.read() }) }

    override suspend fun analyze(input: LetterInput, today: LocalDate) = direct.analyze(input, today)
    override fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate) =
        direct.draft(input, analysis, keywords, tone, sender, today)
}
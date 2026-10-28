package de.briefampel.app.data

import de.briefampel.app.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface LlmProvider {
    suspend fun analyze(input: LetterInput, today: LocalDate): LetterAnalysis
    fun draft(input: LetterInput, analysis: LetterAnalysis, keywords: String, tone: Tone, sender: Sender, today: LocalDate): Flow<String>
}

fun errorMessage(error: Throwable): String {
    val name = error.javaClass.simpleName
    return when {
        // Eigene Meldungen von Server, Konto und Kauf sind schon für Menschen geschrieben.
        error is ServerFehler || error is KaufFehler || error is GuthabenLeer || error is AnmeldungNoetig -> error.message ?: "Etwas ist schiefgelaufen. Bitte nochmal versuchen."
        name.contains("Serialization") || name.contains("JSON") -> "Antwort war unvollständig. Bitte nochmal prüfen."
        name.contains("Quota") || error.message?.contains("429") == true -> "Gerade zu viele Anfragen. Bitte gleich nochmal versuchen."
        name.contains("UnknownHost") || name.contains("Connect") || name.contains("Network") -> "Keine Verbindung. Bitte Internet prüfen und erneut versuchen."
        name.contains("InvalidApiKey") || name.contains("Server") -> "Der KI-Dienst ist gerade nicht erreichbar. Bitte Konfiguration und Verbindung prüfen."
        error is IllegalArgumentException || error is IllegalStateException -> error.message ?: "Etwas ist schiefgelaufen. Bitte nochmal versuchen."
        else -> "Etwas ist schiefgelaufen. Bitte nochmal versuchen."
    }
}

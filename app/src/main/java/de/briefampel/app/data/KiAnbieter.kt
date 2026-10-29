package de.briefampel.app.data

import de.briefampel.app.BuildConfig

/** KI der App. Ohne Server spricht die Test-APK direkt mit ihr; mit Server muss BRIEFAMPEL_KI dort dasselbe sagen. */
enum class KiAnbieter(val anzeige: String, val schluesselSeite: String) {
    MISTRAL("Mistral", "https://console.mistral.ai/api-keys"),
    GEMINI("Gemini", "https://aistudio.google.com/apikey");

    companion object {
        /** Aus local.properties: briefampel.ki=gemini (Standard) oder mistral. */
        val gewaehlt: KiAnbieter get() = if (BuildConfig.AI_ANBIETER == "mistral") MISTRAL else GEMINI
    }
}

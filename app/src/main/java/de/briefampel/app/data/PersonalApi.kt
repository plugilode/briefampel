package de.briefampel.app.data

/** Personal API keys are stored encrypted in Android Keystore and used with the direct Gemini REST API. */
interface PersonalApi {
    val supported: Boolean
    val configured: Boolean
    fun save(key: String)
    fun clear()
}

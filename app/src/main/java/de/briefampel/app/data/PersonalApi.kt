package de.briefampel.app.data

/** Persönlicher KI-Schlüssel der Test-APK, verschlüsselt im Android Keystore. */
interface PersonalApi {
    val anbieter: KiAnbieter get() = KiAnbieter.GEMINI
    val supported: Boolean
    val configured: Boolean
    fun save(key: String)
    fun clear()
}

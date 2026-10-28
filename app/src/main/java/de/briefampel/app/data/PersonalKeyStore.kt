package de.briefampel.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import de.briefampel.app.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The encryption key never leaves Android Keystore. The API key is never bundled or logged. */
internal class PersonalKeyStore(context: Context) : PersonalApi {
    private val prefs = context.getSharedPreferences("briefampel_personal_api", Context.MODE_PRIVATE)
    private val alias = "briefampel.gemini.personal.v1"
    override val supported = true
    override val configured get() = !read().isNullOrBlank()
    val stored get() = prefs.contains("encrypted")

    init {
        // Test-APK: einen mitgebauten Schlüssel einmalig verschlüsselt übernehmen.
        // Danach gilt nur noch der gespeicherte; „API-Schlüssel löschen“ bleibt dauerhaft wirksam.
        val mitgebaut = BuildConfig.TEST_GEMINI_KEY
        if (mitgebaut.isNotBlank() && !prefs.getBoolean("testschluessel_uebernommen", false)) {
            try {
                if (!stored) save(mitgebaut)
                prefs.edit().putBoolean("testschluessel_uebernommen", true).apply()
            } catch (_: Exception) {
                // Ohne Keystore (z. B. in Unit-Tests) bleibt die App unkonfiguriert.
            }
        }
    }

    fun read(): String? {
        val encrypted = prefs.getString("encrypted", null) ?: return null
        return try {
            val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    override fun save(key: String) {
        require(key.length in 20..512 && key.none(Char::isWhitespace)) { "Bitte einen gültigen Gemini-API-Schlüssel eingeben." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Der Schlüssel konnte nicht gespeichert werden." }
    }

    override fun clear() {
        check(prefs.edit().clear().commit()) { "Der Schlüssel konnte nicht gelöscht werden." }
    }

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
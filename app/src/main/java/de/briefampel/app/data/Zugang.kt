package de.briefampel.app.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Lokale App-Sperre: eine 6-stellige PIN, optional Entsperren per Fingerabdruck oder Gesicht. Kein Konto, kein Server. */
interface Zugang {
    /** false nur in Tests und Vorschauen, dann gibt es keine Sperre. */
    val aktiv: Boolean
    val eingerichtet: Boolean
    var biometrie: Boolean
    fun setzePin(pin: String)
    /** Prüft die PIN. Nach 5 Fehlversuchen wartet die Sperre 30 Sekunden, danach jeweils doppelt so lange. */
    fun pruefePin(pin: String): PinErgebnis
    /** Löscht PIN und Biometrie-Einstellung, z. B. bei „PIN vergessen“. */
    fun zuruecksetzen()
}

sealed interface PinErgebnis {
    data object Richtig : PinErgebnis
    data class Falsch(val versucheBisPause: Int) : PinErgebnis
    data class Pause(val sekunden: Long) : PinErgebnis
}

fun istGueltigePin(pin: String) = pin.length == 6 && pin.all(Char::isDigit)

internal class ZugangsSpeicher(context: Context, private val uhr: () -> Long = SystemClock::elapsedRealtime) : Zugang {
    private val prefs = context.getSharedPreferences("briefampel_zugang", Context.MODE_PRIVATE)
    override val aktiv = true
    override val eingerichtet get() = prefs.contains("hash")
    override var biometrie: Boolean
        get() = prefs.getBoolean("biometrie", false)
        set(value) { prefs.edit().putBoolean("biometrie", value).apply() }

    override fun setzePin(pin: String) {
        require(istGueltigePin(pin)) { "Die PIN muss aus 6 Ziffern bestehen." }
        val salz = ByteArray(16).also(SecureRandom()::nextBytes)
        check(prefs.edit()
            .putString("salz", Base64.encodeToString(salz, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(ableiten(pin, salz), Base64.NO_WRAP))
            .putInt("fehler", 0).putLong("pause_bis", 0L)
            .commit()) { "Die PIN konnte nicht gespeichert werden." }
    }

    override fun pruefePin(pin: String): PinErgebnis {
        val pauseBis = prefs.getLong("pause_bis", 0L)
        val jetzt = uhr()
        // Nach einem Neustart des Handys beginnt elapsedRealtime neu; eine alte Pause verfällt dann.
        if (pauseBis > jetzt && pauseBis - jetzt < 60 * 60 * 1000) return PinErgebnis.Pause((pauseBis - jetzt + 999) / 1000)
        val salz = Base64.decode(prefs.getString("salz", "") ?: "", Base64.NO_WRAP)
        val erwartet = Base64.decode(prefs.getString("hash", "") ?: "", Base64.NO_WRAP)
        if (erwartet.isNotEmpty() && MessageDigest.isEqual(ableiten(pin, salz), erwartet)) {
            prefs.edit().putInt("fehler", 0).putLong("pause_bis", 0L).apply()
            return PinErgebnis.Richtig
        }
        val fehler = prefs.getInt("fehler", 0) + 1
        return if (fehler % 5 == 0) {
            val sekunden = 30L shl ((fehler / 5) - 1).coerceAtMost(6)
            prefs.edit().putInt("fehler", fehler).putLong("pause_bis", jetzt + sekunden * 1000).apply()
            PinErgebnis.Pause(sekunden)
        } else {
            prefs.edit().putInt("fehler", fehler).apply()
            PinErgebnis.Falsch(5 - fehler % 5)
        }
    }

    override fun zuruecksetzen() {
        check(prefs.edit().clear().commit()) { "Die Sperre konnte nicht zurückgesetzt werden." }
    }

    private fun ableiten(pin: String, salz: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salz, 120_000, 256)).encoded
}

/** Für Tests und Vorschauen: keine Sperre. */
object OffenerZugang : Zugang {
    override val aktiv = false
    override val eingerichtet = true
    override var biometrie = false
    override fun setzePin(pin: String) = Unit
    override fun pruefePin(pin: String): PinErgebnis = PinErgebnis.Richtig
    override fun zuruecksetzen() = Unit
}

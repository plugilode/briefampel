package de.briefampel.app.data

import android.content.Context
import de.briefampel.app.domain.Sender
import de.briefampel.app.domain.Guthaben

/** Only preferences live on disk. Letter text, pages and generated replies stay in memory. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("briefampel_private", Context.MODE_PRIVATE)
    var introSeen: Boolean
        get() = prefs.getBoolean("intro_seen", false)
        set(value) { prefs.edit().putBoolean("intro_seen", value).apply() }
    var consent: Boolean
        get() = prefs.getBoolean("consent_v2", false)
        set(value) { prefs.edit().putBoolean("consent_v2", value).apply() }
    var introTon: Boolean
        get() = prefs.getBoolean("intro_ton", true)
        set(value) { prefs.edit().putBoolean("intro_ton", value).apply() }
    /** Gewählte Sprache ("de", "en", "es"); null, solange beim ersten Start noch nicht gewählt. */
    var sprache: String?
        get() = prefs.getString("sprache", null)
        set(value) { prefs.edit().putString("sprache", value).apply() }
    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }
    var sender: Sender
        get() = Sender(prefs.getString("name", "").orEmpty(), prefs.getString("address", "").orEmpty(), prefs.getString("email", "").orEmpty())
        set(value) { prefs.edit().putString("name", value.name).putString("address", value.address).putString("email", value.email).apply() }
    /** Verbleibende Analysen. Neue Installationen starten mit dem Gratis-Startguthaben. */
    var guthaben: Int
        get() = prefs.getInt("guthaben", Guthaben.START)
        set(value) { prefs.edit().putInt("guthaben", value.coerceAtLeast(0)).apply() }
}

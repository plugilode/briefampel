package de.briefampel.app.data

import android.content.Context
import de.briefampel.app.domain.Sender
import de.briefampel.app.domain.Usage

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
    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }
    var sender: Sender
        get() = Sender(prefs.getString("name", "").orEmpty(), prefs.getString("address", "").orEmpty(), prefs.getString("email", "").orEmpty())
        set(value) { prefs.edit().putString("name", value.name).putString("address", value.address).putString("email", value.email).apply() }
    var usage: Usage
        get() = Usage(prefs.getString("usage_date", "").orEmpty(), prefs.getInt("usage_count", 0))
        set(value) { prefs.edit().putString("usage_date", value.date).putInt("usage_count", value.count).apply() }
}

package de.briefampel.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import de.briefampel.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Was die App vom eigenen Server braucht: Anmeldung, Guthaben, Käufe einlösen. */
interface ServerZugang {
    /** true, wenn die App mit eigenem Firebase-Projekt und Server-Adresse gebaut wurde. */
    val verfuegbar: Boolean
    val angemeldet: Boolean
    val email: String?
    /** Neuester vom Server gemeldeter Stand, z. B. nach einer Prüfung. */
    val guthabenStrom: MutableStateFlow<Int?>
    suspend fun guthaben(): Int
    suspend fun kaufEinloesen(produktId: String, token: String): Int
    fun abmelden()
}

class GuthabenLeer : Exception("Dein Guthaben ist aufgebraucht.")
class AnmeldungNoetig : Exception("Bitte einmal mit Google anmelden.")

/** Fehler, die der Server mit einem lesbaren Text zurückgibt. */
class ServerFehler(val status: Int, text: String) : Exception(text)

/** Ein Server pro Prozess, damit Anmeldung, Provider und Guthaben denselben Stand sehen. */
object Dienste {
    @Volatile private var server: FirebaseServer? = null
    fun server(context: Context): FirebaseServer =
        server ?: synchronized(this) { server ?: FirebaseServer(context.applicationContext).also { server = it } }
}

/**
 * Firebase-Anbindung: Google-Anmeldung über Firebase Auth, App Check und die HTTP-Funktion „api“.
 * Ohne google-services.json oder Server-Adresse ist [verfuegbar] false und die App nutzt den Testweg.
 */
open class FirebaseServer internal constructor(
    context: Context,
    internal val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
    internal val basis: String = BuildConfig.SERVER_URL.trimEnd('/'),
) : ServerZugang {
    private val app: FirebaseApp? = try {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    } catch (_: Exception) { null }

    override val verfuegbar = app != null && basis.isNotBlank()
    private val auth get() = FirebaseAuth.getInstance(app!!)
    override val angemeldet get() = verfuegbar && auth.currentUser != null
    override val email get() = if (verfuegbar) auth.currentUser?.email else null
    override val guthabenStrom = MutableStateFlow<Int?>(null)

    /** Meldet bei Firebase mit dem ID-Token aus „Mit Google anmelden“ an. */
    suspend fun mitGoogleAnmelden(googleIdToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
    }

    override fun abmelden() { if (verfuegbar) auth.signOut(); guthabenStrom.value = null }

    internal open suspend fun koepfe(): Map<String, String> {
        val nutzer = auth.currentUser ?: throw AnmeldungNoetig()
        val id = nutzer.getIdToken(false).await().token ?: throw AnmeldungNoetig()
        val appCheck = FirebaseAppCheck.getInstance(app!!).getAppCheckToken(false).await().token
        return mapOf("Authorization" to "Bearer $id", "X-Firebase-AppCheck" to appCheck)
    }

    internal fun anfrage(weg: String, koepfe: Map<String, String>, body: JSONObject? = null): Request =
        Request.Builder().url(basis + weg).apply {
            koepfe.forEach { (k, v) -> header(k, v) }
            if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
        }.build()

    /** Wandelt Fehlerantworten des Servers in verständliche Ausnahmen um. */
    internal fun pruefeAntwort(status: Int, text: String?) {
        if (status in 200..299) return
        val meldung = runCatching { JSONObject(text ?: "").optString("text") }.getOrNull().orEmpty()
        when (status) {
            401 -> throw AnmeldungNoetig()
            402 -> { guthabenStrom.value = 0; if (meldung.contains("Kauf")) throw ServerFehler(402, meldung) else throw GuthabenLeer() }
            else -> throw ServerFehler(status, meldung.ifBlank { "Etwas ist schiefgelaufen. Bitte nochmal versuchen." })
        }
    }

    override suspend fun guthaben(): Int = withContext(Dispatchers.IO) {
        client.newCall(anfrage("/guthaben", koepfe())).execute().use { r ->
            val text = r.body?.string()
            pruefeAntwort(r.code, text)
            JSONObject(text!!).getInt("guthaben").also { guthabenStrom.value = it }
        }
    }

    override suspend fun kaufEinloesen(produktId: String, token: String): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().put("produktId", produktId).put("token", token)
        client.newCall(anfrage("/kauf", koepfe(), body)).execute().use { r ->
            val text = r.body?.string()
            pruefeAntwort(r.code, text)
            JSONObject(text!!).getInt("guthaben").also { guthabenStrom.value = it }
        }
    }
}

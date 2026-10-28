package de.briefampel.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import de.briefampel.app.domain.AnalysisJson
import de.briefampel.app.domain.LetterAnalysis
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ein geprüfter Brief im Verlauf: das Ergebnis und die dazu geschriebenen Antworten, ohne die Originalseiten. */
data class VerlaufEintrag(
    val id: String,
    val geprueftAm: Long,
    val analyse: LetterAnalysis,
    val seiten: Int,
    val antworten: List<GespeicherteAntwort> = emptyList(),
    /** Wie viele Analysen für diesen Brief schon bezahlt sind; jede deckt bis zu 3 Antworten. */
    val bezahlteAnalysen: Int = 1,
)

data class GespeicherteAntwort(val text: String, val ton: String, val am: Long)

/** Lokaler Verlauf. Bleibt auf dem Gerät, bis der Nutzer Einträge löscht. */
interface Verlauf {
    fun lade(): List<VerlaufEintrag>
    fun speichere(eintraege: List<VerlaufEintrag>)
}

internal object VerlaufJson {
    fun encode(eintraege: List<VerlaufEintrag>): String = JSONArray().apply {
        eintraege.forEach { e ->
            put(JSONObject()
                .put("id", e.id).put("am", e.geprueftAm).put("seiten", e.seiten).put("bezahlt", e.bezahlteAnalysen)
                .put("analyse", JSONObject(AnalysisJson.encode(e.analyse)))
                .put("antworten", JSONArray().apply { e.antworten.forEach { a -> put(JSONObject().put("text", a.text).put("ton", a.ton).put("am", a.am)) } }))
        }
    }.toString()

    fun decode(raw: String): List<VerlaufEintrag> {
        val liste = JSONArray(raw)
        return (0 until liste.length()).mapNotNull { i ->
            val o = liste.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                val antworten = o.optJSONArray("antworten") ?: JSONArray()
                VerlaufEintrag(
                    id = o.getString("id"),
                    geprueftAm = o.getLong("am"),
                    analyse = AnalysisJson.parse(o.getJSONObject("analyse").toString()),
                    seiten = o.optInt("seiten", 1),
                    bezahlteAnalysen = o.optInt("bezahlt", 1),
                    antworten = (0 until antworten.length()).mapNotNull { j ->
                        antworten.optJSONObject(j)?.let { GespeicherteAntwort(it.optString("text"), it.optString("ton"), it.optLong("am")) }
                    },
                )
            }.getOrNull()
        }
    }
}

/** Speichert den Verlauf AES-256-GCM-verschlüsselt im privaten App-Speicher; der Schlüssel liegt im Android Keystore. */
internal class VerlaufSpeicher(context: Context) : Verlauf {
    private val datei = File(context.filesDir, "verlauf.bin")
    private val alias = "briefampel.verlauf.v1"

    override fun lade(): List<VerlaufEintrag> {
        if (!datei.exists()) return emptyList()
        val bytes = datei.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, bytes, 0, 12))
        return VerlaufJson.decode(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
    }

    override fun speichere(eintraege: List<VerlaufEintrag>) {
        if (eintraege.isEmpty()) { datei.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, schluessel())
        val inhalt = cipher.iv + cipher.doFinal(VerlaufJson.encode(eintraege).toByteArray(Charsets.UTF_8))
        val neu = File(datei.parentFile, "verlauf.bin.neu")
        neu.writeBytes(inhalt)
        check(neu.renameTo(datei) || run { datei.delete(); neu.renameTo(datei) }) { "Der Verlauf konnte nicht gespeichert werden." }
    }

    private fun schluessel(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}

/** Für Tests und Vorschauen: nur im Arbeitsspeicher. */
class FluechtigerVerlauf(start: List<VerlaufEintrag> = emptyList()) : Verlauf {
    private var eintraege = start
    override fun lade() = eintraege
    override fun speichere(eintraege: List<VerlaufEintrag>) { this.eintraege = eintraege }
}

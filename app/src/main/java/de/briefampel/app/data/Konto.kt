package de.briefampel.app.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.android.billingclient.api.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import de.briefampel.app.domain.Guthaben
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Konto beim eigenen Server: Anmeldung, Guthaben und Käufe. Ohne Server gibt es kein Konto (null). */
interface Konto {
    val angemeldet: Boolean
    val email: String?
    /** Letzter bekannter Stand vom Server; null, solange noch nichts geladen ist. */
    val guthaben: StateFlow<Int?>
    /** @return false, wenn die Anmeldung abgebrochen wurde. */
    suspend fun anmelden(activity: Activity): Boolean
    suspend fun guthabenLaden(): Int
    /** Echte Preise aus Google Play, je Produkt-ID. */
    suspend fun preise(): Map<String, String>
    /** @return neues Guthaben, oder null, wenn der Kauf abgebrochen wurde oder noch aussteht. */
    suspend fun kaufen(activity: Activity, produktId: String): Int?
    /** Bezahlte, aber noch nicht gutgeschriebene Käufe (z. B. App vorher geschlossen) einlösen. */
    suspend fun offeneKaeufeEinloesen()
    fun abmelden()
}

class KaufFehler(text: String) : Exception(text)

fun erstelleKonto(context: Context): Konto? =
    Dienste.server(context).takeIf { it.verfuegbar }?.let { PlayKonto(context.applicationContext, it) }

/** Google-Anmeldung per Credential Manager, Kauf über Google Play, Gutschrift über den Server. */
internal class PlayKonto(private val context: Context, private val server: FirebaseServer) : Konto {
    override val angemeldet get() = server.angemeldet
    override val email get() = server.email
    override val guthaben: StateFlow<Int?> = server.guthabenStrom.asStateFlow()

    override suspend fun anmelden(activity: Activity): Boolean {
        // Die Web-Client-ID legt das google-services-Plugin aus der google-services.json an.
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) throw KaufFehler("Die Google-Anmeldung ist in dieser Version nicht eingerichtet.")
        val anfrage = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(context.getString(id)).build())
            .build()
        val antwort = try { CredentialManager.create(activity).getCredential(activity, anfrage) }
            catch (_: GetCredentialCancellationException) { return false }
        val nachweis = antwort.credential
        if (nachweis !is CustomCredential || nachweis.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            throw KaufFehler("Die Anmeldung hat nicht geklappt. Bitte nochmal versuchen.")
        server.mitGoogleAnmelden(GoogleIdTokenCredential.createFrom(nachweis.data).idToken)
        return true
    }

    override suspend fun guthabenLaden() = server.guthaben()
    override fun abmelden() = server.abmelden()

    /** Antwort des Play-Kaufdialogs; der Listener lebt so lange wie der Client. */
    @Volatile private var kaufErgebnis: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener { ergebnis, kaeufe -> kaufErgebnis?.complete(ergebnis to kaeufe) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }
    private var details: Map<String, ProductDetails> = emptyMap()

    private suspend fun verbinden() {
        if (client.isReady) return
        val fertig = CompletableDeferred<BillingResult>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { fertig.complete(result) }
            override fun onBillingServiceDisconnected() {}
        })
        val ergebnis = fertig.await()
        if (ergebnis.responseCode != BillingClient.BillingResponseCode.OK) throw KaufFehler("Google Play ist gerade nicht erreichbar. Bitte später nochmal versuchen.")
    }

    private suspend fun ladeDetails(): Map<String, ProductDetails> {
        if (details.isNotEmpty()) return details
        verbinden()
        val produkte = Guthaben.pakete.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(BillingClient.ProductType.INAPP).build()
        }
        val ergebnis = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(produkte).build())
        details = ergebnis.productDetailsList.orEmpty().associateBy { it.productId }
        return details
    }

    override suspend fun preise(): Map<String, String> =
        ladeDetails().mapNotNull { (id, d) -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { id to it } }.toMap()

    override suspend fun kaufen(activity: Activity, produktId: String): Int? {
        val produkt = ladeDetails()[produktId] ?: throw KaufFehler("Dieses Paket ist gerade nicht verfügbar.")
        val warten = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>().also { kaufErgebnis = it }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(produkt).build()))
            .build()
        val start = client.launchBillingFlow(activity, params)
        if (start.responseCode != BillingClient.BillingResponseCode.OK) {
            kaufErgebnis = null
            if (start.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) { offeneKaeufeEinloesen(); return guthaben.value }
            throw KaufFehler("Der Kauf konnte nicht gestartet werden. Bitte später nochmal versuchen.")
        }
        val (ergebnis, kaeufe) = warten.await()
        kaufErgebnis = null
        return when (ergebnis.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                var neu: Int? = null
                kaeufe.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { neu = einloesen(it) }
                neu
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> null
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { offeneKaeufeEinloesen(); guthaben.value }
            else -> throw KaufFehler("Der Kauf hat nicht geklappt. Es wurde nichts abgebucht.")
        }
    }

    override suspend fun offeneKaeufeEinloesen() {
        verbinden()
        val ergebnis = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        ergebnis.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { einloesen(it) }
    }

    /** Der Server prüft den Kauf bei Google, schreibt gut und verbraucht ihn, damit er erneut kaufbar ist. */
    private suspend fun einloesen(kauf: Purchase): Int? =
        kauf.products.firstOrNull()?.let { server.kaufEinloesen(it, kauf.purchaseToken) }
}

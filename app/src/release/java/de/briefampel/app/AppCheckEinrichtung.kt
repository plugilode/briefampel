package de.briefampel.app

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Store-Version: Nur echte, unveränderte Installationen aus Google Play dürfen den Server nutzen. */
internal fun appCheckEinrichten(app: FirebaseApp) {
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}

package de.briefampel.app

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Testversion: App Check mit Debug-Token (steht beim ersten Start im Logcat, in der Firebase-Konsole freischalten). */
internal fun appCheckEinrichten(app: FirebaseApp) {
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}

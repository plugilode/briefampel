package de.briefampel.app

import android.app.Application
import com.google.firebase.FirebaseApp
import de.briefampel.app.data.Dienste

class BriefampelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // App Check vor der ersten Server-Anfrage einrichten; ohne eigenes Firebase-Projekt passiert nichts.
        if (Dienste.server(this).verfuegbar) appCheckEinrichten(FirebaseApp.getInstance())
    }
}

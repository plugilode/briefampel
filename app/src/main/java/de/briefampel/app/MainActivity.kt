package de.briefampel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.briefampel.app.ui.BriefampelApp
import de.briefampel.app.ui.BriefampelViewModel

/** FragmentActivity, weil die Biometrie-Abfrage ein Fragment braucht. */
class MainActivity : FragmentActivity() {
    private val model: BriefampelViewModel by viewModels()
    private var imHintergrundSeit = 0L

    override fun onStop() {
        super.onStop()
        imHintergrundSeit = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Nach einer Minute im Hintergrund wird wieder gesperrt.
        if (imHintergrundSeit > 0 && SystemClock.elapsedRealtime() - imHintergrundSeit > 60_000) model.sperren()
        imHintergrundSeit = 0
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BriefampelApp(model) }
        if (savedInstanceState == null) handleShare(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }
    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        if (intent?.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        val uris = when (intent?.action) {
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> listOfNotNull(intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (uris.isNotEmpty() || !text.isNullOrBlank()) model.import(uris, text)
    }
}

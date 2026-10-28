package de.briefampel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.briefampel.app.ui.BriefampelApp
import de.briefampel.app.ui.BriefampelViewModel

class MainActivity : ComponentActivity() {
    private val model: BriefampelViewModel by viewModels()
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

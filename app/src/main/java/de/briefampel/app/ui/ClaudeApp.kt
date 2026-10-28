package de.briefampel.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.google.mlkit.vision.documentscanner.*
import de.briefampel.app.domain.Tone
import de.briefampel.app.ui.theme.BriefampelTheme
import de.briefampel.app.ui.theme.LocalFarben
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val ORIGIN = "https://appassets.androidplatform.net"
private const val ENTRY = "$ORIGIN/assets/briefampel/index.html"

/** Native Android host for the original local Claude presentation and existing ViewModel. */
@Composable
fun BriefampelApp(model: BriefampelViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.import(it) }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }?.let { model.import(it) }
    }
    val scan = {
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(context as Activity)
            .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { model.error("Scannen ist hier nicht verfügbar. Bitte ein Foto oder PDF über Datei auswählen.") }
        Unit
    }
    val share: (String) -> Unit = { body ->
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }, "Mit anderer App teilen"))
    }
    BackHandler(s.showIntro || s.sheet != null || s.screen != Screen.INPUT || s.loading || s.writing) { model.back() }
    BriefampelTheme(dunkel = s.dark) {
        val c = LocalFarben.current
        Box(Modifier.fillMaxSize().background(c.grund).imePadding()) {
            ClaudePresentation(s, model, scan = scan, file = { files.launch(arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")) },
                copy = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Antwortbrief", model.state.value.reply))
                    scope.launch { snackbar.showSnackbar("Antwort kopiert") }
                }, share = { share(model.state.value.reply) },
                shareAnalysis = { model.state.value.result?.analyse?.let { a -> share("${a.titel}\n\n${a.kurz}\n\n${a.empfehlung}") } })
            if (s.sheet != Sheet.CONSENT) AppSheets(s, model)
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ClaudePresentation(s: UiState, model: BriefampelViewModel, scan: () -> Unit, file: () -> Unit, copy: () -> Unit, share: () -> Unit, shareAnalysis: () -> Unit) {
    val renderer = remember { RendererState() }
    val currentState by rememberUpdatedState(s)
    val action: (String, String?) -> Unit = { name, value ->
        when (name) {
            "ready" -> { renderer.ready = true; renderer.view?.let { push(it, currentState) } }
            "introDone" -> model.finishIntro()
            "settings", "sender" -> model.sheet(Sheet.SETTINGS)
            "privacy" -> model.sheet(Sheet.PRIVACY)
            "paste" -> model.sheet(Sheet.PASTE)
            "original" -> model.sheet(Sheet.ORIGINAL)
            "back" -> model.back()
            "scan" -> scan()
            "file" -> file()
            "analyze" -> model.analyze()
            "discard" -> model.newLetter()
            "reply" -> model.openReply()
            "consent" -> if (model.state.value.sheet == Sheet.CONSENT) model.consent()
            "keywords" -> model.keywords(value.orEmpty())
            "keyword" -> model.toggleKeyword(value.orEmpty())
            "tone" -> Tone.entries.find { it.label == value }?.let(model::tone)
            "write" -> model.write()
            "stop" -> model.stop()
            "copy" -> copy()
            "share" -> share()
            "shareAnalysis" -> shareAnalysis()
            "remove" -> model.remove(value.orEmpty())
            "dismissError" -> model.clearError()
        }
    }
    val dispatch by rememberUpdatedState(action)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            WebView(context).apply {
                renderer.view = this
                setBackgroundColor(android.graphics.Color.rgb(18, 20, 23))
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    textZoom = 100
                }
                addJavascriptInterface(ClaudeBridge { name, value -> dispatch(name, value) }, "BriefampelNative")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                        return loader.shouldInterceptRequest(request.url) ?: WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean = request.url.toString() != ENTRY
                    override fun onPageFinished(view: WebView, url: String?) { if (renderer.ready) push(view, currentState) }
                }
                loadUrl(ENTRY)
            }
        },
        update = { view -> if (renderer.ready) push(view, s) },
        onRelease = { view -> renderer.ready = false; renderer.view = null; view.removeJavascriptInterface("BriefampelNative"); view.stopLoading(); view.destroy() },
    )
}

private class RendererState(var ready: Boolean = false, var view: WebView? = null)
private fun push(view: WebView, state: UiState) { view.evaluateJavascript("window.Briefampel && window.Briefampel.update(${ClaudeUiJson.encode(state)})", null) }

/** Single narrow command channel, available only to bundled local assets. */
class ClaudeBridge(private val action: (String, String?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun dispatch(raw: String) {
        if (raw.length > 70_000) return
        val command = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val name = command.optString("action")
        val value = (command.opt("value") as? String)
        if (name !in ACTIONS) return
        main.post { action(name, value) }
    }
    companion object {
        private val ACTIONS = setOf("ready", "introDone", "settings", "sender", "privacy", "paste", "original", "back", "scan", "file", "analyze", "discard", "reply", "consent", "keywords", "keyword", "tone", "write", "stop", "copy", "share", "shareAnalysis", "remove", "dismissError")
    }
}

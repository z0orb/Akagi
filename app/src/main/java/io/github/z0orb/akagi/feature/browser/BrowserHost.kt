package io.github.z0orb.akagi.feature.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.z0orb.akagi.playback.registerNoisyReceiver

/**
 * Hosts the app's one long-lived YouTube WebView (PLAN §7.1). [startUrl] is only read when the WebView is
 * created; later navigation goes through [BrowserController].
 */
@Composable
fun BrowserHost(
    controller: BrowserController,
    startUrl: String,
    onPageChange: (url: String, canGoBack: Boolean) -> Unit,
    onWebAudioStart: () -> Unit,
    onWebAudioUnplug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val currentOnWebAudioStart by rememberUpdatedState(onWebAudioStart)
    // A dead WebView can't be reused: a new generation builds a fresh one and the old one is destroyed.
    key(controller.webViewGeneration) {
        val webView = remember {
            createWebView(context, backgroundColor, controller) { url, canGoBack ->
                controller.lastUrl = url
                currentOnPageChange(url, canGoBack)
            }.apply {
                WebMediaBridge.install(this) { audible ->
                    if (audible && !controller.webMediaAudible) currentOnWebAudioStart()
                    controller.webMediaAudible = audible
                }
                YouTubeMusicPage.install(this)
                loadUrl(controller.lastUrl ?: startUrl)
            }
        }
        WebViewLifecycle(webView, controller, onWebAudioUnplug)
        AndroidView(factory = { webView }, modifier = modifier)
    }
}

@Composable
private fun WebViewLifecycle(webView: WebView, controller: BrowserController, onWebAudioUnplug: () -> Unit) {
    val context = LocalContext.current
    val currentOnUnplugged by rememberUpdatedState(onWebAudioUnplug)
    DisposableEffect(webView) {
        controller.webView = webView
        onDispose {
            controller.webView = null
            webView.destroy()
        }
    }
    // Stop page timers and media work while Akagi is in the background (PLAN §7.17). Only Akagi plays
    // in the background (D-019), so the page's media is paused too: onPause() alone doesn't stop it.
    LifecycleStartEffect(webView) {
        webView.onResume()
        webView.resumeTimers()
        val unplugged = registerNoisyReceiver(context) {
            controller.pauseMedia()
            if (controller.webMediaAudible) currentOnUnplugged()
        }
        onStopOrDispose {
            context.unregisterReceiver(unplugged)
            controller.pauseMedia()
            webView.onPause()
            webView.pauseTimers()
        }
    }
}

// YouTube does not work without JavaScript; the bridge added later is origin-scoped.
@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    backgroundColor: Int,
    controller: BrowserController,
    onPageChange: (String, Boolean) -> Unit,
): WebView {
    val webView = WebView(context)
    // AndroidView would give it WRAP_CONTENT, which makes the WebView lay pages out at zero height: CSS `vh`
    // units become 0 and YouTube Music's player page (sized in vh) collapses to a black screen.
    webView.layoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    // Avoids a white flash before YouTube paints.
    webView.setBackgroundColor(backgroundColor)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        // Pages must not start audio on their own (PLAN §7.10).
        mediaPlaybackRequiresUserGesture = true
    }
    WebViewIdentity.hide(webView.settings)
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
    webView.webViewClient = AkagiWebViewClient(onPageChange, controller)
    webView.webChromeClient = FullscreenChromeClient(controller)
    return webView
}

private class AkagiWebViewClient(
    private val onPageChange: (String, Boolean) -> Unit,
    private val controller: BrowserController,
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        controller.onPageStarted()
    }

    // Akagi shows its own message over the WebView's error page, which is unreadable on the dark background.
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) controller.onMainFrameError(PageError.fromWebViewError(error.errorCode))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return when (NavigationPolicy.decide(request.url.toString())) {
            NavigationDecision.LOAD -> false

            NavigationDecision.BLOCK -> true

            NavigationDecision.OPEN_EXTERNALLY -> {
                openExternally(view.context, request.url)
                true
            }
        }
    }

    // YouTube is a single-page app: this fires on in-page navigation where onPageStarted does not.
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        url?.let { onPageChange(it, view.canGoBack()) }
    }

    // Without this the WebView aborts the whole app when its renderer crashes or is killed for memory,
    // which Android does routinely in the background. Returning true lets Akagi rebuild the WebView.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        controller.onRendererGone()
        return true
    }

    private fun openExternally(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Nothing can open it; staying on the current page is the least surprising outcome.
        }
    }
}

private class FullscreenChromeClient(private val controller: BrowserController) : WebChromeClient() {

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        controller.enterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        controller.clearFullscreen()
    }

    // Every navigation is the user's own (mark menu, link bar), so a site's "changes may not be saved" prompt
    // (YouTube Music's player page raises one) would only get in the way.
    override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
        result.confirm()
        return true
    }

    // The default poster is a grey play glyph that flashes before every video.
    override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)
}

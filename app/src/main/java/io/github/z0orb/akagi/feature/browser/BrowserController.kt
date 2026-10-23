package io.github.z0orb.akagi.feature.browser

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** UI-side handle on the single WebView: commands go in, fullscreen state comes out. */
@Stable
class BrowserController {
    /** The page's fullscreen video view while a video is fullscreen. */
    var fullscreenView: View? by mutableStateOf(null)
        private set

    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    internal var webView: WebView? = null

    /** Bumped when the renderer process dies, so [BrowserHost] replaces the dead WebView. */
    internal var webViewGeneration by mutableIntStateOf(0)
        private set

    /** Where a replacement WebView should resume. */
    internal var lastUrl: String? = null

    /** Whether the page is playing audible media, as last reported by [WebMediaBridge]. */
    internal var webMediaAudible = false

    /** Why the current page failed to load, shown by Akagi instead of the WebView's own error page. */
    var pageError: PageError? by mutableStateOf(null)
        private set

    internal fun onPageStarted() {
        pageError = null
    }

    internal fun onMainFrameError(error: PageError) {
        pageError = error
    }

    /** Loads the failed page again. */
    fun retry() {
        pageError = null
        webView?.reload()
    }

    internal fun onRendererGone() {
        clearFullscreen()
        webMediaAudible = false
        webViewGeneration++
    }

    fun load(url: String) {
        webView?.loadUrl(url)
    }

    fun goBack() {
        webView?.goBack()
    }

    /** Pauses every video and audio element on the page; nothing in Akagi ever resumes them (R11). */
    fun pauseMedia() {
        webView?.let(WebMediaBridge::pauseMedia)
    }

    fun exitFullscreen() {
        val callback = fullscreenCallback
        clearFullscreen()
        callback?.onCustomViewHidden()
    }

    internal fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            // Already fullscreen: the platform docs say to refuse the new view immediately.
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
    }

    internal fun clearFullscreen() {
        fullscreenView = null
        fullscreenCallback = null
    }
}

@Composable
fun rememberBrowserController(): BrowserController = remember { BrowserController() }

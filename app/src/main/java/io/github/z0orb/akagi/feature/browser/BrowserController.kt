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

    internal fun onRendererGone() {
        clearFullscreen()
        webViewGeneration++
    }

    fun load(url: String) {
        webView?.loadUrl(url)
    }

    fun goBack() {
        webView?.goBack()
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

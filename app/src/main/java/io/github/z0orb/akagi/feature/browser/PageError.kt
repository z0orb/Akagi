package io.github.z0orb.akagi.feature.browser

import android.webkit.WebViewClient

/** A page that failed to load (P7.3): the WebView's own error page is dark text on Akagi's dark background. */
enum class PageError {
    Offline,
    Failed,
    ;

    companion object {
        fun fromWebViewError(code: Int): PageError = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> Offline
            else -> Failed
        }
    }
}

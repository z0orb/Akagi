package io.github.z0orb.akagi.feature.browser

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Akagi and YouTube pause each other (R11, PLAN §7.10). A script injected before YouTube's own reports
 * when a page starts audible playback; Akagi never resumes the page's media, it only pauses it.
 */
internal object WebMediaBridge {

    /** The page reports through `akagi.postMessage`, which only these origins get (no wildcard). */
    private val allowedOrigins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://music.youtube.com")

    // Capturing listeners on the document see every media element, including ones added later.
    // Muted playback is ignored: the Home feed's silent previews must not stop Akagi.
    private val reportScript = """
        (() => {
          if (window.__akagiBridge) return;
          window.__akagiBridge = true;
          const audible = (m) => !m.paused && !m.muted && m.volume > 0;
          // Reports the whole page, so a preview pausing doesn't hide the main video still playing.
          const report = (e) => {
            if (!(e.target instanceof HTMLMediaElement)) return;
            const any = [...document.querySelectorAll('video,audio')].some(audible);
            try { akagi.postMessage(any ? 'audible' : 'silent'); } catch (_) {}
          };
          ['play', 'playing', 'pause', 'ended', 'volumechange'].forEach(
            (type) => document.addEventListener(type, report, true));
        })();
    """.trimIndent()

    private const val PAUSE_SCRIPT = "document.querySelectorAll('video,audio').forEach((m) => m.pause());"

    /**
     * Installs the report script. Returns false on a WebView too old for origin-scoped messaging
     * (older than 2020); Akagi then simply doesn't hear about YouTube's playback.
     */
    fun install(webView: WebView, onAudibleChange: (Boolean) -> Unit): Boolean {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (!supported) return false
        WebViewCompat.addWebMessageListener(webView, "akagi", allowedOrigins) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) onAudibleChange(message.data == "audible")
        }
        WebViewCompat.addDocumentStartJavaScript(webView, reportScript, allowedOrigins)
        return true
    }

    fun pauseMedia(webView: WebView) {
        webView.evaluateJavascript(PAUSE_SCRIPT, null)
    }
}

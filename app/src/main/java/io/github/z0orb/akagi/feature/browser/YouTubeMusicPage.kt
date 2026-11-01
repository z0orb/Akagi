package io.github.z0orb.akagi.feature.browser

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * YouTube Music's mobile site opens its player page by making the whole document fullscreen. In Akagi that
 * would hide the top bar and the download FAB, which the player page is the place for (PLAN §7.20). The
 * request is answered as if it succeeded, so the player page opens inside the browser area like any page.
 * Only a request for the whole document is skipped; other elements still go fullscreen.
 */
internal object YouTubeMusicPage {

    private val origins = setOf("https://music.youtube.com")

    private val script = """
        (() => {
          const request = Element.prototype.requestFullscreen;
          Element.prototype.requestFullscreen = function (...args) {
            if (this === document.documentElement) return Promise.resolve();
            return request.apply(this, args);
          };
          // Its own fullscreen button asks for the document too, so the swallowed request leaves the page in
          // its fullscreen layout (black, Back is the only way out). Fullscreen has no use for music here.
          const hide = new CSSStyleSheet();
          hide.replaceSync('ytmusic-player .fullscreen-button { display: none !important; }');
          document.adoptedStyleSheets = [...document.adoptedStyleSheets, hide];
        })();
    """.trimIndent()

    fun install(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
        }
    }
}

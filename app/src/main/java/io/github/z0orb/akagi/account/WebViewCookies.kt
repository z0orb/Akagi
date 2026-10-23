package io.github.z0orb.akagi.account

import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** [BrowserCookies] backed by the WebView's [CookieManager], which YouTube's own sign-in fills (PLAN §7.15). */
class WebViewCookies @Inject constructor() : BrowserCookies {
    // CookieManager loads the WebView provider on first use, which must happen on the main thread.
    override suspend fun youtubeCookies(): List<String> = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        // getCookieInfo keeps each cookie's domain, path and expiry; getCookie only has name=value pairs.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            CookieManagerCompat.getCookieInfo(manager, YOUTUBE_URL)
        } else {
            CookieExporter.entriesOfHeader(manager.getCookie(YOUTUBE_URL))
        }
    }

    override suspend fun add(cookies: List<ImportedCookie>) = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(it.url, it.setCookie) }
        manager.flush()
    }

    override suspend fun clear() = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation -> manager.removeAllCookies { continuation.resume(Unit) } }
        manager.flush()
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com"
    }
}

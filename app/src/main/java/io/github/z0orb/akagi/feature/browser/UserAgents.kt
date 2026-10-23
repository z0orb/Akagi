package io.github.z0orb.akagi.feature.browser

object UserAgents {
    private val webViewVersionToken = Regex("""Version/\d+(?:\.\d+)* """)

    /**
     * The WebView's default UA minus the WebView markers ("; wv" and "Version/4.0"), so YouTube serves
     * the same mobile site as Chrome instead of nudging toward the app (PLAN §7.1).
     */
    fun chromeLike(webViewUserAgent: String): String =
        webViewUserAgent.replace("; wv", "").replace(webViewVersionToken, "")

    /**
     * WebView names itself "Android WebView" in the client hints (Sec-CH-UA, navigator.userAgentData) as well;
     * Google sign-in reads them to refuse embedded browsers (PLAN §7.15).
     */
    fun chromeBrand(brand: String): String = if (brand == WEBVIEW_BRAND) CHROME_BRAND else brand

    private const val WEBVIEW_BRAND = "Android WebView"
    private const val CHROME_BRAND = "Google Chrome"
}

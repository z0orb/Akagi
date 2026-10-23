package io.github.z0orb.akagi.feature.browser

object UserAgents {
    private val webViewVersionToken = Regex("""Version/\d+(?:\.\d+)* """)

    /**
     * The WebView's default UA minus the WebView markers ("; wv" and "Version/4.0"), so YouTube serves
     * the same mobile site as Chrome instead of nudging toward the app (PLAN §7.1).
     */
    fun chromeLike(webViewUserAgent: String): String =
        webViewUserAgent.replace("; wv", "").replace(webViewVersionToken, "")
}

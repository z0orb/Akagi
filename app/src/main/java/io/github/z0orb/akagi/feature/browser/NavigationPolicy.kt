package io.github.z0orb.akagi.feature.browser

/** Where a main-frame navigation goes (PLAN §7.1). */
enum class NavigationDecision { LOAD, BLOCK, OPEN_EXTERNALLY }

object NavigationPolicy {
    // intent:// and vnd.youtube: are how YouTube's "Open app" hands the video to the YouTube app.
    private val blockedSchemes = setOf("intent", "vnd.youtube", "vnd.youtube.launch")

    // Schemes the WebView handles itself.
    private val internalSchemes = setOf("about", "data", "blob", "javascript")

    private val schemePattern = Regex("""^([a-z][a-z0-9+.\-]*):""", RegexOption.IGNORE_CASE)
    private val authorityPattern = Regex("""^[a-z][a-z0-9+.\-]*://([^/?#]*)""", RegexOption.IGNORE_CASE)

    // Sign-in and consent hop through google.com and country domains such as google.co.id.
    private val googleHost = Regex("""^(?:[a-z0-9-]+\.)*google\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")

    // Parsed by hand rather than with java.net.URI, which rejects characters YouTube URLs really contain.
    fun decide(url: String): NavigationDecision {
        val scheme = schemePattern.find(url)?.groupValues?.get(1)?.lowercase() ?: return NavigationDecision.BLOCK
        return when {
            scheme in blockedSchemes -> NavigationDecision.BLOCK
            scheme in internalSchemes -> NavigationDecision.LOAD
            scheme != "http" && scheme != "https" -> NavigationDecision.OPEN_EXTERNALLY
            isInAppHost(hostOf(url)) -> NavigationDecision.LOAD
            else -> NavigationDecision.OPEN_EXTERNALLY
        }
    }

    // "https://youtube.com@evil.com/" really goes to evil.com, so drop any user info first.
    private fun hostOf(url: String): String = authorityPattern.find(url)?.groupValues?.get(1)
        ?.substringAfterLast('@')
        ?.substringBefore(':')
        ?.lowercase()
        .orEmpty()

    private fun isInAppHost(host: String): Boolean = host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host == "youtu.be" ||
        googleHost.matches(host)
}

package io.github.z0orb.akagi.account

/** The WebView's cookie store behind an interface (PLAN §5), so the cookie policy is testable. */
interface BrowserCookies {
    /** youtube.com cookies as Set-Cookie style entries ("name=value; domain=…; expires=…") or bare pairs. */
    suspend fun youtubeCookies(): List<String>

    suspend fun add(cookies: List<ImportedCookie>)

    /** Removes every cookie, which signs the browser out of YouTube and Google. */
    suspend fun clear()
}

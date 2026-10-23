package io.github.z0orb.akagi.account

/** Tells a signed-in browser from a visitor by its youtube.com cookies. */
object YouTubeSession {
    // Google sets these only after sign-in; visitors get VISITOR_INFO1_LIVE, YSC and PREF instead.
    private val sessionCookies = setOf("SID", "__Secure-1PSID", "__Secure-3PSID", "LOGIN_INFO")

    /** [cookies] are Set-Cookie style entries or bare "name=value" pairs. */
    fun isSignedIn(cookies: List<String>): Boolean = cookies.any { entry ->
        val pair = entry.substringBefore(';')
        pair.substringBefore('=').trim() in sessionCookies && pair.substringAfter('=', "").isNotBlank()
    }
}

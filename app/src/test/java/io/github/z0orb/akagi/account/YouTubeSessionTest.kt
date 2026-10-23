package io.github.z0orb.akagi.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSessionTest {

    @Test
    fun `session cookies mean signed in`() {
        assertTrue(YouTubeSession.isSignedIn(listOf("PREF=x", "SID=abc")))
        assertTrue(YouTubeSession.isSignedIn(listOf("__Secure-3PSID=abc; domain=.youtube.com; secure")))
        assertTrue(YouTubeSession.isSignedIn(listOf("LOGIN_INFO=AFmmF2sw; domain=.youtube.com")))
    }

    @Test
    fun `visitor cookies, empty values and look-alike names don't`() {
        assertFalse(YouTubeSession.isSignedIn(emptyList()))
        assertFalse(YouTubeSession.isSignedIn(listOf("VISITOR_INFO1_LIVE=v", "YSC=y", "PREF=p")))
        assertFalse(YouTubeSession.isSignedIn(listOf("SID=; domain=.youtube.com")))
        assertFalse(YouTubeSession.isSignedIn(listOf("SIDCC=x", "__Secure-1PSIDTS=y")))
    }
}

package io.github.z0orb.akagi.feature.browser

import io.github.z0orb.akagi.feature.browser.NavigationDecision.BLOCK
import io.github.z0orb.akagi.feature.browser.NavigationDecision.LOAD
import io.github.z0orb.akagi.feature.browser.NavigationDecision.OPEN_EXTERNALLY
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {

    private fun assertDecision(expected: NavigationDecision, url: String) =
        assertEquals(url, expected, NavigationPolicy.decide(url))

    @Test
    fun `YouTube and Google sign-in stay in the WebView`() {
        listOf(
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com",
            "https://consent.youtube.com/m?continue=x",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://accounts.google.com/ServiceLogin?service=youtube",
            "https://consent.google.co.id/",
            "https://www.google.com.br/",
            "https://m.youtube.com/watch?v=x&pp=a|b",
        ).forEach { assertDecision(LOAD, it) }
    }

    @Test
    fun `app hand-offs are blocked`() {
        assertDecision(
            BLOCK,
            "intent://www.youtube.com/watch?v=x#Intent;package=com.google.android.youtube;scheme=https;end",
        )
        assertDecision(BLOCK, "vnd.youtube:dQw4w9WgXcQ")
        assertDecision(BLOCK, "not a url")
    }

    @Test
    fun `other sites and schemes leave the app`() {
        assertDecision(OPEN_EXTERNALLY, "https://example.com/")
        assertDecision(OPEN_EXTERNALLY, "https://youtube.com@evil.com/")
        assertDecision(OPEN_EXTERNALLY, "https://notyoutube.com/")
        assertDecision(OPEN_EXTERNALLY, "https://google.evil.com/")
        assertDecision(OPEN_EXTERNALLY, "mailto:someone@example.com")
        assertDecision(OPEN_EXTERNALLY, "market://details?id=x")
    }

    @Test
    fun `WebView-internal schemes load`() {
        assertDecision(LOAD, "about:blank")
        assertDecision(LOAD, "blob:https://m.youtube.com/abc")
    }
}

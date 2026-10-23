package io.github.z0orb.akagi.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentsTest {

    @Test
    fun `WebView markers are removed`() {
        val webView = "Mozilla/5.0 (Linux; Android 15; 23021RAA2Y Build/AQ3A.240912.001; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.122 Mobile Safari/537.36"
        val chrome = "Mozilla/5.0 (Linux; Android 15; 23021RAA2Y Build/AQ3A.240912.001) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.7390.122 Mobile Safari/537.36"
        assertEquals(chrome, UserAgents.chromeLike(webView))
    }

    @Test
    fun `a Chrome UA is unchanged`() {
        val chrome = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/141.0.0.0 Mobile Safari/537.36"
        assertEquals(chrome, UserAgents.chromeLike(chrome))
    }
}

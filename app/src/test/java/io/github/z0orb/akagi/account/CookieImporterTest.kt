package io.github.z0orb.akagi.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieImporterTest {

    private val now = 1_700_000_000L

    private fun parse(vararg lines: String) = CookieImporter.parse(lines.joinToString("\r\n"), now)

    @Test
    fun `a domain cookie becomes a Set-Cookie with domain, secure and expiry`() {
        val cookie = parse(".youtube.com\tTRUE\t/\tTRUE\t1800000000\tSID\tabc").single()
        assertEquals(
            ImportedCookie(
                url = "https://youtube.com/",
                name = "SID",
                setCookie = "SID=abc; Path=/; Domain=.youtube.com; Secure; Expires=Fri, 15 Jan 2027 08:00:00 GMT",
            ),
            cookie,
        )
    }

    @Test
    fun `HttpOnly lines are cookies, other comments and blank lines are not`() {
        val cookies = parse(
            "# Netscape HTTP Cookie File",
            "# This is a generated file!",
            "",
            "#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t0\tLOGIN_INFO\tv",
        )
        assertEquals("LOGIN_INFO=v; Path=/; Domain=.youtube.com; Secure; HttpOnly", cookies.single().setCookie)
    }

    @Test
    fun `host-only and __Host- cookies get no Domain attribute`() {
        val cookies = parse(
            "www.youtube.com\tFALSE\t/\tFALSE\t0\tYSC\tx",
            ".youtube.com\tTRUE\t/\tTRUE\t0\t__Host-3PLSID\ty",
        )
        assertEquals("https://www.youtube.com/", cookies[0].url)
        assertEquals("YSC=x; Path=/", cookies[0].setCookie)
        assertEquals("__Host-3PLSID=y; Path=/; Secure", cookies[1].setCookie)
    }

    @Test
    fun `only YouTube and Google cookies are kept`() {
        val cookies = parse(
            ".example.com\tTRUE\t/\tFALSE\t0\ta\t1",
            ".notyoutube.com\tTRUE\t/\tFALSE\t0\tb\t2",
            ".google.com\tTRUE\t/\tTRUE\t0\tc\t3",
            "accounts.google.com\tFALSE\t/\tTRUE\t0\td\t4",
            ".m.youtube.com\tTRUE\t/\tTRUE\t0\te\t5",
        )
        assertEquals(listOf("c", "d", "e"), cookies.map { it.name })
    }

    @Test
    fun `expired, malformed and nameless lines are skipped`() {
        val cookies = parse(
            ".youtube.com\tTRUE\t/\tTRUE\t1600000000\told\t1",
            ".youtube.com TRUE / TRUE 0 spaces 1",
            ".youtube.com\tTRUE\t/\tTRUE\tsoon\tbad\t1",
            ".youtube.com\tTRUE\t/\tTRUE\t0\t\t1",
        )
        assertTrue(cookies.isEmpty())
    }

    @Test
    fun `a missing value column reads as an empty value`() {
        assertEquals(
            "PREF=; Path=/; Domain=.youtube.com",
            parse(".youtube.com\tTRUE\t/\tFALSE\t0\tPREF").single().setCookie,
        )
    }
}

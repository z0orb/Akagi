package io.github.z0orb.akagi.account

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieExporterTest {

    private val now = 1_700_000_000L

    private fun lines(vararg cookies: String) =
        CookieExporter.toNetscape(cookies.toList(), ".youtube.com", now).lines().filter(String::isNotEmpty)

    @Test
    fun `the file starts with the Netscape header`() {
        assertEquals(listOf(CookieExporter.HEADER), lines())
    }

    @Test
    fun `cookie info entries keep their domain, path, secure flag and expiry`() {
        val line = lines(
            "SID=abc; domain=.youtube.com; path=/; expires=Fri, 15 Jan 2027 08:00:00 GMT; secure; httponly",
        )[1]
        assertEquals(".youtube.com\tTRUE\t/\tTRUE\t1800000000\tSID\tabc", line)
    }

    @Test
    fun `host-only cookies are written without the subdomain flag`() {
        assertEquals(
            listOf("www.youtube.com", "FALSE", "/feed", "FALSE"),
            lines("a=1; domain=www.youtube.com; path=/feed")[1].split('\t').take(4),
        )
    }

    @Test
    fun `bare pairs from a Cookie header belong to the default domain and live a day`() {
        val entries = CookieExporter.entriesOfHeader("PREF=f6=40000000; SID=x=y; junk")
        assertEquals(listOf("PREF=f6=40000000", "SID=x=y"), entries)
        val line = CookieExporter.toNetscape(entries, ".youtube.com", now).lines()[2]
        assertEquals(".youtube.com\tTRUE\t/\tFALSE\t${now + 86_400}\tSID\tx=y", line)
    }

    @Test
    fun `secure-prefixed names are always secure and max-age wins over expires`() {
        val line = lines("__Secure-3PSID=v; max-age=60; expires=Fri, 15 Jan 2027 08:00:00 GMT")[1]
        assertEquals(".youtube.com\tTRUE\t/\tTRUE\t${now + 60}\t__Secure-3PSID\tv", line)
    }

    @Test
    fun `cookies that would break the file are skipped`() {
        assertEquals(1, lines("a=b\tc", "=nameless", "ok=1").size - 1)
    }

    @Test
    fun `an unreadable expiry falls back to a day`() {
        assertEquals(now + 86_400, lines("a=1; expires=someday")[1].split('\t')[4].toLong())
    }
}

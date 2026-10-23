package io.github.z0orb.akagi.account

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.data.prefs.CookiePolicy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CookieAuthTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val signedIn = listOf("SID=abc; domain=.youtube.com", "PREF=x; domain=.youtube.com")
    private val visitor = listOf("VISITOR_INFO1_LIVE=v; domain=.youtube.com")

    private class FakeCookies(var entries: List<String>) : BrowserCookies {
        override suspend fun youtubeCookies() = entries

        override suspend fun add(cookies: List<ImportedCookie>) = Unit

        override suspend fun clear() {
            entries = emptyList()
        }
    }

    private var policy = CookiePolicy.ONLY_WHEN_NEEDED
    private var now = 1_700_000_000_000L
    private val browser = FakeCookies(signedIn)
    private val directory get() = File(folder.root, "cookies")
    private val auth by lazy { CookieAuth(browser, { policy }, directory, { now }, Dispatchers.Unconfined) }

    /** What each attempt saw: null for anonymous, else the cookie file's text. */
    private val attempts = mutableListOf<String?>()

    private suspend fun run(videoId: String = "v", failAnonymousWith: AppError? = null): String =
        auth.run(videoId) { file ->
            attempts += file?.readText()
            if (file == null && failAnonymousWith != null) throw AppException(failAnonymousWith)
            "ok"
        }

    @Test
    fun `only when needed runs anonymously when YouTube doesn't ask`() = runTest {
        assertEquals("ok", run())
        assertEquals(listOf(null), attempts)
    }

    @Test
    fun `only when needed retries an age-restricted video with the sign-in`() = runTest {
        assertEquals("ok", run(failAnonymousWith = AppError.AgeRestricted))
        assertEquals(2, attempts.size)
        assertEquals(null, attempts[0])
        assertTrue(attempts[1].orEmpty().contains("\tSID\tabc"))
    }

    @Test
    fun `members-only, bot checks and private videos are retried too, other errors are not`() = runTest {
        listOf(AppError.MembersOnly, AppError.BotCheck, AppError.Private).forEach {
            attempts.clear()
            run(videoId = it.key, failAnonymousWith = it)
            assertEquals(it.toString(), 2, attempts.size)
        }
        attempts.clear()
        val error = runCatching { run(videoId = "n", failAnonymousWith = AppError.Network) }.exceptionOrNull()
        assertEquals(AppError.Network, (error as AppException).error)
        assertEquals(listOf(null), attempts)
    }

    @Test
    fun `a video that needed the sign-in uses it straight away next time`() = runTest {
        run(failAnonymousWith = AppError.AgeRestricted)
        attempts.clear()
        run(failAnonymousWith = AppError.AgeRestricted)
        assertEquals(1, attempts.size)
        assertTrue(attempts.single() != null)
    }

    @Test
    fun `a visitor without a sign-in gets the original error`() = runTest {
        browser.entries = visitor
        val error = runCatching { run(failAnonymousWith = AppError.AgeRestricted) }.exceptionOrNull()
        assertEquals(AppError.AgeRestricted, (error as AppException).error)
        assertEquals(listOf(null), attempts)
    }

    @Test
    fun `always uses the sign-in from the start, and anonymously when signed out`() = runTest {
        policy = CookiePolicy.ALWAYS
        run()
        assertTrue(attempts.single() != null)
        browser.entries = visitor
        attempts.clear()
        run()
        assertEquals(listOf(null), attempts)
    }

    @Test
    fun `never doesn't retry`() = runTest {
        policy = CookiePolicy.NEVER
        val error = runCatching { run(failAnonymousWith = AppError.AgeRestricted) }.exceptionOrNull()
        assertEquals(AppError.AgeRestricted, (error as AppException).error)
        assertEquals(listOf(null), attempts)
    }

    @Test
    fun `the cookie file is deleted after the run, even when it fails`() = runTest {
        policy = CookiePolicy.ALWAYS
        runCatching { auth.run("v") { throw AppException(AppError.Network) } }
        run()
        assertEquals(0, directory.listFiles()?.size ?: 0)
    }

    @Test
    fun `clean-up removes only old leftovers`() = runTest {
        directory.mkdirs()
        val old = File(directory, "old.txt").apply {
            writeText("x")
            setLastModified(now - 7 * 60 * 60 * 1000L)
        }
        val fresh = File(directory, "fresh.txt").apply {
            writeText("x")
            setLastModified(now - 60_000)
        }
        auth.cleanUp()
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}

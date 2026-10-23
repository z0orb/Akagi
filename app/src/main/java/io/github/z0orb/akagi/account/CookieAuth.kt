package io.github.z0orb.akagi.account

import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.core.common.accountCanHelp
import io.github.z0orb.akagi.data.prefs.CookiePolicy
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Decides whether a yt-dlp run gets the browser's YouTube sign-in (PLAN §7.15, D-020). With "Only when
 * needed" a run goes out anonymously and is repeated with cookies only when YouTube asks for an account,
 * which keeps the account out of most requests (yt-dlp warns that accounts used this way can get flagged).
 */
class CookieAuth(
    private val cookies: BrowserCookies,
    private val policy: suspend () -> CookiePolicy,
    private val directory: File,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    // Videos that needed the account once; their download goes straight to the signed-in run.
    private val neededCookies: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Runs [block] with a cookies.txt for yt-dlp's `--cookies`, or with null to run anonymously. */
    suspend fun <T> run(videoId: String, block: suspend (cookieFile: File?) -> T): T {
        val policy = policy()
        if (policy == CookiePolicy.ALWAYS || (policy == CookiePolicy.ONLY_WHEN_NEEDED && videoId in neededCookies)) {
            signedInCookies()?.let { return withCookieFile(it, block) }
        }
        return try {
            block(null)
        } catch (e: AppException) {
            if (policy != CookiePolicy.ONLY_WHEN_NEEDED || !e.error.accountCanHelp) throw e
            val signedIn = signedInCookies() ?: throw e
            neededCookies += videoId
            withCookieFile(signedIn, block)
        }
    }

    private suspend fun signedInCookies(): List<String>? = cookies.youtubeCookies().takeIf(YouTubeSession::isSignedIn)

    // Each run gets its own file (up to three downloads run at once, and yt-dlp writes the jar back on exit),
    // deleted as soon as the run ends so the session doesn't sit on disk.
    private suspend fun <T> withCookieFile(entries: List<String>, block: suspend (File?) -> T): T {
        val file = withContext(io) {
            directory.mkdirs()
            File(directory, "${UUID.randomUUID()}.txt").apply {
                writeText(CookieExporter.toNetscape(entries, YOUTUBE_DOMAIN, clock.nowMillis() / 1000))
            }
        }
        return try {
            block(file)
        } finally {
            withContext(NonCancellable + io) { file.delete() }
        }
    }

    /** Deletes files left by runs the process died during; files of runs still going are younger than this. */
    suspend fun cleanUp() {
        val cutoff = clock.nowMillis() - LEFTOVER_AGE_MS
        withContext(io) { directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete) }
    }

    private companion object {
        const val YOUTUBE_DOMAIN = ".youtube.com"
        const val LEFTOVER_AGE_MS = 6 * 60 * 60 * 1000L
    }
}

package io.github.z0orb.akagi.ytdlp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.data.prefs.AutoUpdate
import io.github.z0orb.akagi.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-app yt-dlp updates (PLAN §7.14): on demand from Settings, and weekly in the background. */
@Singleton
class YtDlpUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: YtDlpEngine,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val running = Mutex()

    /** Waits for running fetches and downloads (the engine's write lock), then updates. */
    suspend fun updateNow(): UpdateResult = running.withLock {
        val channel = if (settings.current().nightlyYtDlp) UpdateChannel.NIGHTLY else UpdateChannel.STABLE
        engine.update(channel).also { settings.setLastUpdateCheck(clock.nowMillis()) }
    }

    /** Called at startup. Quietly does nothing when not due, on the wrong network, or offline. */
    suspend fun autoUpdateIfDue() {
        val current = settings.current()
        val due = clock.nowMillis() - current.lastUpdateCheckMillis >= WEEK_MILLIS
        // The bundled yt-dlp is months old and fails on YouTube, so the very first check may use mobile data (~3 MB).
        val firstCheck = current.lastUpdateCheckMillis == 0L && current.autoUpdate != AutoUpdate.OFF
        if (!due || !networkAllows(if (firstCheck) AutoUpdate.ANY_NETWORK else current.autoUpdate)) return
        try {
            Log.i(TAG, "Weekly yt-dlp check: ${updateNow()}")
        } catch (e: AppException) {
            Log.w(TAG, "Weekly yt-dlp check failed: ${e.error}", e)
        }
    }

    private fun networkAllows(mode: AutoUpdate): Boolean {
        if (mode == AutoUpdate.OFF) return false
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return mode == AutoUpdate.ANY_NETWORK ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val TAG = "YtDlpUpdater"
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

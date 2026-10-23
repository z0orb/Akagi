package io.github.z0orb.akagi.download

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.di.ApplicationScope
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.ytdlp.YtDlpUpdater
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App-start work for downloads: clean up, resume what was interrupted, and keep the service running while busy. */
@Singleton
class DownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val pipeline: DownloadPipeline,
    private val notifier: DownloadNotifier,
    private val updater: YtDlpUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            pipeline.cleanUpAll()
            queue.restore()
            updater.autoUpdateIfDue()
        }
        scope.launch {
            queue.tasks.map { tasks -> tasks.any { it.state != DownloadState.FAILED } }
                .distinctUntilChanged()
                .collect { busy -> if (busy) startService() }
        }
        scope.launch { queue.completed.collect(notifier::showCompleted) }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start a foreground service from the background (e.g. the process was
            // started by a notification action). Downloads still run; the service starts on the next launch.
            Log.w(TAG, "Couldn't start the download service", e)
        }
    }

    private companion object {
        const val TAG = "DownloadCoordinator"
    }
}

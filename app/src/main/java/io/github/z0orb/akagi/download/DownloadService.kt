package io.github.z0orb.akagi.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.z0orb.akagi.data.db.DownloadState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive in the background as a dataSync foreground service (PLAN §7.5). It only draws
 * notifications; the queue does the work and this stops itself once nothing is queued or running.
 */
@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var queue: DownloadQueue

    @Inject lateinit var notifier: DownloadNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    // The newest start request. stopSelfResult(it) refuses to stop when a later start is already on its way, which
    // would otherwise crash the app ("startForegroundService() did not then call startForeground()", B-06).
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        ServiceCompat.startForeground(
            this,
            DownloadNotifier.SUMMARY_ID,
            notifier.summary(activeCount = 1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!observing) {
            observing = true
            observe()
        }
        return START_NOT_STICKY
    }

    @OptIn(FlowPreview::class)
    private fun observe() = scope.launch {
        // Android rate-limits notification updates; once a second is what it allows per app (PLAN §7.5).
        combine(queue.tasks, queue.progress) { tasks, progress -> tasks to progress }
            .sample(NOTIFICATION_INTERVAL_MS)
            .collect { (tasks, progress) ->
                val active = activeCount(tasks)
                notifier.showTasks(tasks, progress)
                // The sample can be up to a second old, so ask the store again before giving up the service.
                if (active == 0 && activeCount(queue.tasks.first()) == 0) {
                    stop()
                } else {
                    notifier.refreshSummary(active)
                }
            }
    }

    /** Android 15 caps dataSync services at 6 hours a day; fail what's left so the user can retry later. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch {
            queue.interruptAll()
            stop()
        }
    }

    private fun activeCount(tasks: List<DownloadTask>) = tasks.count { it.state != DownloadState.FAILED }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // If a new download raced in, its start is still pending: onStartCommand then promotes the service again.
        stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_INTERVAL_MS = 1_000L
    }
}

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                val active = tasks.count { it.state != DownloadState.FAILED }
                notifier.showTasks(tasks, progress)
                if (active == 0) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notifier.refreshSummary(active)
                }
            }
    }

    /** Android 15 caps dataSync services at 6 hours a day; fail what's left so the user can retry later. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch {
            queue.interruptAll()
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_INTERVAL_MS = 1_000L
    }
}

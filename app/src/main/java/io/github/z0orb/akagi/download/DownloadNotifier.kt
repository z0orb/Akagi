package io.github.z0orb.akagi.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.MainActivity
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.account.AccountRepository
import io.github.z0orb.akagi.core.designsystem.describe
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import javax.inject.Inject
import javax.inject.Singleton

/** Progress, failure and completion notifications (PLAN §7.5). The group summary is the service's FGS notification. */
@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val account: AccountRepository,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val shown = mutableSetOf<Long>()

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.notif_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                context.getString(R.string.notif_channel_events),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun summary(activeCount: Int): Notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_stat_akagi)
        .setContentTitle(context.getString(R.string.notif_summary))
        .setContentText(context.resources.getString(R.string.notif_summary_count, activeCount))
        .setContentIntent(openApp())
        .setGroup(GROUP)
        .setGroupSummary(true)
        .setOngoing(true)
        .setSilent(true)
        .build()

    fun refreshSummary(activeCount: Int) = notify(SUMMARY_ID, summary(activeCount))

    /** Redraws one notification per queued, running or failed task and removes the ones that are gone. */
    fun showTasks(tasks: List<DownloadTask>, progress: Map<Long, DownloadProgress>) {
        if (!canNotify()) return
        val ids = tasks.map { it.id }.toSet()
        (shown - ids).forEach { manager.cancel(taskNotificationId(it)) }
        shown.retainAll(ids)
        tasks.forEach { task ->
            notify(
                taskNotificationId(task.id),
                if (task.state ==
                    DownloadState.FAILED
                ) {
                    failed(task)
                } else {
                    running(task, progress[task.id])
                },
            )
            shown += task.id
        }
    }

    fun showCompleted(done: CompletedDownload) {
        if (!canNotify()) return
        manager.cancel(taskNotificationId(done.taskId))
        shown -= done.taskId
        notify(
            doneNotificationId(done.taskId),
            NotificationCompat.Builder(context, CHANNEL_EVENTS)
                .setSmallIcon(R.drawable.ic_stat_akagi)
                .setContentTitle(done.title)
                .setContentText(context.getString(R.string.notif_done))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun running(task: DownloadTask, progress: DownloadProgress?): Notification {
        val percent = ((progress?.fraction ?: 0f) * 100).toInt()
        val text = when {
            task.state == DownloadState.QUEUED -> context.getString(R.string.notif_queued)

            task.state == DownloadState.POST_PROCESSING -> context.getString(R.string.notif_processing)

            progress?.speed != null && progress.etaSeconds != null ->
                context.getString(
                    R.string.notif_progress_speed,
                    percent,
                    progress.speed,
                    formatEta(progress.etaSeconds),
                )

            else -> context.getString(R.string.notif_progress, percent)
        }
        val indeterminate = task.state != DownloadState.RUNNING || progress == null
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_akagi)
            .setContentTitle(task.request.title)
            .setContentText(text)
            .setProgress(PROGRESS_MAX, percent, indeterminate)
            .setContentIntent(openApp())
            .addAction(
                0,
                context.getString(R.string.action_cancel),
                action(DownloadActionReceiver.ACTION_CANCEL, task.id),
            )
            .setGroup(GROUP)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun failed(task: DownloadTask): Notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
        .setSmallIcon(R.drawable.ic_stat_akagi)
        .setContentTitle(task.request.title)
        .setContentText(
            task.error?.describe(context.resources, account.state.value) ?: context.getString(R.string.notif_failed),
        )
        .setContentIntent(openApp())
        .addAction(0, context.getString(R.string.action_retry), action(DownloadActionReceiver.ACTION_RETRY, task.id))
        .addAction(0, context.getString(R.string.action_cancel), action(DownloadActionReceiver.ACTION_CANCEL, task.id))
        .setGroup(GROUP)
        .setOnlyAlertOnce(true)
        .build()

    private fun notify(id: Int, notification: Notification) {
        // Checked again here because the user can revoke the permission at any time.
        if (canNotify()) {
            @Suppress("MissingPermission")
            manager.notify(id, notification)
        }
    }

    // False on Android 13+ until POST_NOTIFICATIONS is granted; downloads then show progress in the app only.
    private fun canNotify(): Boolean = manager.areNotificationsEnabled()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Cancel and Retry differ by action, so one request code per task is enough.
        taskNotificationId(taskId),
        Intent(
            context,
            DownloadActionReceiver::class.java,
        ).setAction(action).putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun formatEta(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    companion object {
        const val SUMMARY_ID = 1
        private const val CHANNEL_PROGRESS = "downloads"
        private const val CHANNEL_EVENTS = "download_events"
        private const val GROUP = "io.github.z0orb.akagi.DOWNLOADS"
        private const val PROGRESS_MAX = 100

        // Task ids are small positive longs; keep their notification ids clear of the summary.
        private fun taskNotificationId(taskId: Long) = (taskId % 1_000_000).toInt() * 2 + 100

        private fun doneNotificationId(taskId: Long) = taskNotificationId(taskId) + 1
    }
}

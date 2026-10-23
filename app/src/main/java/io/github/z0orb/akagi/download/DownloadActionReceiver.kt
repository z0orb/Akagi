package io.github.z0orb.akagi.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.z0orb.akagi.core.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Cancel and Retry buttons on download notifications. */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {
    @Inject lateinit var queue: DownloadQueue

    @Inject lateinit var coordinator: DownloadCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it >= 0 } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CANCEL -> queue.cancel(taskId)
                    ACTION_RETRY -> queue.retry(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "io.github.z0orb.akagi.download.CANCEL"
        const val ACTION_RETRY = "io.github.z0orb.akagi.download.RETRY"
        const val EXTRA_TASK_ID = "task_id"
    }
}

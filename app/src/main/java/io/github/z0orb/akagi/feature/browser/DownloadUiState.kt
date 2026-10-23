package io.github.z0orb.akagi.feature.browser

import androidx.compose.runtime.Immutable
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.ytdlp.FormatChoices
import io.github.z0orb.akagi.ytdlp.FormatOption

@Immutable
data class DownloadUiState(
    val fab: FabState = FabState.Idle,
    /** Null while the format sheet is closed. */
    val sheet: SheetUiState? = null,
    /** Something the UI should do once, then acknowledge with [DownloadViewModel.onEventHandled]. */
    val event: DownloadEvent? = null,
)

/** The FAB for the video on screen (PLAN §7.3). */
sealed interface FabState {
    data object Idle : FabState

    data object Fetching : FabState

    /** [progress] is null while queued or post-processing. */
    data class Downloading(val progress: Float?) : FabState

    data object Downloaded : FabState
}

@Immutable
data class SheetUiState(val title: String?, val content: SheetContent)

sealed interface SheetContent {
    data object Loading : SheetContent

    data object UpdatingYtDlp : SheetContent

    data class Failed(val error: AppError) : SheetContent

    @Immutable
    data class Choose(val choices: FormatChoices, val selected: FormatOption) : SheetContent {
        val codec: AudioCodec get() = selected.codec
    }

    /** Already downloaded: offer Play or Download again (PLAN §7.5 "Duplicates"). */
    data class InLibrary(val codec: AudioCodec, val sizeBytes: Long, val contentUri: String) : SheetContent

    /** Queued, running or failed for this video: show progress with Cancel, or the error with Retry. */
    data class Active(val taskId: Long, val state: DownloadState, val progress: Float?, val error: AppError?) :
        SheetContent
}

sealed interface DownloadEvent {
    val id: Long

    data class Toast(override val id: Long, val message: Int) : DownloadEvent

    /** Opens a downloaded file in another player; Akagi's own player arrives in Phase 5. */
    data class OpenFile(override val id: Long, val contentUri: String, val codec: AudioCodec) : DownloadEvent
}

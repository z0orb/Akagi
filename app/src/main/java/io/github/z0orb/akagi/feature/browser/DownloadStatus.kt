package io.github.z0orb.akagi.feature.browser

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.download.DownloadTask
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import io.github.z0orb.akagi.ytdlp.FormatOption
import io.github.z0orb.akagi.ytdlp.FormatRecommender
import io.github.z0orb.akagi.ytdlp.VideoInfo

/** Where one video stands: in the library, in the queue, both or neither. */
internal data class VideoStatus(val track: TrackEntity?, val task: DownloadTask?, val progress: DownloadProgress?)

internal sealed interface Load {
    data object Loading : Load

    data object UpdatingYtDlp : Load

    data class Ready(val info: VideoInfo) : Load

    data class Failed(val error: AppError) : Load
}

/** The open sheet. [choosing] is set by "Download again", which skips the already-downloaded prompt. */
internal data class SheetModel(
    val videoId: String,
    val title: String? = null,
    val load: Load = Load.Loading,
    val selected: FormatOption? = null,
    val choosing: Boolean = false,
)

internal fun fabStateOf(status: VideoStatus?, fetching: Boolean): FabState {
    val task = status?.task?.takeIf { it.state != DownloadState.FAILED }
    return when {
        task != null -> FabState.Downloading(status.progress?.fraction?.takeIf { task.state == DownloadState.RUNNING })
        fetching -> FabState.Fetching
        status?.track != null -> FabState.Downloaded
        else -> FabState.Idle
    }
}

internal fun sheetContentOf(model: SheetModel, status: VideoStatus?): SheetContent {
    val task = status?.task
    val track = status?.track
    return when {
        // A queued, running or failed download for this video always wins: it's what the user acts on next.
        task != null && !model.choosing ->
            SheetContent.Active(task.id, task.state, status.progress?.fraction, task.error)

        track != null && !model.choosing -> SheetContent.InLibrary(track.codec, track.sizeBytes, track.contentUri)

        else -> when (val load = model.load) {
            Load.Loading -> SheetContent.Loading

            Load.UpdatingYtDlp -> SheetContent.UpdatingYtDlp

            is Load.Failed -> SheetContent.Failed(load.error)

            is Load.Ready -> {
                val choices = FormatRecommender.choicesFor(load.info)
                val selected = model.selected ?: choices.recommended
                selected?.let { SheetContent.Choose(choices, it) } ?: SheetContent.Failed(AppError.Unavailable)
            }
        }
    }
}

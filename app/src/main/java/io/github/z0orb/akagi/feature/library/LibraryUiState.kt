package io.github.z0orb.akagi.feature.library

import androidx.compose.runtime.Immutable
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.data.db.PlaylistSummary
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.download.DownloadTask
import io.github.z0orb.akagi.ytdlp.DownloadProgress

@Immutable
data class LibraryUiState(
    val tasks: List<DownloadTask> = emptyList(),
    val progress: Map<Long, DownloadProgress> = emptyMap(),
    val tracks: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    /** The playlist shown as a sub-page inside the panel, if any. */
    val openPlaylist: PlaylistDetail? = null,
    val tab: LibraryTab = LibraryTab.DOWNLOADS,
    val dialog: LibraryDialog? = null,
)

enum class LibraryTab(val title: Int) {
    DOWNLOADS(R.string.library_tab_downloads),
    PLAYLISTS(R.string.library_tab_playlists),
}

@Immutable
data class PlaylistDetail(val id: Long, val name: String, val tracks: List<TrackEntity>)

enum class NameError { BLANK, TOO_LONG }

@Immutable
sealed interface LibraryDialog {
    /** Multi-select checklist; [draftGeneration] changes when an inline-created playlist clears the name field. */
    data class AddToPlaylist(
        val track: TrackEntity,
        val selected: Set<Long>,
        val nameError: NameError? = null,
        val draftGeneration: Int = 0,
    ) : LibraryDialog

    /** Create ([playlistId] null) or rename. */
    data class NamePlaylist(val playlistId: Long?, val initialName: String, val error: NameError? = null) :
        LibraryDialog

    data class DeletePlaylist(val playlistId: Long, val name: String) : LibraryDialog
}

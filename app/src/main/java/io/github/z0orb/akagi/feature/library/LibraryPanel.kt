package io.github.z0orb.akagi.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.data.db.TrackEntity

/** Content of the side panel: Downloads | Playlists tabs, or an open playlist as a sub-page (PLAN §7.12). */
@Composable
fun LibraryPanel(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val detail = state.openPlaylist
    // Composed after MainScreen's handlers, so back leaves the playlist before it closes the panel.
    BackHandler(enabled = detail != null, onBack = actions.onClosePlaylist)
    if (detail != null) {
        PlaylistDetailPage(detail, actions, modifier)
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            SecondaryTabRow(
                selectedTabIndex = state.tab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                LibraryTab.entries.forEach {
                    Tab(
                        selected = state.tab == it,
                        onClick = { actions.onSelectTab(it) },
                        text = { Text(stringResource(it.title)) },
                    )
                }
            }
            when (state.tab) {
                LibraryTab.DOWNLOADS -> DownloadsTab(state, actions)
                LibraryTab.PLAYLISTS -> PlaylistsTab(state.playlists, actions)
            }
        }
    }
    state.dialog?.let { LibraryDialogHost(it, state.playlists, actions) }
}

/** Every user intent the panel can raise, bundled so the panel's signatures stay small. */
class LibraryActions(
    val onSelectTab: (LibraryTab) -> Unit,
    val onPlayTrack: (TrackEntity) -> Unit,
    val onCancelTask: (Long) -> Unit,
    val onRetryTask: (Long) -> Unit,
    val onAddToPlaylist: (TrackEntity) -> Unit,
    val onDeleteTrack: (TrackEntity) -> Unit,
    val onConfirmDeleteTrack: () -> Unit,
    val onOpenPlaylist: (Long) -> Unit,
    val onClosePlaylist: () -> Unit,
    val onNewPlaylist: () -> Unit,
    val onRenamePlaylist: (id: Long, name: String) -> Unit,
    val onDeletePlaylist: (id: Long, name: String) -> Unit,
    val onRemoveFromPlaylist: (playlistId: Long, trackId: Long) -> Unit,
    val onReorder: (playlistId: Long, trackIds: List<Long>) -> Unit,
    val onDismissDialog: () -> Unit,
    val onToggleMembership: (Long) -> Unit,
    val onCreatePlaylistInline: (String) -> Unit,
    val onConfirmMemberships: () -> Unit,
    val onSubmitPlaylistName: (String) -> Unit,
    val onConfirmDeletePlaylist: () -> Unit,
)

@Composable
private fun DownloadsTab(state: LibraryUiState, actions: LibraryActions) {
    if (state.tasks.isEmpty() && state.tracks.isEmpty()) {
        EmptyState(stringResource(R.string.downloads_empty))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (state.tasks.isNotEmpty()) {
            item(key = "active-header", contentType = "header") {
                SectionHeader(stringResource(R.string.downloads_active))
            }
            items(state.tasks, key = { "task-${it.id}" }, contentType = { "task" }) { task ->
                TaskRow(task, state.progress[task.id], actions.onCancelTask, actions.onRetryTask)
            }
            if (state.tracks.isNotEmpty()) {
                item(key = "library-header", contentType = "header") {
                    SectionHeader(stringResource(R.string.downloads_library))
                }
            }
        }
        items(state.tracks, key = { "track-${it.id}" }, contentType = { "track" }) { track ->
            TrackRow(
                track,
                onClick = { actions.onPlayTrack(track) },
                trailing = {
                    RowMenu(
                        listOf(
                            MenuEntry(R.string.track_add_to_playlist) { actions.onAddToPlaylist(track) },
                            MenuEntry(R.string.track_delete) { actions.onDeleteTrack(track) },
                        ),
                    )
                },
            )
        }
    }
}

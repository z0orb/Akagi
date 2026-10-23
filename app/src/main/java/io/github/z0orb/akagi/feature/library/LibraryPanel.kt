package io.github.z0orb.akagi.feature.library

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

private enum class LibraryTab(val title: Int) {
    DOWNLOADS(R.string.library_tab_downloads),
    PLAYLISTS(R.string.library_tab_playlists),
}

/** Content of the side panel: Downloads | Playlists tabs (PLAN §7.12). */
@Composable
fun LibraryPanel(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.DOWNLOADS) }
    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            LibraryTab.entries.forEach {
                Tab(
                    selected = tab == it,
                    onClick = { tab = it },
                    text = { Text(stringResource(it.title)) },
                )
            }
        }
        when (tab) {
            LibraryTab.DOWNLOADS -> DownloadsTab(state, actions)
            LibraryTab.PLAYLISTS -> EmptyState(stringResource(R.string.playlists_empty))
        }
    }
}

/** Every user intent the panel can raise, bundled so the panel's signature stays small. */
class LibraryActions(
    val onPlayTrack: (TrackEntity) -> Unit,
    val onCancelTask: (Long) -> Unit,
    val onRetryTask: (Long) -> Unit,
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
            TrackRow(track, onClick = { actions.onPlayTrack(track) })
        }
    }
}

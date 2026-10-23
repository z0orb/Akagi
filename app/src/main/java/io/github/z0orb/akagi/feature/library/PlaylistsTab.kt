package io.github.z0orb.akagi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.designsystem.TrackThumbnail
import io.github.z0orb.akagi.data.db.PlaylistSummary

@Composable
internal fun PlaylistsTab(
    playlists: List<PlaylistSummary>,
    actions: LibraryActions,
    guard: ScrollGuard,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    TrackScroll(guard, list)
    LazyColumn(modifier = modifier, state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "new", contentType = "new") { NewPlaylistRow(onClick = actions.onNewPlaylist) }
        if (playlists.isEmpty()) {
            item(key = "empty", contentType = "empty") { EmptyState(stringResource(R.string.playlists_empty)) }
        }
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistRow(playlist, actions, guard)
        }
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.playlist_new),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, actions: LibraryActions, guard: ScrollGuard) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .deliberateClickable(guard, interaction) { actions.onOpenPlaylist(playlist.id) }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        TrackThumbnail(playlist.coverPath, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.playlist_track_count, playlist.trackCount, playlist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowMenu(playlistMenu(playlist.id, playlist.name, actions))
    }
}

internal fun playlistMenu(id: Long, name: String, actions: LibraryActions): List<MenuEntry> = listOf(
    MenuEntry(R.string.playlist_rename) { actions.onRenamePlaylist(id, name) },
    MenuEntry(R.string.playlist_delete) { actions.onDeletePlaylist(id, name) },
)

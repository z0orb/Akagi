package io.github.z0orb.akagi.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A playlist's tracks in order; the handle on each row drags it to a new place (PLAN P4.6). */
@Composable
internal fun PlaylistDetailPage(
    detail: PlaylistDetail,
    actions: LibraryActions,
    guard: ScrollGuard,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = actions.onClosePlaylist) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                detail.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RowMenu(playlistMenu(detail.id, detail.name, actions))
        }
        if (detail.tracks.isEmpty()) {
            EmptyState(stringResource(R.string.playlist_empty))
        } else {
            ReorderableTracks(detail, actions, guard)
        }
    }
}

@Composable
private fun ReorderableTracks(detail: PlaylistDetail, actions: LibraryActions, guard: ScrollGuard) {
    val haptics = LocalHapticFeedback.current
    // Moves happen locally while dragging; the new order is written once, when the drag ends.
    var tracks by remember(detail.tracks) { mutableStateOf(detail.tracks) }
    val listState = rememberLazyListState()
    TrackScroll(guard, listState)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        tracks = tracks.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        items(tracks, key = { it.id }, contentType = { "track" }) { track ->
            ReorderableItem(reorderState, key = track.id) { isDragging ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = if (isDragging) 6.dp else 0.dp,
                ) {
                    TrackRow(
                        track,
                        guard = guard,
                        onClick = { actions.onPlayTrack(track) },
                        startPadding = 4.dp,
                        // On the left, so the title keeps its room at Half width.
                        leading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 36.dp, height = 48.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = { actions.onReorder(detail.id, tracks.map { it.id }) },
                                    ),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = stringResource(R.string.playlist_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        trailing = {
                            RowMenu(
                                listOf(
                                    MenuEntry(R.string.track_add_to_playlist) { actions.onAddToPlaylist(track) },
                                    MenuEntry(R.string.playlist_remove_track) {
                                        actions.onRemoveFromPlaylist(detail.id, track.id)
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

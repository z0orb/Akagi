package io.github.z0orb.akagi.feature.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.data.db.TrackEntity
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
            PlayButtons(onPlay = actions.onPlayPlaylist)
            ReorderableTracks(detail, actions, guard)
        }
    }
}

/** In their own row, so the playlist name keeps the header's width at Half. */
@Composable
private fun PlayButtons(onPlay: (shuffle: Boolean) -> Unit) {
    val buttonPadding = PaddingValues(horizontal = 8.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        FilledTonalButton(onClick = { onPlay(false) }, contentPadding = buttonPadding, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
            ButtonLabel(stringResource(R.string.action_play))
        }
        OutlinedButton(onClick = { onPlay(true) }, contentPadding = buttonPadding, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null)
            ButtonLabel(stringResource(R.string.action_shuffle))
        }
    }
}

/** Shrinks rather than clipping when the panel is at Half width or the font scale is large. */
@Composable
private fun ButtonLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
        modifier = Modifier.padding(start = 6.dp),
    )
}

@Composable
private fun ReorderableTracks(detail: PlaylistDetail, actions: LibraryActions, guard: ScrollGuard) {
    val haptics = LocalHapticFeedback.current
    // Moves happen locally while dragging; the new order is written once, when the drag ends. Room's updates wait
    // until then: a member row changing mid-drag (a re-download, the missing flag) snapped the row back (B-14).
    var tracks by remember(detail.id) { mutableStateOf(detail.tracks) }
    var dragStartOrder by remember(detail.id) { mutableStateOf<List<Long>?>(null) }
    var heldBack by remember(detail.id) { mutableStateOf<List<TrackEntity>?>(null) }
    LaunchedEffect(detail.tracks) {
        if (dragStartOrder == null) tracks = detail.tracks else heldBack = detail.tracks
    }

    fun endDrag() {
        val order = tracks.map { it.id }
        // An unchanged order isn't written; Room's re-emission after a write brings any held-back change anyway.
        if (order != dragStartOrder) actions.onReorder(detail.id, order) else heldBack?.let { tracks = it }
        heldBack = null
        dragStartOrder = null
    }
    val listState = rememberLazyListState()
    TrackScroll(guard, listState)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        tracks = tracks.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    // Dragging can't be done with TalkBack, so each row also offers "Move up" / "Move down" (P7.4).
    val moveUp = stringResource(R.string.playlist_move_up)
    val moveDown = stringResource(R.string.playlist_move_down)

    // Returns true: the accessibility action was handled.
    fun move(from: Int, to: Int): Boolean {
        tracks = tracks.toMutableList().apply { add(to, removeAt(from)) }
        actions.onReorder(detail.id, tracks.map { it.id })
        return true
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
            ReorderableItem(reorderState, key = track.id) { isDragging ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = if (isDragging) 6.dp else 0.dp,
                    modifier = Modifier.semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index, index - 1) })
                            if (index < tracks.lastIndex) {
                                add(CustomAccessibilityAction(moveDown) { move(index, index + 1) })
                            }
                        }
                    },
                ) {
                    TrackRow(
                        track,
                        guard = guard,
                        onClick = { actions.onPlayPlaylistTrack(track) },
                        startPadding = 4.dp,
                        // On the left, so the title keeps its room at Half width.
                        leading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 36.dp, height = 48.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            dragStartOrder = tracks.map { it.id }
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = { endDrag() },
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

package io.github.z0orb.akagi.feature.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.DurationFormatter
import io.github.z0orb.akagi.core.designsystem.TrackThumbnail
import io.github.z0orb.akagi.playback.NowPlaying
import io.github.z0orb.akagi.playback.OutputFormat
import io.github.z0orb.akagi.playback.RepeatMode
import kotlinx.coroutines.delay

/** Every intent the player can raise. */
class PlayerActions(
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (positionMs: Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val positionMs: () -> Long?,
)

/**
 * The player under the browser (PLAN §7.13): compact by default, expanded by a tap or an upward swipe,
 * collapsed by a downward swipe, the chevron or back. The caller hides it when nothing is queued.
 */
@Composable
fun PlayerBar(
    state: PlayerUiState,
    track: NowPlaying,
    actions: PlayerActions,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var seeks by remember { mutableIntStateOf(0) }
    // Read only by the time texts and the seekbar's drawing, so a tick doesn't recompose the bar (PF-03).
    val position = rememberPositionMs(state.isPlaying, track.trackId, seeks, actions.positionMs)
    val positionMs = { position.value }
    val progress = { if (track.durationMs > 0) positionMs().toFloat() / track.durationMs else 0f }
    val seek: (Float) -> Unit = { fraction ->
        actions.onSeek((fraction * track.durationMs).toLong())
        seeks++
    }
    val swipeThreshold = with(LocalDensity.current) { swipeDistance.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragged += it },
                onDragStopped = { velocity ->
                    when {
                        dragged < -swipeThreshold || velocity < -FLING_VELOCITY -> onExpandedChange(true)
                        dragged > swipeThreshold || velocity > FLING_VELOCITY -> onExpandedChange(false)
                    }
                    dragged = 0f
                },
            )
            .animateContentSize(),
    ) {
        if (expanded) {
            ExpandedPlayer(state, track, actions, positionMs, progress, seek, onCollapse = { onExpandedChange(false) })
        } else {
            MiniPlayer(state, track, actions, positionMs, progress, onExpand = { onExpandedChange(true) })
        }
    }
}

@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    track: NowPlaying,
    actions: PlayerActions,
    position: () -> Long,
    progress: () -> Float,
    onExpand: () -> Unit,
) {
    val expandLabel = stringResource(R.string.player_expand)
    Column(modifier = Modifier.clickable(onClickLabel = expandLabel, onClick = onExpand)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 4.dp),
        ) {
            TrackThumbnail(track.thumbnailPath, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    // One pass: while it scrolls, every frame redraws (≈ 90 ms/s main + 200 ms/s RenderThread, PF-14).
                    modifier = Modifier.basicMarquee(iterations = 1),
                )
                TimeText({ timesOf(position(), track.durationMs) })
            }
            PlayPauseButton(state.isPlaying, actions.onPlayPause)
            IconButton(onClick = actions.onNext) {
                Icon(
                    painterResource(R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.player_next),
                )
            }
        }
        WaveformSeekBar(
            waveform = state.waveform,
            progress = progress,
            onSeek = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(16.dp).padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun ExpandedPlayer(
    state: PlayerUiState,
    track: NowPlaying,
    actions: PlayerActions,
    position: () -> Long,
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    onCollapse: () -> Unit,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.player_collapse),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                state.playingFrom?.let {
                    Text(
                        stringResource(R.string.player_playing_from, playingFromLabel(it)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            state.usbDac?.let { UsbDacChip(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrackThumbnail(track.thumbnailPath, modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        WaveformSeekBar(
            waveform = state.waveform,
            progress = progress,
            onSeek = onSeek,
            onScrub = { scrub = it },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val scrubbed = scrub?.let { (it * track.durationMs).toLong() }
            val timeColor = if (scrubbed == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
            TimeText({ DurationFormatter.format(scrubbed ?: position()) }, color = timeColor)
            Spacer(Modifier.weight(1f))
            Text(
                DurationFormatter.format(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Controls(state, actions)
    }
}

@Composable
private fun Controls(state: PlayerUiState, actions: PlayerActions) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconToggleButton(
            checked = state.shuffle,
            onCheckedChange = { actions.onToggleShuffle() },
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = stringResource(R.string.action_shuffle))
        }
        IconButton(onClick = actions.onPrevious) {
            Icon(
                painterResource(R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.player_previous),
            )
        }
        FilledIconButton(onClick = actions.onPlayPause, modifier = Modifier.size(56.dp)) {
            PlayPauseIcon(state.isPlaying)
        }
        IconButton(onClick = actions.onNext) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.player_next))
        }
        IconButton(onClick = actions.onCycleRepeat) {
            Icon(
                painterResource(if (state.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                contentDescription = stringResource(
                    when (state.repeat) {
                        RepeatMode.OFF -> R.string.player_repeat_off
                        RepeatMode.ALL -> R.string.player_repeat_all
                        RepeatMode.ONE -> R.string.player_repeat_one
                    },
                ),
                tint = if (state.repeat == RepeatMode.OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) { PlayPauseIcon(isPlaying) }
}

@Composable
private fun PlayPauseIcon(isPlaying: Boolean) {
    Icon(
        painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.action_play),
    )
}

@Composable
private fun playingFromLabel(from: PlayingFrom): String = when (from) {
    PlayingFrom.Downloads -> stringResource(R.string.library_tab_downloads)
    is PlayingFrom.Playlist -> from.name
}

/** Reads [text] itself, so a position tick recomposes only this label (PF-03). */
@Composable
private fun TimeText(text: () -> String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text(), style = MaterialTheme.typography.labelSmall, color = color)
}

private fun timesOf(position: Long, duration: Long) =
    "${DurationFormatter.format(position)} / ${DurationFormatter.format(duration)}"

/**
 * The playback position, re-read on each whole second (when the time text changes) only while playing and while
 * the app is visible (§7.13, PF-03), and once whenever the track, the play state or a seek changes it.
 */
@Composable
private fun rememberPositionMs(isPlaying: Boolean, trackId: Long, seeks: Int, read: () -> Long?): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val position = remember { mutableLongStateOf(read() ?: 0L) }
    val currentRead by rememberUpdatedState(read)
    LaunchedEffect(isPlaying, trackId, seeks, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Back in the foreground the controller reconnects asynchronously. Reading before that gave 0:00, which
            // stayed while paused; the last position is kept until the real one can be read.
            var current = currentRead()
            var polls = 0
            while (current == null && polls++ < RECONNECT_POLLS) {
                delay(RECONNECT_POLL_MS)
                current = currentRead()
            }
            current?.let { position.longValue = it }
            while (isPlaying) {
                delay(TICK_MS - position.longValue % TICK_MS)
                currentRead()?.let { position.longValue = it }
            }
        }
    }
    return position
}

private val swipeDistance = 48.dp
private const val FLING_VELOCITY = 1_000f
private const val TICK_MS = 1_000L
private const val RECONNECT_POLL_MS = 50L

// The connection's own timeout (5 s); a bind that fails leaves the last position shown.
private const val RECONNECT_POLLS = 100

/** "USB DAC · 44.1 kHz · 24-bit", or "USB DAC" alone while Android picks the format (PLAN §7.19). */
@Composable
private fun UsbDacChip(output: UsbDacOutput) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text = output.format?.let { stringResource(R.string.player_output_usb_dac, OutputFormat.format(it)) }
                ?: stringResource(R.string.output_usb_dac),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

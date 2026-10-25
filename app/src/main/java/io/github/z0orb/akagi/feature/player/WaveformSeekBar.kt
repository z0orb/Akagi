package io.github.z0orb.akagi.feature.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.designsystem.theme.waveformUnplayed

/**
 * The track's waveform as bars, played part in the primary colour (PLAN §7.13). Tap seeks; a horizontal
 * drag scrubs and seeks on release, reporting the scrub position through [onScrub] for a time preview.
 * Without a waveform it draws flat bars. [progress] is read at draw time, so a tick redraws the bars without
 * recomposing (PF-03); only the whole percent goes to accessibility.
 */
@Composable
fun WaveformSeekBar(
    waveform: Waveform?,
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScrub: (Float?) -> Unit = {},
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentProgress by rememberUpdatedState(progress)
    val shown = { (scrub ?: currentProgress()).coerceIn(0f, 1f) }
    val percent by remember { derivedStateOf { (shown() * PERCENT).toInt() } }
    val played = MaterialTheme.colorScheme.primary
    val unplayed = MaterialTheme.colorScheme.waveformUnplayed
    val description = stringResource(R.string.player_seek)

    val input = if (!enabled) {
        Modifier
    } else {
        Modifier
            .semantics {
                setProgress { target ->
                    currentOnSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { currentOnSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                fun update(x: Float) {
                    scrub = (x / size.width).coerceIn(0f, 1f)
                    currentOnScrub(scrub)
                }
                detectHorizontalDragGestures(
                    onDragStart = { update(it.x) },
                    onDragEnd = {
                        scrub?.let(currentOnSeek)
                        scrub = null
                        currentOnScrub(null)
                    },
                    onDragCancel = {
                        scrub = null
                        currentOnScrub(null)
                    },
                ) { change, _ -> update(change.position.x) }
            }
    }

    Spacer(
        modifier = modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(percent / PERCENT, 0f..1f)
            }
            .then(input)
            .drawWithCache {
                val widthPx = barWidth.toPx()
                val step = widthPx + barGap.toPx()
                val count = ((size.width + barGap.toPx()) / step).toInt()
                // Resampled only when the size or the waveform changes.
                val heights = waveform?.let { WaveformBars.resample(it.peaks, count) } ?: FloatArray(count) { FLAT_BAR }
                val radius = CornerRadius(widthPx / 2)
                onDrawBehind {
                    val drawnProgress = shown()
                    heights.forEachIndexed { i, height ->
                        val barHeight = (height * size.height).coerceAtLeast(widthPx)
                        drawRoundRect(
                            color = if ((i + 0.5f) / count <= drawnProgress) played else unplayed,
                            topLeft = Offset(i * step, (size.height - barHeight) / 2),
                            size = Size(widthPx, barHeight),
                            cornerRadius = radius,
                        )
                    }
                }
            },
    )
}

private val barWidth = 3.dp
private val barGap = 2.dp
private const val FLAT_BAR = 0.2f
private const val PERCENT = 100f

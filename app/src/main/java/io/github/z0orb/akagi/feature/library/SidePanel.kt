package io.github.z0orb.akagi.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class SidePanelValue { Closed, Half, Full }

/** The panel's position; its anchor offsets are the panel's visible width in pixels (PLAN §7.12). */
@Stable
class SidePanelState(initialValue: SidePanelValue) {
    internal val draggable = AnchoredDraggableState(initialValue)

    /** True from the moment the panel starts opening until it is fully closed again. */
    val isOpen: Boolean
        get() = draggable.currentValue != SidePanelValue.Closed || draggable.targetValue != SidePanelValue.Closed

    internal val visibleWidthPx: Float
        get() = draggable.offset.takeUnless { it.isNaN() } ?: 0f

    suspend fun open() = draggable.animateTo(SidePanelValue.Half, snapSpec)

    suspend fun close() = draggable.animateTo(SidePanelValue.Closed, snapSpec)

    suspend fun toggle() = if (draggable.settledValue == SidePanelValue.Closed) open() else close()

    companion object {
        private val snapSpec = spring<Float>(stiffness = 600f)

        val Saver: Saver<SidePanelState, SidePanelValue> = Saver(
            save = { it.draggable.settledValue },
            restore = ::SidePanelState,
        )
    }
}

@Composable
fun rememberSidePanelState(): SidePanelState =
    rememberSaveable(saver = SidePanelState.Saver) { SidePanelState(SidePanelValue.Closed) }

/**
 * Right-edge library panel. It moves only by its handle, which is excluded from system gestures, so neither
 * the back swipe nor YouTube's own gestures can trigger it (D-013). Tapping the scrim closes it.
 */
@Composable
fun SidePanel(
    state: SidePanelState,
    handleVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerPx = constraints.maxWidth.toFloat()
        val fullPx = containerPx * FULL_FRACTION
        val halfPx = max(containerPx * HALF_FRACTION, with(density) { MIN_HALF_WIDTH.toPx() }).coerceAtMost(fullPx)
        val anchors = remember(halfPx, fullPx) {
            DraggableAnchors {
                SidePanelValue.Closed at 0f
                SidePanelValue.Half at halfPx
                SidePanelValue.Full at fullPx
            }
        }
        LaunchedEffect(anchors) { state.draggable.updateAnchors(anchors) }
        val showPanel by remember { derivedStateOf { state.visibleWidthPx > 0f } }

        if (showPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (state.visibleWidthPx / halfPx).coerceIn(0f, 1f) }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                    .pointerInput(state) { detectTapGestures { scope.launch { state.close() } } },
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
                // Below Half the panel keeps its Half width and slides, so closing doesn't re-layout the list.
                modifier = Modifier.layout { measurable, constraints ->
                    val visible = state.visibleWidthPx
                    val width = max(visible, halfPx).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(width, constraints.maxHeight) {
                        placeable.place(constraints.maxWidth - visible.roundToInt(), 0)
                    }
                },
            ) {
                // At Half the panel is narrow; a little air on its left edge keeps it from feeling cramped.
                val startInset by animateDpAsState(
                    targetValue = if (state.draggable.targetValue == SidePanelValue.Half) HALF_START_INSET else 0.dp,
                    label = "panelStartInset",
                )
                Box(modifier = Modifier.padding(start = startInset)) { content() }
            }
        }
        if (handleVisible || showPanel) {
            Handle(
                state = state,
                onClick = { scope.launch { state.toggle() } },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(-state.visibleWidthPx.roundToInt(), 0) },
            )
        }
    }
}

@Composable
private fun Handle(state: SidePanelState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.library_handle)
    Surface(
        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .size(HANDLE_WIDTH, HANDLE_HEIGHT)
            .systemGestureExclusion()
            .anchoredDraggable(
                state = state.draggable,
                orientation = Orientation.Horizontal,
                // Offsets grow as the panel opens, which is a drag to the left.
                reverseDirection = true,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(state.draggable),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private const val HALF_FRACTION = 0.55f
private const val FULL_FRACTION = 0.92f
private const val SCRIM_ALPHA = 0.5f
private val MIN_HALF_WIDTH = 260.dp
private val HANDLE_WIDTH = 24.dp
private val HANDLE_HEIGHT = 96.dp
private val HALF_START_INSET = 12.dp

package io.github.z0orb.akagi.feature.library

import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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

    private val anchorPositions: Map<SidePanelValue, Float>
        get() = SidePanelValue.entries.associateWith { draggable.anchors.positionOf(it) }

    /** The offsets a swipe starting now may move the panel through: one step, [opening] or closing. */
    internal fun swipeRange(opening: Boolean): ClosedFloatingPointRange<Float> =
        PanelSettle.stepRange(anchorPositions, draggable.settledValue, opening)

    /** Moves the panel by [delta] offset pixels within [range]; returns the part used. */
    internal fun dragWithin(delta: Float, range: ClosedFloatingPointRange<Float>): Float =
        draggable.dispatchRawDelta((visibleWidthPx + delta).coerceIn(range) - visibleWidthPx)

    /**
     * Ends a drag that came from the content at an anchor inside [range]; [velocity] is in offset pixels per second
     * (opening is positive).
     */
    internal suspend fun settle(velocity: Float, range: ClosedFloatingPointRange<Float>, flingThresholdPx: Float) {
        val anchors = anchorPositions.filterValues { it in range }
        draggable.animateTo(PanelSettle.target(anchors, visibleWidthPx, velocity, flingThresholdPx), snapSpec)
    }

    companion object {
        // Critically damped and stiff: taps and flings settle in about 170 ms without overshoot (P7.9).
        internal val snapSpec = spring<Float>(stiffness = Spring.StiffnessMedium)

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
 * Right-edge library panel. From outside it moves only by its handle, which is excluded from system gestures, so
 * neither the back swipe nor YouTube's own gestures can trigger it (D-013). Tapping the scrim closes it. Inside,
 * a horizontal swipe the content can't use (the tab pager at its first or last page) moves the panel (D-059).
 *
 * [content] gets the content's width at Half, the narrowest open panel: parts that must not re-lay out on every
 * frame of a drag (text auto-sizing) measure against it instead of the current width (P7.9).
 */
@Composable
fun SidePanel(
    state: SidePanelState,
    handleVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (narrowWidth: Dp) -> Unit,
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
        // Composed shortly after start and kept while closed (measured but not placed, so never drawn): composing
        // the whole library on the first frame of a drag dropped frames just as the finger started moving (P7.9).
        var keepComposed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(PREWARM_DELAY_MS)
            keepComposed = true
        }

        if (showPanel) {
            val scrim = MaterialTheme.colorScheme.scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Fades through the colour's alpha: a graphicsLayer alpha below 1 renders the whole screen
                    // off-screen first, about 10 ms of render-thread time per frame on the test phone (P7.9).
                    .drawBehind {
                        drawRect(scrim, alpha = SCRIM_ALPHA * (state.visibleWidthPx / halfPx).coerceIn(0f, 1f))
                    }
                    .pointerInput(state) { detectTapGestures { scope.launch { state.close() } } },
            )
        }
        if (showPanel || keepComposed) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .then(if (showPanel) Modifier else Modifier.clearAndSetSemantics {})
                    // Below Half the panel keeps its Half width and slides, so closing doesn't re-layout the list.
                    .layout { measurable, constraints ->
                        val visible = state.visibleWidthPx
                        val width = max(visible, halfPx).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                        layout(width, constraints.maxHeight) {
                            if (visible > 0f) placeable.place(constraints.maxWidth - visible.roundToInt(), 0)
                        }
                    },
            ) {
                // A little air on the left edge keeps the narrow Half panel from feeling cramped. It is constant,
                // so it never re-lays out the content mid-drag.
                val narrowWidth = with(density) { halfPx.toDp() } - START_INSET
                val flingThresholdPx = with(density) { FLING_THRESHOLD.toPx() }
                val leftoverSwipes = remember(state, flingThresholdPx) { LeftoverSwipes(state, flingThresholdPx) }
                Box(modifier = Modifier.padding(start = START_INSET).nestedScroll(leftoverSwipes)) {
                    content(narrowWidth)
                }
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

/**
 * Hands the content's unused horizontal swipes to the panel (D-059): past Downloads a swipe right narrows it one
 * step (Full → Half, Half → Closed), past Playlists a swipe left widens it. Once a swipe has moved the panel it
 * keeps the rest of that swipe, so turning back doesn't switch tabs halfway.
 */
private class LeftoverSwipes(private val state: SidePanelState, private val flingThresholdPx: Float) :
    NestedScrollConnection {
    /** Set while the current swipe is moving the panel. */
    private var range: ClosedFloatingPointRange<Float>? = null

    // Scroll deltas follow the finger (right is positive); the panel's offset grows as it opens, to the left.
    private fun movePanel(fingerDx: Float, within: ClosedFloatingPointRange<Float>): Float =
        -state.dragWithin(-fingerDx, within)

    // While the panel has this swipe, all of its horizontal motion is spent here, even past the step's end.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val within = range ?: return Offset.Zero
        movePanel(available.x, within)
        return Offset(available.x, 0f)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
        val within = range ?: state.swipeRange(opening = available.x < 0f)
        val used = movePanel(available.x, within)
        if (used != 0f) range = within
        return Offset(used, 0f)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val within = range ?: return Velocity.Zero
        range = null
        state.settle(velocity = -available.x, range = within, flingThresholdPx = flingThresholdPx)
        return Velocity(available.x, 0f)
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
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                    state.draggable,
                    animationSpec = SidePanelState.snapSpec,
                ),
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
private val START_INSET = 12.dp
private const val PREWARM_DELAY_MS = 1_000L

// The same minimum as a fling on the handle (anchoredDraggable's own threshold), per second.
private val FLING_THRESHOLD = 125.dp

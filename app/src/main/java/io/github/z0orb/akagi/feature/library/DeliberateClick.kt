package io.github.z0orb.akagi.feature.library

import android.os.SystemClock
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlin.math.max

/** Remembers when the panel's lists and pager last scrolled, so rows can ignore the tail of a swipe. */
@Stable
class ScrollGuard {
    private val active = mutableSetOf<Any>()
    private var lastEndedAt: Long? = null

    val scrolling: Boolean get() = active.isNotEmpty()

    fun msSinceScrollEnded(now: Long): Long? = lastEndedAt?.let { now - it }

    internal fun update(source: Any, inProgress: Boolean, now: Long) {
        if (inProgress) {
            active += source
        } else if (active.remove(source)) {
            lastEndedAt = now
        }
    }
}

@Composable
internal fun rememberScrollGuard(): ScrollGuard = remember { ScrollGuard() }

/** Reports [state]'s scrolling to [guard] while this is composed. */
@Composable
internal fun TrackScroll(guard: ScrollGuard, state: ScrollableState) {
    LaunchedEffect(guard, state) {
        try {
            snapshotFlow { state.isScrollInProgress }.collect { guard.update(state, it, SystemClock.uptimeMillis()) }
        } finally {
            guard.update(state, false, SystemClock.uptimeMillis())
        }
    }
}

/**
 * Like `clickable`, but only for touches [TapRules] calls deliberate. The ripple plays when the tap is accepted,
 * so a touch that turns into a swipe never flashes the row. Accessibility clicks are unaffected.
 */
internal fun Modifier.deliberateClickable(
    guard: ScrollGuard,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = indication(interactionSource, ripple())
    .semantics {
        role = Role.Button
        onClick {
            onClick()
            true
        }
    }
    .pointerInput(guard, onClick) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val scrollingAtDown = guard.scrolling
            val sinceScroll = guard.msSinceScrollEnded(down.uptimeMillis)
            var travel = 0f
            var up: PointerInputChange? = null
            while (true) {
                val event = awaitPointerEvent()
                // A second finger means a gesture, not a tap.
                val change = event.changes.singleOrNull()?.takeIf { it.id == down.id } ?: break
                travel = max(travel, (change.position - down.position).getDistance())
                if (change.isConsumed) break
                if (!change.pressed) {
                    up = change
                    break
                }
            }
            val accepted = up?.takeIf {
                TapRules.isDeliberate(
                    pressMs = it.uptimeMillis - down.uptimeMillis,
                    travelPx = travel,
                    touchSlopPx = viewConfiguration.touchSlop,
                    scrolling = scrollingAtDown || guard.scrolling,
                    msSinceScrollEnded = sinceScroll,
                )
            }
            if (accepted != null) {
                accepted.consume()
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                interactionSource.tryEmit(PressInteraction.Release(press))
                onClick()
            }
        }
    }

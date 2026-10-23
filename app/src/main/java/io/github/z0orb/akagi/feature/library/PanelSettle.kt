package io.github.z0orb.akagi.feature.library

import kotlin.math.abs

/**
 * How a swipe inside the side panel moves it (D-059). One swipe moves the panel at most one step from where it
 * rested, in the direction it first moved it (Full → Half, Half → Closed or Full), and its release follows the
 * handle's rule: a flick goes to the next anchor in its direction, a slow release to the nearest one.
 */
object PanelSettle {
    /** The positions a swipe may move the panel through from [start], [opening] (higher positions) or closing. */
    fun <T> stepRange(anchors: Map<T, Float>, start: T, opening: Boolean): ClosedFloatingPointRange<Float> {
        val positions = anchors.values.sorted()
        val index = positions.indexOf(anchors.getValue(start))
        return if (opening) {
            positions[index]..positions[(index + 1).coerceAtMost(positions.lastIndex)]
        } else {
            positions[(index - 1).coerceAtLeast(0)]..positions[index]
        }
    }

    /** [anchors] maps each panel value to its position; [velocity] is in position units per second. */
    fun <T> target(anchors: Map<T, Float>, position: Float, velocity: Float, flingThreshold: Float): T {
        val nearest = anchors.minBy { abs(it.value - position) }.key
        if (abs(velocity) < flingThreshold) return nearest
        val ahead = anchors.filterValues { if (velocity > 0) it > position else it < position }
        return ahead.minByOrNull { abs(it.value - position) }?.key ?: nearest
    }
}

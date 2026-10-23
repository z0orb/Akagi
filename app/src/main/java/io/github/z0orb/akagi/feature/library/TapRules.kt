package io.github.z0orb.akagi.feature.library

/**
 * Decides whether a touch on a library row was meant as a tap. Rows sit inside a vertical list and a horizontal
 * pager, so many touches are the start or the end of a swipe; playing a track by accident is worse than asking
 * for one more tap.
 */
object TapRules {
    /** Below the system's scroll threshold, so a tap is rejected before any scroll would have started. */
    const val TRAVEL_FRACTION_OF_SLOP = 0.5f

    /** Longer presses are holds, not taps. */
    const val MAX_PRESS_MS = 400L

    /** A touch landing right after a scroll or fling is usually the tail of that swipe. */
    const val SCROLL_COOLDOWN_MS = 250L

    fun isDeliberate(
        pressMs: Long,
        travelPx: Float,
        touchSlopPx: Float,
        scrolling: Boolean,
        msSinceScrollEnded: Long?,
    ): Boolean = !scrolling &&
        pressMs <= MAX_PRESS_MS &&
        travelPx <= touchSlopPx * TRAVEL_FRACTION_OF_SLOP &&
        (msSinceScrollEnded == null || msSinceScrollEnded >= SCROLL_COOLDOWN_MS)
}

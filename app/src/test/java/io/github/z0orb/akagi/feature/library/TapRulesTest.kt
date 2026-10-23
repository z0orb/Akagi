package io.github.z0orb.akagi.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapRulesTest {

    private fun tap(
        pressMs: Long = 80,
        travelPx: Float = 2f,
        scrolling: Boolean = false,
        msSinceScrollEnded: Long? = null,
    ) = TapRules.isDeliberate(pressMs, travelPx, touchSlopPx = 20f, scrolling, msSinceScrollEnded)

    @Test
    fun `a short still tap counts`() = assertTrue(tap())

    @Test
    fun `finger jitter under half the slop still counts`() = assertTrue(tap(travelPx = 10f))

    @Test
    fun `movement past half the slop is the start of a swipe`() = assertFalse(tap(travelPx = 10.5f))

    @Test
    fun `a long hold is not a tap`() = assertFalse(tap(pressMs = 401))

    @Test
    fun `touches during a scroll never count`() = assertFalse(tap(scrolling = true))

    @Test
    fun `a touch right after a scroll is ignored`() = assertFalse(tap(msSinceScrollEnded = 120))

    @Test
    fun `after the cooldown taps work again`() = assertTrue(tap(msSinceScrollEnded = 250))
}

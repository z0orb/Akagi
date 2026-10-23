package io.github.z0orb.akagi.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSettleTest {

    private val anchors = mapOf(
        SidePanelValue.Closed to 0f,
        SidePanelValue.Half to 600f,
        SidePanelValue.Full to 1000f,
    )

    private fun settle(position: Float, velocity: Float) =
        PanelSettle.target(anchors, position, velocity, flingThreshold = 300f)

    @Test
    fun `a flick right from Full stops at Half`() = assertEquals(SidePanelValue.Half, settle(950f, -2000f))

    @Test
    fun `a flick right from Half closes`() = assertEquals(SidePanelValue.Closed, settle(560f, -2000f))

    @Test
    fun `a flick left from Half opens Full`() = assertEquals(SidePanelValue.Full, settle(640f, 2000f))

    @Test
    fun `a flick past the widest anchor stays there`() = assertEquals(SidePanelValue.Full, settle(1000f, 2000f))

    @Test
    fun `a slow release goes back to the nearest anchor`() = assertEquals(SidePanelValue.Half, settle(700f, -100f))

    @Test
    fun `a slow release past halfway goes on`() = assertEquals(SidePanelValue.Closed, settle(250f, 0f))

    @Test
    fun `a flick just under the threshold counts as slow`() = assertEquals(SidePanelValue.Full, settle(950f, -299f))

    @Test
    fun `a flick that already reached the end of its step stays there`() {
        val oneStep = anchors.filterValues {
            it in PanelSettle.stepRange(anchors, SidePanelValue.Full, opening = false)
        }
        assertEquals(SidePanelValue.Half, PanelSettle.target(oneStep, 600f, -2000f, flingThreshold = 300f))
    }

    @Test
    fun `narrowing from Full reaches Half at most`() =
        assertEquals(600f..1000f, PanelSettle.stepRange(anchors, SidePanelValue.Full, opening = false))

    @Test
    fun `narrowing from Half can close it`() =
        assertEquals(0f..600f, PanelSettle.stepRange(anchors, SidePanelValue.Half, opening = false))

    @Test
    fun `widening from Half reaches Full`() =
        assertEquals(600f..1000f, PanelSettle.stepRange(anchors, SidePanelValue.Half, opening = true))

    @Test
    fun `widening from Full goes nowhere`() =
        assertEquals(1000f..1000f, PanelSettle.stepRange(anchors, SidePanelValue.Full, opening = true))
}

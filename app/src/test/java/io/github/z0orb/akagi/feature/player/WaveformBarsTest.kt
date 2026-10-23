package io.github.z0orb.akagi.feature.player

import io.github.z0orb.akagi.core.common.DurationFormatter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformBarsTest {

    @Test
    fun `fewer bars than peaks keep the loudest peak of each group`() {
        val peaks = byteArrayOf(0, 51, 0, 0, 255.toByte(), 0, 102, 0)
        assertArrayEquals(floatArrayOf(0.2f, 0f, 1f, 0.4f), WaveformBars.resample(peaks, 4), 0.001f)
    }

    @Test
    fun `more bars than peaks repeat peaks`() {
        val peaks = byteArrayOf(0, 255.toByte())
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), WaveformBars.resample(peaks, 4), 0.001f)
    }

    @Test
    fun `peaks are unsigned`() {
        assertEquals(200 / 255f, WaveformBars.resample(byteArrayOf(200.toByte()), 1)[0], 0.001f)
    }

    @Test
    fun `no room or no peaks gives flat bars`() {
        assertEquals(0, WaveformBars.resample(byteArrayOf(1), 0).size)
        assertArrayEquals(floatArrayOf(0f, 0f), WaveformBars.resample(byteArrayOf(), 2), 0f)
    }

    @Test
    fun `durations format as m-ss, or h-mm-ss past an hour`() {
        assertEquals("0:00", DurationFormatter.format(-5))
        assertEquals("0:09", DurationFormatter.format(9_999))
        assertEquals("4:05", DurationFormatter.format(245_000))
        assertEquals("1:02:03", DurationFormatter.format(3_723_000))
    }
}

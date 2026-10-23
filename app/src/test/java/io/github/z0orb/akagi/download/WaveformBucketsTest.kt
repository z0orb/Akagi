package io.github.z0orb.akagi.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformBucketsTest {

    private fun ByteArray.level(i: Int) = this[i].toInt() and 0xFF

    @Test
    fun `nothing decoded means no waveform`() {
        assertNull(WaveformBuckets(1_000_000).toPeaks())
    }

    @Test
    fun `loudest bucket is 255 and louder buckets are higher`() {
        val buckets = WaveformBuckets(durationUs = 256_000)
        // One 1 ms bucket each: silence, quiet, loud.
        buckets.add(timeUs = 500, sumOfSquares = 0.0, count = 100)
        buckets.add(timeUs = 1_500, sumOfSquares = 100 * 0.1 * 0.1, count = 100)
        buckets.add(timeUs = 2_500, sumOfSquares = 100 * 0.9 * 0.9, count = 100)
        val peaks = requireNotNull(buckets.toPeaks())
        assertEquals(WaveformBuckets.BUCKETS, peaks.size)
        assertEquals(0, peaks.level(0))
        assertEquals(255, peaks.level(2))
        assertTrue(peaks.level(1) in 1 until 255)
        // The square root lifts quiet passages: 0.1/0.9 of the RMS shows as about a third.
        assertEquals(85, peaks.level(1))
    }

    @Test
    fun `times past the end land in the last bucket`() {
        val buckets = WaveformBuckets(durationUs = 1_000)
        buckets.add(timeUs = 5_000, sumOfSquares = 0.25, count = 1)
        assertEquals(255, requireNotNull(buckets.toPeaks()).level(255))
    }
}

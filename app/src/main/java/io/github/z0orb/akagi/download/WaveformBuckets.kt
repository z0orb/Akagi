package io.github.z0orb.akagi.download

import kotlin.math.sqrt

/**
 * Accumulates PCM samples into [BUCKETS] RMS buckets by playback time, then turns them into the
 * 256 unsigned bytes stored per track (D-007). Pure, so it is unit-tested without a decoder.
 */
class WaveformBuckets(private val durationUs: Long) {
    private val sumSquares = DoubleArray(BUCKETS)
    private val counts = LongArray(BUCKETS)

    /**
     * Adds [count] samples (in -1..1, squared and summed into [sumOfSquares]) played at [timeUs]. Every
     * channel counts, which averages them into mono.
     */
    fun add(timeUs: Long, sumOfSquares: Double, count: Int) {
        val bucket = (timeUs * BUCKETS / durationUs.coerceAtLeast(1)).toInt().coerceIn(0, BUCKETS - 1)
        sumSquares[bucket] += sumOfSquares
        counts[bucket] += count
    }

    /** Null when nothing was decoded, so the player falls back to a plain bar. */
    fun toPeaks(): ByteArray? {
        if (counts.all { it == 0L }) return null
        // The square root of each RMS compresses the range, so quiet passages still show as bars.
        val levels = DoubleArray(BUCKETS) { i -> if (counts[i] == 0L) 0.0 else sqrt(sqrt(sumSquares[i] / counts[i])) }
        val max = levels.max().takeIf { it > 0 } ?: return ByteArray(BUCKETS)
        return ByteArray(BUCKETS) { i -> (levels[i] / max * MAX_LEVEL).toInt().toByte() }
    }

    companion object {
        const val BUCKETS = 256
        private const val MAX_LEVEL = 255
    }
}

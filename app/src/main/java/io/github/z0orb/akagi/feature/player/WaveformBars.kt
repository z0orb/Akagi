package io.github.z0orb.akagi.feature.player

object WaveformBars {
    /**
     * Fits the stored peaks (unsigned bytes, D-007) to [bars] bars as heights in 0..1. Each bar takes the
     * loudest peak it covers, so short loud moments stay visible when many peaks share one bar.
     */
    fun resample(peaks: ByteArray, bars: Int): FloatArray {
        if (bars <= 0 || peaks.isEmpty()) return FloatArray(bars.coerceAtLeast(0))
        return FloatArray(bars) { bar ->
            val from = bar * peaks.size / bars
            val to = maxOf(from + 1, (bar + 1) * peaks.size / bars)
            var loudest = 0
            for (i in from until to) loudest = maxOf(loudest, peaks[i].toInt() and 0xFF)
            loudest / 255f
        }
    }
}

package io.github.z0orb.akagi.core.common

/** Player times: "4:05", or "1:02:03" past an hour. Negative or unknown durations show as "0:00". */
object DurationFormatter {
    fun format(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}

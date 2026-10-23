package io.github.z0orb.akagi.core.common

import java.util.Locale

/** "3.4 MB": SI megabytes with one decimal, as Android's own file UIs show them (D-018). */
object SizeFormatter {
    fun megabytes(bytes: Long, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f MB", bytes / 1_000_000.0)
}

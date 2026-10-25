package io.github.z0orb.akagi.playback

import java.text.NumberFormat
import java.util.Locale

/** Labels for output formats: "44.1 kHz · 24-bit", and DAC summaries such as "44.1–384 kHz · 16/24/32-bit". */
object OutputFormat {
    fun rate(rateHz: Int, locale: Locale = Locale.getDefault()): String = "${khz(rateHz, locale)} kHz"

    fun depth(encoding: PcmEncoding): String = when (encoding) {
        PcmEncoding.FLOAT -> "32-bit float"
        else -> "${encoding.bits}-bit"
    }

    fun format(format: PcmFormat, locale: Locale = Locale.getDefault()): String =
        "${rate(format.rateHz, locale)} · ${depth(format.encoding)}"

    /** Null when the DAC reports nothing usable. */
    fun capabilities(caps: DacCapabilities, locale: Locale = Locale.getDefault()): String? {
        val rates = caps.ratesHz.filter { it > 0 }.distinct().sorted()
        val rateRange = when {
            rates.isEmpty() -> null
            rates.size == 1 -> rate(rates.single(), locale)
            else -> "${khz(rates.first(), locale)}–${khz(rates.last(), locale)} kHz"
        }
        val bits = caps.encodings.filter { it != PcmEncoding.FLOAT }.map { it.bits }.distinct().sorted()
        val depths = bits.takeIf { it.isNotEmpty() }?.joinToString("/", postfix = "-bit")
        val float = if (PcmEncoding.FLOAT in caps.encodings) depth(PcmEncoding.FLOAT) else null
        return listOfNotNull(rateRange, depths, float).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun khz(rateHz: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(rateHz / 1000.0)
}

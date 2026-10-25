package io.github.z0orb.akagi.playback

/** Where media audio goes. [OTHER] is an external output Akagi doesn't name, such as HDMI or a dock. */
enum class OutputKind { SPEAKER, WIRED, BLUETOOTH, USB_DAC, OTHER }

enum class PcmEncoding(val bits: Int) { PCM_16(16), PCM_24(24), PCM_32(32), FLOAT(32) }

data class PcmFormat(val rateHz: Int, val encoding: PcmEncoding, val channels: Int)

/**
 * What a DAC reports. [mixerFormats] are the formats Android can open its mixer at (API 34+),
 * empty when unknown; [ratesHz] and [encodings] cover both sources for display.
 */
data class DacCapabilities(
    val ratesHz: List<Int> = emptyList(),
    val encodings: Set<PcmEncoding> = emptySet(),
    val mixerFormats: List<PcmFormat> = emptyList(),
)

/**
 * The current media route (PLAN §7.19). [caps] describe the connected USB DAC; [sent] is what
 * Akagi's AudioTrack sends; [dacFormat] is the mixer format Akagi set, null when Android picks it.
 */
data class AudioRoute(
    val kind: OutputKind = OutputKind.SPEAKER,
    val name: String? = null,
    /** A USB DAC is plugged in, whether or not media plays on it. */
    val dacConnected: Boolean = false,
    val caps: DacCapabilities? = null,
    val sent: PcmFormat? = null,
    val dacFormat: PcmFormat? = null,
)

/**
 * Whether media has fallen back to the phone speaker after an unplug. On API 33+ the media route is
 * known ([route]); before that, any external output still connected means it hasn't (B-01).
 */
fun mediaOnSpeaker(route: OutputKind?, connected: Collection<OutputKind>): Boolean =
    if (route != null) route == OutputKind.SPEAKER else connected.all { it == OutputKind.SPEAKER }

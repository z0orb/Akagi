package io.github.z0orb.akagi.feature.player

import androidx.compose.runtime.Immutable
import io.github.z0orb.akagi.playback.NowPlaying
import io.github.z0orb.akagi.playback.PcmFormat
import io.github.z0orb.akagi.playback.RepeatMode

@Immutable
data class PlayerUiState(
    /** Null hides the player: nothing is queued. */
    val track: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val playingFrom: PlayingFrom? = null,
    val waveform: Waveform? = null,
    /** Set only while a USB DAC is the route (PLAN §7.19). */
    val usbDac: UsbDacOutput? = null,
)

/** [format] is what the DAC runs at, or null when Android picks it. */
@Immutable
data class UsbDacOutput(val format: PcmFormat?)

@Immutable
sealed interface PlayingFrom {
    data object Downloads : PlayingFrom

    data class Playlist(val name: String) : PlayingFrom
}

/** Wraps the peaks so the state compares by content and Compose can treat it as stable. */
@Immutable
class Waveform(val peaks: ByteArray) {
    override fun equals(other: Any?): Boolean = other is Waveform && other.peaks.contentEquals(peaks)

    override fun hashCode(): Int = peaks.contentHashCode()
}

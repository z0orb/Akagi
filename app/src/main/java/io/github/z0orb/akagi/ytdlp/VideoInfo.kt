package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
/** What Akagi needs from yt-dlp's `-J` output. */
data class VideoInfo(
    val videoId: String,
    val title: String,
    val durationSeconds: Double?,
    val isLiveOrUpcoming: Boolean,
    /** Audio-only Opus/M4A candidates, already filtered (no DRC, original language only). */
    val formats: List<AudioFormat>,
)

data class AudioFormat(
    val formatId: String,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    /** Exact or yt-dlp's approximation; null when yt-dlp knows neither. */
    val sizeBytes: Long?,
)

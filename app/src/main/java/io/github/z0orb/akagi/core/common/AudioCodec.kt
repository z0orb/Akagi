package io.github.z0orb.akagi.core.common

/** The audio families Akagi saves (PLAN §7.4). [extension] is also yt-dlp's `--audio-format` value. */
enum class AudioCodec(val extension: String) {
    OPUS("opus"),
    M4A("m4a"),
    MP3("mp3"),
}

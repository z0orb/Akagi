package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec

/** The yt-dlp command lines Akagi runs (PLAN §7.5), as plain argument lists so they are unit-tested. */
object YtDlpOptions {
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /**
     * youtubedl-android appends `--js-runtimes quickjs:<libqjs.so>` and `--ffmpeg-location` itself, and
     * `--no-cache-dir` unless a cache dir is given. The cache plus Akagi's plugin keeps the preprocessed
     * YouTube player between runs, which cuts a fetch from ~40 s to ~13 s on the test phone (risk 2, D-033).
     */
    fun base(cacheDir: String, pluginDir: String): List<String> = listOf(
        "--no-playlist",
        "--no-mtime",
        "--cache-dir",
        cacheDir,
        "--plugin-dirs",
        pluginDir,
        "--socket-timeout",
        "20",
        "--retries",
        "3",
    )

    fun cookies(path: String): List<String> = listOf("--cookies", path)

    fun info(): List<String> = listOf("-J")

    /** Downloads from a saved `-J` output instead of the watch page. */
    fun loadInfo(path: String): List<String> = listOf("--load-info-json", path)

    fun download(formatId: String, codec: AudioCodec, bitrateKbps: Int, outputTemplate: String): List<String> =
        buildList {
            addAll(listOf("-f", formatId, "-x", "--audio-format", codec.extension))
            // Opus and M4A are remuxed as-is; only the MP3 fallback is encoded, at the chosen bitrate.
            if (codec == AudioCodec.MP3) addAll(listOf("--audio-quality", "${bitrateKbps}K"))
            addAll(listOf("--embed-metadata", "--newline", "-o", outputTemplate))
        }

    private val speedPattern = Regex("""\bat\s+(\S+/s)""")

    /** yt-dlp's progress line, e.g. "[download]  42.0% of 3.40MiB at 1.20MiB/s ETA 00:02", reduced to the speed. */
    fun speedOf(line: String): String? =
        speedPattern.find(line)?.groupValues?.get(1)?.takeUnless { it.startsWith("Unknown") }
}

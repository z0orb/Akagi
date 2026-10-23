package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
import java.io.File

/**
 * yt-dlp behind an interface (PLAN §5), so everything above it can be tested with a fake and the
 * packaging can be swapped if youtubedl-android stalls (risk 4). Failures throw
 * [io.github.z0orb.akagi.core.common.AppException].
 */
interface YtDlpEngine {
    suspend fun fetchInfo(videoId: String): VideoInfo

    /**
     * Downloads [formatId] as [codec] into [outputDir] and returns the finished file. Cancelling the
     * coroutine kills the yt-dlp process.
     */
    suspend fun download(
        processId: String,
        videoId: String,
        formatId: String,
        codec: AudioCodec,
        bitrateKbps: Int,
        outputDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): File

    /** The installed yt-dlp version, e.g. "2025.11.12"; null before the first init finishes. */
    suspend fun version(): String?

    /** Replaces yt-dlp with the newest build of [channel]. Waits for running fetches and downloads first. */
    suspend fun update(channel: UpdateChannel): UpdateResult
}

enum class UpdateChannel { STABLE, NIGHTLY }

sealed interface UpdateResult {
    data class Updated(val version: String?) : UpdateResult

    data object AlreadyUpToDate : UpdateResult
}

/** Live numbers for one running download. */
data class DownloadProgress(val fraction: Float, val etaSeconds: Long?, val speed: String?)

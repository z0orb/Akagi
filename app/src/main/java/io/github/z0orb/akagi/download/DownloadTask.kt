package io.github.z0orb.akagi.download

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.data.db.DownloadState

/** What the user asked to download: the video and the option picked in the format sheet. */
data class DownloadRequest(
    val videoId: String,
    val title: String,
    val formatId: String,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    /** Set by the "Download again" flow: the track whose file this download replaces. */
    val replaceTrackId: Long? = null,
)

/** A download as the UI and notifications see it. */
data class DownloadTask(val id: Long, val request: DownloadRequest, val state: DownloadState, val error: AppError?)

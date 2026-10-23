package io.github.z0orb.akagi.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

/** The media ID is the track's row ID, so the player and the library can find each other's items. */
internal fun QueueItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(trackId.toString())
    .setUri(Uri.parse(contentUri))
    .setMediaMetadata(
        // Title and artwork only: no artist or album (R9).
        MediaMetadata.Builder()
            .setTitle(title)
            .setDurationMs(durationMs)
            .setArtworkUri(thumbnailPath?.let { Uri.fromFile(File(it)) })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()

internal val MediaItem.trackId: Long? get() = mediaId.toLongOrNull()

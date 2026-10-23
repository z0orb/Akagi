package io.github.z0orb.akagi.playback

import io.github.z0orb.akagi.data.db.TrackEntity
import kotlin.random.Random

/** Where a queue came from; the expanded player shows it as "Playing from …" (PLAN §7.9). */
sealed interface QueueSource {
    data object Downloads : QueueSource

    data class Playlist(val id: Long) : QueueSource
}

data class QueueItem(
    val trackId: Long,
    val title: String,
    val contentUri: String,
    val thumbnailPath: String?,
    val durationMs: Long,
)

/** A snapshot of tracks to play (R15): later library changes don't touch a running queue. */
data class PlayQueue(
    val items: List<QueueItem>,
    val startIndex: Int,
    val source: QueueSource,
    /** Turns shuffle on or off for this queue; null keeps whatever the player has. */
    val shuffle: Boolean? = null,
)

/** Builds queues from the library's lists. Missing files are left out, so playback never stalls on them. */
object QueueFactory {

    /** The whole Downloads list in display order (newest first), starting at the tapped track. */
    fun fromDownloads(tracks: List<TrackEntity>, startTrackId: Long): PlayQueue? =
        queueOf(tracks, QueueSource.Downloads) { items -> items.indexOfFirst { it.trackId == startTrackId } }

    /** A row tap in a playlist: only that playlist, in its order. */
    fun fromPlaylist(playlistId: Long, tracks: List<TrackEntity>, startTrackId: Long): PlayQueue? =
        queueOf(tracks, QueueSource.Playlist(playlistId)) { items -> items.indexOfFirst { it.trackId == startTrackId } }

    /** The playlist header's Play (from the top, in order) or Shuffle (a random first track, shuffled). */
    fun playAll(playlistId: Long, tracks: List<TrackEntity>, shuffle: Boolean, random: Random = Random): PlayQueue? =
        queueOf(tracks, QueueSource.Playlist(playlistId), shuffle) { items ->
            if (shuffle) random.nextInt(items.size) else 0
        }

    private fun queueOf(
        tracks: List<TrackEntity>,
        source: QueueSource,
        shuffle: Boolean? = null,
        startIndexIn: (List<QueueItem>) -> Int,
    ): PlayQueue? {
        val items = tracks.filterNot { it.missing }.map { it.toQueueItem() }
        if (items.isEmpty()) return null
        val start = startIndexIn(items).takeIf { it >= 0 } ?: return null
        return PlayQueue(items, start, source, shuffle)
    }

    private fun TrackEntity.toQueueItem() = QueueItem(id, title, contentUri, thumbnailPath, durationMs)
}

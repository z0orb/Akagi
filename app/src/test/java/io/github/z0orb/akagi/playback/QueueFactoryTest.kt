package io.github.z0orb.akagi.playback

import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.data.db.TrackEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueFactoryTest {

    private fun track(id: Long, missing: Boolean = false) = TrackEntity(
        id = id,
        videoId = "video$id",
        title = "Track $id",
        durationMs = 1_000L * id,
        sizeBytes = 1,
        codec = AudioCodec.OPUS,
        bitrateKbps = 128,
        contentUri = "content://media/$id",
        fileName = "$id.opus",
        thumbnailPath = "/thumbs/$id.jpg",
        downloadedAt = id,
        missing = missing,
    )

    @Test
    fun `downloads queue is the whole list in order, starting at the tapped track`() {
        val queue = QueueFactory.fromDownloads(listOf(track(3), track(2), track(1)), startTrackId = 2)
        assertEquals(listOf(3L, 2L, 1L), queue?.items?.map { it.trackId })
        assertEquals(1, queue?.startIndex)
        assertEquals(QueueSource.Downloads, queue?.source)
        assertNull(queue?.shuffle)
    }

    @Test
    fun `queue items carry what the player shows`() {
        val item = QueueFactory.fromDownloads(listOf(track(7)), startTrackId = 7)?.items?.single()
        assertEquals(QueueItem(7, "Track 7", "content://media/7", "/thumbs/7.jpg", 7_000), item)
    }

    @Test
    fun `missing files are skipped and the start index follows`() {
        val queue = QueueFactory.fromDownloads(listOf(track(3, missing = true), track(2), track(1)), startTrackId = 1)
        assertEquals(listOf(2L, 1L), queue?.items?.map { it.trackId })
        assertEquals(1, queue?.startIndex)
    }

    @Test
    fun `starting at a missing or unknown track gives no queue`() {
        assertNull(QueueFactory.fromDownloads(listOf(track(1, missing = true), track(2)), startTrackId = 1))
        assertNull(QueueFactory.fromDownloads(listOf(track(2)), startTrackId = 9))
    }

    @Test
    fun `playlist queue holds only that playlist`() {
        val queue = QueueFactory.fromPlaylist(5, listOf(track(1), track(4)), startTrackId = 4)
        assertEquals(listOf(1L, 4L), queue?.items?.map { it.trackId })
        assertEquals(1, queue?.startIndex)
        assertEquals(QueueSource.Playlist(5), queue?.source)
    }

    @Test
    fun `play all starts at the top with shuffle off`() {
        val queue = QueueFactory.playAll(5, listOf(track(1, missing = true), track(2), track(3)), shuffle = false)
        assertEquals(0, queue?.startIndex)
        assertEquals(false, queue?.shuffle)
        assertEquals(listOf(2L, 3L), queue?.items?.map { it.trackId })
    }

    @Test
    fun `shuffle all starts at a random playable track with shuffle on`() {
        val tracks = (1L..10L).map { track(it) }
        val starts = (0 until 50).mapNotNull {
            QueueFactory.playAll(5, tracks, shuffle = true, random = Random(it))?.startIndex
        }.toSet()
        assert(starts.size > 1) { "start index never changed: $starts" }
        assertEquals(true, QueueFactory.playAll(5, tracks, shuffle = true)?.shuffle)
    }

    @Test
    fun `a playlist with only missing files gives no queue`() {
        assertNull(QueueFactory.playAll(5, listOf(track(1, missing = true)), shuffle = true))
        assertNull(QueueFactory.playAll(5, emptyList(), shuffle = false))
    }
}

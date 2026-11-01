package io.github.z0orb.akagi.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.z0orb.akagi.core.common.AudioCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DAO behaviour on a real in-memory SQLite: cascades, multi-membership and reorder (PLAN P4.8, P3.6). */
@RunWith(RobolectricTestRunner::class)
class AkagiDatabaseTest {

    private lateinit var db: AkagiDatabase
    private lateinit var tracks: TrackDao
    private lateinit var playlists: PlaylistDao
    private lateinit var tasks: DownloadTaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AkagiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tracks = db.trackDao()
        playlists = db.playlistDao()
        tasks = db.downloadTaskDao()
    }

    @After
    fun tearDown() = db.close()

    private fun track(videoId: String, downloadedAt: Long = 0, thumbnail: String? = "/thumbs/$videoId.jpg") =
        TrackEntity(
            videoId = videoId,
            title = "Title $videoId",
            durationMs = 1_000,
            sizeBytes = 3_000_000,
            codec = AudioCodec.OPUS,
            bitrateKbps = 128,
            contentUri = "content://media/external_primary/audio/media/1",
            fileName = "$videoId.opus",
            thumbnailPath = thumbnail,
            downloadedAt = downloadedAt,
        )

    private suspend fun playlist(name: String) =
        playlists.insert(PlaylistEntity(name = name, createdAt = 0, updatedAt = 0))

    private suspend fun titlesIn(playlistId: Long) = playlists.observeTracks(playlistId).first().map { it.videoId }

    @Test
    fun `save replaces a re-download in place, keeping its id and playlists`() = runTest {
        val id = tracks.save(track("a"), peaks = ByteArray(256) { 1 })
        val list = playlist("Mix")
        playlists.append(list, id, now = 1)

        val againId = tracks.save(track("a").copy(codec = AudioCodec.M4A, missing = true), peaks = ByteArray(256) { 2 })

        assertEquals(id, againId)
        assertEquals(AudioCodec.M4A, tracks.findByVideoId("a")?.codec)
        assertArrayEquals(ByteArray(256) { 2 }, tracks.waveform(id)?.peaks)
        assertEquals(listOf("a"), titlesIn(list))
    }

    @Test
    fun `save without peaks drops an old waveform`() = runTest {
        val id = tracks.save(track("a"), peaks = ByteArray(256))
        tracks.save(track("a"), peaks = null)
        assertNull(tracks.waveform(id))
    }

    @Test
    fun `tracks come newest first`() = runTest {
        tracks.save(track("old", downloadedAt = 1), null)
        tracks.save(track("new", downloadedAt = 2), null)
        assertEquals(listOf("new", "old"), tracks.observeAll().first().map { it.videoId })
    }

    @Test
    fun `deleting a track removes it from every playlist and its waveform`() = runTest {
        val a = tracks.save(track("a"), peaks = ByteArray(256))
        val b = tracks.save(track("b"), null)
        val first = playlist("First")
        val second = playlist("Second")
        listOf(first, second).forEach {
            playlists.append(it, a, now = 1)
            playlists.append(it, b, now = 1)
        }
        assertEquals(2, tracks.playlistCount(a))

        tracks.delete(a)

        assertEquals(listOf("b"), titlesIn(first))
        assertEquals(listOf("b"), titlesIn(second))
        assertNull(tracks.waveform(a))
    }

    @Test
    fun `deleting a playlist keeps its tracks`() = runTest {
        val a = tracks.save(track("a"), null)
        val list = playlist("Mix")
        playlists.append(list, a, now = 1)

        playlists.delete(list)

        assertEquals(1, tracks.observeAll().first().size)
        assertTrue(playlists.observeSummaries().first().isEmpty())
    }

    @Test
    fun `a track is in a playlist at most once`() = runTest {
        val a = tracks.save(track("a"), null)
        val list = playlist("Mix")
        playlists.append(list, a, now = 1)
        playlists.append(list, a, now = 2)
        assertEquals(listOf("a"), titlesIn(list))
    }

    @Test
    fun `setMemberships adds to new playlists at the end and leaves the rest`() = runTest {
        val a = tracks.save(track("a"), null)
        val b = tracks.save(track("b"), null)
        val keep = playlist("Keep")
        val drop = playlist("Drop")
        val add = playlist("Add")
        playlists.append(keep, a, now = 1)
        playlists.append(drop, a, now = 1)
        playlists.append(add, b, now = 1)

        playlists.setMemberships(a, setOf(keep, add), now = 2)

        assertEquals(setOf(keep, add), playlists.playlistIdsOf(a).toSet())
        assertEquals(listOf("b", "a"), titlesIn(add))
        assertTrue(titlesIn(drop).isEmpty())
    }

    @Test
    fun `reorder rewrites positions and the cover follows the first track`() = runTest {
        val ids = listOf("a", "b", "c").map { tracks.save(track(it), null) }
        val list = playlist("Mix")
        ids.forEach { playlists.append(list, it, now = 1) }

        playlists.reorder(list, listOf(ids[2], ids[0], ids[1]), now = 2)

        assertEquals(listOf("c", "a", "b"), titlesIn(list))
        val summary = playlists.observeSummaries().first().single()
        assertEquals(3, summary.trackCount)
        assertEquals("/thumbs/c.jpg", summary.coverPath)
    }

    @Test
    fun `playlists are listed by name, ignoring case`() = runTest {
        playlist("beta")
        playlist("Alpha")
        playlist("gamma")
        assertEquals(listOf("Alpha", "beta", "gamma"), playlists.observeSummaries().first().map { it.name })
    }

    @Test
    fun `missing flag can be set and cleared`() = runTest {
        val a = tracks.save(track("a"), null)
        tracks.setMissing(a, true)
        assertTrue(tracks.getAll().single().missing)
        tracks.setMissing(a, false)
        assertEquals(false, tracks.getAll().single().missing)
    }

    @Test
    fun `download tasks keep their state and order, and delete removes them`() = runTest {
        val first = tasks.insert(task("a", createdAt = 1))
        val second = tasks.insert(task("b", createdAt = 2))

        tasks.setState(first, DownloadState.FAILED, 0.5f, "Network", "timeout", now = 3)

        val failed = tasks.get(first)
        assertEquals(DownloadState.FAILED, failed?.state)
        assertEquals("Network", failed?.errorType)
        assertEquals(listOf(first, second), tasks.getAll().map { it.id })

        tasks.delete(first)
        assertEquals(listOf(second), tasks.observeAll().first().map { it.id })
    }

    @Test
    fun `a playlist is found by the YouTube list it came from, the oldest first`() = runTest {
        playlists.insert(PlaylistEntity(name = "Other", createdAt = 0, updatedAt = 0, sourceListId = "PLx"))
        val first = playlists.insert(PlaylistEntity(name = "Mix", createdAt = 0, updatedAt = 0, sourceListId = "PLy"))
        playlists.insert(PlaylistEntity(name = "Mix again", createdAt = 0, updatedAt = 0, sourceListId = "PLy"))

        assertEquals(first, playlists.findBySourceListId("PLy")?.id)
        assertNull(playlists.findBySourceListId("PLmissing"))
        assertNull(playlists.findBySourceListId(""))
    }

    @Test
    fun `addAt keeps YouTube's order even when tracks finish out of order`() = runTest {
        val list = playlist("Album")
        val third = tracks.save(track("c"), null)
        val first = tracks.save(track("a"), null)
        val second = tracks.save(track("b"), null)

        playlists.addAt(list, third, position = 12, now = 1)
        playlists.addAt(list, first, position = 10, now = 2)
        playlists.addAt(list, second, position = 11, now = 3)

        assertEquals(listOf("a", "b", "c"), titlesIn(list))
        // A track already there keeps its place.
        playlists.addAt(list, first, position = 99, now = 4)
        assertEquals(listOf("a", "b", "c"), titlesIn(list))
    }

    @Test
    fun `addAt to a deleted playlist does nothing and does not fail`() = runTest {
        val list = playlist("Gone")
        val a = tracks.save(track("a"), null)
        playlists.delete(list)

        playlists.addAt(list, a, position = 0, now = 1)

        assertEquals(1, tracks.observeAll().first().size)
        assertTrue(playlists.playlistIdsOf(a).isEmpty())
    }

    @Test
    fun `existingVideoIds spans more than one query chunk`() = runTest {
        listOf("a", "b").forEach { tracks.save(track(it), null) }
        val ids = (1..1_200).map { "x$it" } + listOf("a", "b")

        assertEquals(setOf("a", "b"), tracks.existingVideoIds(ids))
        assertTrue(tracks.existingVideoIds(emptyList()).isEmpty())
    }

    @Test
    fun `a playlist's tasks are queued together, can get a target and are cleared together`() = runTest {
        val list = playlist("Album")
        val ids = tasks.insertAll(listOf(task("a", 1), task("b", 2), task("c", 3)))
        assertEquals(3, ids.size)

        tasks.setTarget(ids[1], list, position = 5)

        val withTarget = tasks.get(ids[1])
        assertEquals(list, withTarget?.targetPlaylistId)
        assertEquals(5, withTarget?.targetPosition)
        assertNull(tasks.get(ids[0])?.targetPlaylistId)

        tasks.deleteAll()
        assertTrue(tasks.getAll().isEmpty())
    }

    private fun task(videoId: String, createdAt: Long) = DownloadTaskEntity(
        videoId = videoId,
        title = videoId,
        formatId = "251",
        codec = AudioCodec.OPUS,
        bitrateKbps = 128,
        state = DownloadState.QUEUED,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}

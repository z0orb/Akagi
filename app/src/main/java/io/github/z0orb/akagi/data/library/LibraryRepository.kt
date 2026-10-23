package io.github.z0orb.akagi.data.library

import io.github.z0orb.akagi.data.db.TrackDao
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.data.storage.AudioDeletion
import io.github.z0orb.akagi.data.storage.AudioStore
import io.github.z0orb.akagi.data.storage.ThumbnailStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The downloaded tracks and their files. */
class LibraryRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val audioStore: AudioStore,
    private val thumbnailStore: ThumbnailStore,
) {
    /** Newest first, the Downloads order (PLAN §7.9). */
    fun observeTracks(): Flow<List<TrackEntity>> = trackDao.observeAll()

    fun observeTrack(videoId: String): Flow<TrackEntity?> = trackDao.observeByVideoId(videoId)

    suspend fun playlistCount(trackId: Long): Int = trackDao.playlistCount(trackId)

    /** 256 peak bytes, or null when the waveform couldn't be generated (PLAN §7.7). */
    suspend fun waveform(trackId: Long): ByteArray? = trackDao.waveform(trackId)?.peaks

    /**
     * Deletes the file, then the row (which takes the track out of every playlist) and the thumbnail.
     * When the user must confirm the file deletion first, nothing is removed until [forget] is called.
     */
    suspend fun delete(track: TrackEntity): AudioDeletion =
        audioStore.delete(track.contentUri).also { if (it == AudioDeletion.Deleted) forget(track) }

    /** Removes the track from the library once its file is gone. */
    suspend fun forget(track: TrackEntity) {
        trackDao.delete(track.id)
        track.thumbnailPath?.let { thumbnailStore.delete(it) }
    }

    /** Flags tracks whose file was deleted or moved away outside Akagi, and clears the flag when it is back. */
    suspend fun reconcileMissingFiles() {
        val present = audioStore.presentMediaIds() ?: return
        val files = trackDao.getAll().map { TrackFile(it.id, it.contentUri, it.missing) }
        MissingFiles.changes(files, present).forEach { (id, missing) -> trackDao.setMissing(id, missing) }
    }
}

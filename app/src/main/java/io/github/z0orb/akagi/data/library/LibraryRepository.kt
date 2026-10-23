package io.github.z0orb.akagi.data.library

import io.github.z0orb.akagi.data.db.TrackDao
import io.github.z0orb.akagi.data.db.TrackEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Read side of the downloaded tracks. Playlists and deletion arrive with the Phase 4 sidebar. */
class LibraryRepository @Inject constructor(private val trackDao: TrackDao) {
    /** Newest first, the Downloads order (PLAN §7.9). */
    fun observeTracks(): Flow<List<TrackEntity>> = trackDao.observeAll()

    fun observeTrack(videoId: String): Flow<TrackEntity?> = trackDao.observeByVideoId(videoId)
}

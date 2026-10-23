package io.github.z0orb.akagi.data.library

import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.data.db.PlaylistDao
import io.github.z0orb.akagi.data.db.PlaylistEntity
import io.github.z0orb.akagi.data.db.PlaylistSummary
import io.github.z0orb.akagi.data.db.TrackEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Playlists and their ordered tracks. A track appears at most once per playlist (PLAN §6). */
class PlaylistRepository @Inject constructor(private val dao: PlaylistDao, private val clock: Clock) {
    fun observeSummaries(): Flow<List<PlaylistSummary>> = dao.observeSummaries()

    fun observe(id: Long): Flow<PlaylistEntity?> = dao.observe(id)

    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>> = dao.observeTracks(playlistId)

    suspend fun playlistIdsOf(trackId: Long): Set<Long> = dao.playlistIdsOf(trackId).toSet()

    suspend fun create(name: PlaylistName.Valid): Long {
        val now = clock.nowMillis()
        return dao.insert(PlaylistEntity(name = name.value, createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: PlaylistName.Valid) = dao.rename(id, name.value, clock.nowMillis())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun setMemberships(trackId: Long, playlistIds: Set<Long>) =
        dao.setMemberships(trackId, playlistIds, clock.nowMillis())

    suspend fun remove(playlistId: Long, trackId: Long) {
        dao.deleteMember(playlistId, trackId)
        dao.touch(playlistId, clock.nowMillis())
    }

    suspend fun reorder(playlistId: Long, trackIds: List<Long>) = dao.reorder(playlistId, trackIds, clock.nowMillis())
}

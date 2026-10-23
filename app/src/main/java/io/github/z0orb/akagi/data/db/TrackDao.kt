package io.github.z0orb.akagi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    suspend fun findByVideoId(videoId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    fun observeByVideoId(videoId: String): Flow<TrackEntity?>

    @Insert
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Upsert
    suspend fun upsertWaveform(waveform: TrackWaveformEntity)

    @Query("DELETE FROM track_waveforms WHERE trackId = :trackId")
    suspend fun deleteWaveform(trackId: Long)

    @Query("SELECT * FROM track_waveforms WHERE trackId = :trackId")
    suspend fun waveform(trackId: Long): TrackWaveformEntity?

    /** Inserts a new track, or replaces an existing one's file in place so its playlists survive (D-009). */
    @Transaction
    suspend fun save(track: TrackEntity, peaks: ByteArray?): Long {
        val existing = findByVideoId(track.videoId)
        val id = if (existing == null) insert(track) else track.copy(id = existing.id).also { update(it) }.id
        if (peaks == null) deleteWaveform(id) else upsertWaveform(TrackWaveformEntity(id, peaks))
        return id
    }
}

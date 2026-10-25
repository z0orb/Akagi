package io.github.z0orb.akagi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** How many tracks the library holds and their files' total size (Settings, P7.5). */
data class LibrarySize(val count: Int, val bytes: Long)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(sizeBytes), 0) AS bytes FROM tracks")
    fun observeLibrarySize(): Flow<LibrarySize>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    suspend fun findByVideoId(videoId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    fun observeByVideoId(videoId: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun playlistCount(trackId: Long): Int

    @Query("UPDATE tracks SET missing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    /** Its waveform and playlist entries go with it (FK cascade, D-008). */
    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

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

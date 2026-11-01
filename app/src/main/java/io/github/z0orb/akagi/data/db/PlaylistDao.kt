package io.github.z0orb.akagi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A playlist row for lists: no tracks, just what the Playlists tab shows. */
data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int, val coverPath: String?)

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, " +
            "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount, " +
            "(SELECT t.thumbnailPath FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId " +
            "WHERE pt.playlistId = p.id ORDER BY pt.position LIMIT 1) AS coverPath " +
            "FROM playlists p ORDER BY p.name COLLATE NOCASE, p.id",
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position",
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun playlistIdsOf(trackId: Long): List<Long>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    /** The playlist made from a YouTube list, if any; the oldest one when the user made several. */
    @Query("SELECT * FROM playlists WHERE sourceListId = :listId ORDER BY id LIMIT 1")
    suspend fun findBySourceListId(listId: String): PlaylistEntity?

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(member: PlaylistTrackEntity)

    // The EXISTS guard: INSERT OR IGNORE does not swallow a foreign-key failure, and a playlist deleted while its
    // tracks were downloading must not fail the download (D-075).
    @Query(
        "INSERT OR IGNORE INTO playlist_tracks (playlistId, trackId, position) " +
            "SELECT :playlistId, :trackId, :position WHERE EXISTS (SELECT 1 FROM playlists WHERE id = :playlistId)",
    )
    suspend fun insertMemberAt(playlistId: Long, trackId: Long, position: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteMember(playlistId: Long, trackId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Int)

    /** Adds the track to the end of [playlistId]; a track already there keeps its place (once per playlist). */
    @Transaction
    suspend fun append(playlistId: Long, trackId: Long, now: Long) {
        insertMember(PlaylistTrackEntity(playlistId, trackId, nextPosition(playlistId)))
        touch(playlistId, now)
    }

    /**
     * Puts the track at [position] (YouTube's order). Positions may have gaps while a list is still downloading;
     * they only order. Does nothing if the playlist is gone or the track is already a member.
     */
    @Transaction
    suspend fun addAt(playlistId: Long, trackId: Long, position: Int, now: Long) {
        insertMemberAt(playlistId, trackId, position)
        touch(playlistId, now)
    }

    /** Makes [trackId] a member of exactly [playlistIds], appending where it is new. */
    @Transaction
    suspend fun setMemberships(trackId: Long, playlistIds: Set<Long>, now: Long) {
        val current = playlistIdsOf(trackId).toSet()
        (current - playlistIds).forEach {
            deleteMember(it, trackId)
            touch(it, now)
        }
        (playlistIds - current).forEach { append(it, trackId, now) }
    }

    /** Rewrites every position in one transaction, so a reorder is never half-applied. */
    @Transaction
    suspend fun reorder(playlistId: Long, trackIds: List<Long>, now: Long) {
        trackIds.forEachIndexed { index, trackId -> setPosition(playlistId, trackId, index) }
        touch(playlistId, now)
    }
}

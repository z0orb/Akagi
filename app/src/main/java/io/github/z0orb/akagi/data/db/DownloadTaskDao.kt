package io.github.z0orb.akagi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt, id")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY createdAt, id")
    suspend fun getAll(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun get(id: Long): DownloadTaskEntity?

    @Insert
    suspend fun insert(task: DownloadTaskEntity): Long

    /** One transaction for a whole playlist: either every task is queued or none is. */
    @Insert
    suspend fun insertAll(tasks: List<DownloadTaskEntity>): List<Long>

    @Query("UPDATE download_tasks SET targetPlaylistId = :playlistId, targetPosition = :position WHERE id = :id")
    suspend fun setTarget(id: Long, playlistId: Long, position: Int)

    @Query("DELETE FROM download_tasks")
    suspend fun deleteAll()

    @Query(
        "UPDATE download_tasks SET state = :state, progress = :progress, errorType = :errorType, " +
            "errorMessage = :errorMessage, updatedAt = :now WHERE id = :id",
    )
    suspend fun setState(
        id: Long,
        state: DownloadState,
        progress: Float,
        errorType: String?,
        errorMessage: String?,
        now: Long,
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

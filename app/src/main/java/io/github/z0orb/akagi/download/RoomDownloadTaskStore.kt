package io.github.z0orb.akagi.download

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.data.db.DownloadTaskDao
import io.github.z0orb.akagi.data.db.DownloadTaskEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadTaskStore @Inject constructor(private val dao: DownloadTaskDao, private val clock: Clock) :
    DownloadTaskStore {

    override fun observe(): Flow<List<DownloadTask>> = dao.observeAll().map { rows -> rows.map(::toTask) }

    override suspend fun all(): List<DownloadTask> = dao.getAll().map(::toTask)

    override suspend fun get(id: Long): DownloadTask? = dao.get(id)?.let(::toTask)

    override suspend fun insert(request: DownloadRequest): Long {
        val now = clock.nowMillis()
        return dao.insert(
            DownloadTaskEntity(
                videoId = request.videoId,
                title = request.title,
                formatId = request.formatId,
                codec = request.codec,
                bitrateKbps = request.bitrateKbps,
                state = DownloadState.QUEUED,
                replaceTrackId = request.replaceTrackId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun setState(id: Long, state: DownloadState, error: AppError?) {
        val detail = (error as? AppError.Unknown)?.detail
        dao.setState(id, state, progress = 0f, errorType = error?.key, errorMessage = detail, now = clock.nowMillis())
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun toTask(row: DownloadTaskEntity) = DownloadTask(
        id = row.id,
        request = DownloadRequest(row.videoId, row.title, row.formatId, row.codec, row.bitrateKbps, row.replaceTrackId),
        state = row.state,
        error = row.errorType?.let { AppError.fromKey(it, row.errorMessage) },
    )
}

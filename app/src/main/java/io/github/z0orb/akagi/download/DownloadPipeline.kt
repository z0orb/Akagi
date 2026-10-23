package io.github.z0orb.akagi.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.account.CookieAuth
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.core.common.DispatcherProvider
import io.github.z0orb.akagi.data.db.TrackDao
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.data.storage.AudioStore
import io.github.z0orb.akagi.data.storage.FileNamer
import io.github.z0orb.akagi.data.storage.ThumbnailStore
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import io.github.z0orb.akagi.ytdlp.YtDlpEngine
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** One download from yt-dlp to a library row (PLAN §7.5 "Steps"). */
class DownloadPipeline @Inject constructor(
    @ApplicationContext context: Context,
    private val engine: YtDlpEngine,
    private val auth: CookieAuth,
    private val waveforms: WaveformGenerator,
    private val audioStore: AudioStore,
    private val thumbnails: ThumbnailStore,
    private val trackDao: TrackDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : DownloadRunner {
    private val tempRoot = File(context.cacheDir, "dl")

    override suspend fun run(
        task: DownloadTask,
        onProgress: (DownloadProgress) -> Unit,
        onPostProcessing: suspend () -> Unit,
    ) = coroutineScope {
        val request = task.request
        val directory = withContext(dispatchers.io) {
            tempDir(task.id).apply {
                deleteRecursively()
                mkdirs()
            }
        }
        // The thumbnail downloads alongside the audio; it is small and never fails the task.
        val thumbnail = async { thumbnails.fetch(request.videoId) }
        val file = auth.run(request.videoId) { cookieFile ->
            engine.download(
                processId = task.id.toString(),
                videoId = request.videoId,
                formatId = request.formatId,
                codec = request.codec,
                bitrateKbps = request.bitrateKbps,
                outputDir = directory,
                cookieFile = cookieFile,
                onProgress = onProgress,
            )
        }
        onPostProcessing()
        val analysis = waveforms.analyse(file)
        val existing = trackDao.findByVideoId(request.videoId)
        val fileName = FileNamer.fileName(request.title, request.videoId, request.codec.extension)
        val saved = audioStore.save(file, fileName, request.title, request.codec, replacing = existing?.contentUri)
        val thumbnailPath = thumbnail.await() ?: existing?.thumbnailPath
        trackDao.save(
            TrackEntity(
                videoId = request.videoId,
                title = request.title,
                durationMs = analysis.durationMs,
                sizeBytes = saved.sizeBytes,
                codec = request.codec,
                bitrateKbps = request.bitrateKbps,
                contentUri = saved.contentUri,
                fileName = fileName,
                thumbnailPath = thumbnailPath,
                downloadedAt = clock.nowMillis(),
            ),
            analysis.peaks,
        )
        cleanUp(task.id)
    }

    override suspend fun cleanUp(taskId: Long) {
        withContext(dispatchers.io) { tempDir(taskId).deleteRecursively() }
    }

    /** Clears every task's leftovers; called at startup, before interrupted tasks restart from scratch. */
    suspend fun cleanUpAll() {
        withContext(dispatchers.io) { tempRoot.deleteRecursively() }
    }

    private fun tempDir(taskId: Long) = File(tempRoot, taskId.toString())
}

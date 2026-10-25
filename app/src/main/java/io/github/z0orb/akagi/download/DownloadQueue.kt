package io.github.z0orb.akagi.download

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.core.common.toAppError
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Persistence for the queue (Room in the app, a map in tests). */
interface DownloadTaskStore {
    fun observe(): Flow<List<DownloadTask>>

    suspend fun all(): List<DownloadTask>

    suspend fun get(id: Long): DownloadTask?

    suspend fun insert(request: DownloadRequest): Long

    suspend fun setState(id: Long, state: DownloadState, error: AppError? = null)

    suspend fun delete(id: Long)
}

/** Does the actual work for one task: yt-dlp, post-processing and saving. Fails by throwing [AppException]. */
interface DownloadRunner {
    suspend fun run(task: DownloadTask, onProgress: (DownloadProgress) -> Unit, onPostProcessing: suspend () -> Unit)

    /** Removes whatever a cancelled or failed task left behind. */
    suspend fun cleanUp(taskId: Long)
}

data class CompletedDownload(val taskId: Long, val videoId: String, val title: String)

/**
 * The persisted download queue (PLAN §7.5): FIFO, at most [maxParallel] running at once, cancel, retry,
 * and recovery after process death. Pure, so the rules are tested with a fake runner.
 */
class DownloadQueue(
    private val store: DownloadTaskStore,
    private val runner: DownloadRunner,
    private val scope: CoroutineScope,
    private val clock: Clock,
    maxParallel: Int = MAX_PARALLEL,
) {
    // Semaphore permits are handed out in FIFO order, which gives the queue its order.
    private val slots = Semaphore(maxParallel)
    private val jobsLock = Mutex()
    private val jobs = mutableMapOf<Long, Job>()
    private val lastProgressAt = mutableMapOf<Long, Long>()

    val tasks: Flow<List<DownloadTask>> = store.observe()

    private val _progress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())

    /** Live progress of running tasks, at most [PROGRESS_INTERVAL_MS] apart per task. */
    val progress: StateFlow<Map<Long, DownloadProgress>> = _progress.asStateFlow()

    private val _completed = MutableSharedFlow<CompletedDownload>(extraBufferCapacity = 16)
    val completed: SharedFlow<CompletedDownload> = _completed.asSharedFlow()

    /** Re-queues what was queued or running when the process died. Failed tasks wait for Retry. */
    suspend fun restore() {
        store.all().filter { it.state != DownloadState.FAILED }.forEach { task ->
            store.setState(task.id, DownloadState.QUEUED)
            start(task.id)
        }
    }

    /** Returns the id of the task for this video: an active one is reused, so tapping twice queues once. */
    suspend fun enqueue(request: DownloadRequest): Long {
        val existing = store.all().filter { it.request.videoId == request.videoId }
        existing.firstOrNull { it.state != DownloadState.FAILED }?.let { return it.id }
        existing.forEach { store.delete(it.id) }
        val id = store.insert(request)
        start(id)
        return id
    }

    suspend fun cancel(id: Long) {
        jobsLock.withLock { jobs.remove(id) }?.cancelAndJoin()
        runner.cleanUp(id)
        store.delete(id)
        clearProgress(id)
    }

    suspend fun retry(id: Long) {
        val task = store.get(id)?.takeIf { it.state == DownloadState.FAILED } ?: return
        store.setState(task.id, DownloadState.QUEUED)
        start(task.id)
    }

    /** Android stopped the service (e.g. the 6 h dataSync limit): fail everything so the user can retry. */
    suspend fun interruptAll() {
        val running = jobsLock.withLock { jobs.values.toList().also { jobs.clear() } }
        running.forEach { it.cancelAndJoin() }
        store.all().filter { it.state != DownloadState.FAILED }.forEach { task ->
            runner.cleanUp(task.id)
            store.setState(task.id, DownloadState.FAILED, AppError.Interrupted)
            clearProgress(task.id)
        }
    }

    private suspend fun start(id: Long) {
        jobsLock.withLock {
            if (jobs[id]?.isActive == true) return
            jobs[id] = scope.launch { slots.withPermit { execute(id) } }
        }
    }

    private suspend fun execute(id: Long) {
        val task = store.get(id) ?: return
        try {
            store.setState(id, DownloadState.RUNNING)
            runner.run(
                task,
                onProgress = { publishProgress(id, it) },
                onPostProcessing = { store.setState(id, DownloadState.POST_PROCESSING) },
            )
            store.delete(id)
            _completed.tryEmit(CompletedDownload(id, task.request.videoId, task.request.title))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(id, e.toAppError())
        } finally {
            val self = currentCoroutineContext()[Job]
            // NonCancellable: a cancelled coroutine can't otherwise wait for the lock.
            withContext(NonCancellable) { jobsLock.withLock { if (jobs[id] === self) jobs.remove(id) } }
            clearProgress(id)
        }
    }

    private suspend fun fail(id: Long, error: AppError) {
        runner.cleanUp(id)
        store.setState(id, DownloadState.FAILED, error)
    }

    private fun publishProgress(id: Long, progress: DownloadProgress) {
        val now = clock.nowMillis()
        val last = synchronized(lastProgressAt) { lastProgressAt[id] }
        if (last != null && now - last < PROGRESS_INTERVAL_MS && progress.fraction < 1f) return
        synchronized(lastProgressAt) { lastProgressAt[id] = now }
        _progress.update { it + (id to progress) }
    }

    private fun clearProgress(id: Long) {
        synchronized(lastProgressAt) { lastProgressAt.remove(id) }
        _progress.update { it - id }
    }

    companion object {
        const val MAX_PARALLEL = 3

        /** The UI updates at most four times a second (PLAN §7.5). */
        const val PROGRESS_INTERVAL_MS = 250L
    }
}

package io.github.z0orb.akagi.download

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {

    private class FakeStore : DownloadTaskStore {
        val rows = MutableStateFlow<Map<Long, DownloadTask>>(emptyMap())
        private var nextId = 1L

        // Room's reads suspend; with this on, so do the fake's, so concurrent callers interleave.
        var suspendReads = false

        override fun observe(): Flow<List<DownloadTask>> = rows.map { it.values.sortedBy(DownloadTask::id) }

        override suspend fun all(): List<DownloadTask> {
            val snapshot = rows.value.values.sortedBy(DownloadTask::id)
            if (suspendReads) yield()
            return snapshot
        }

        override suspend fun get(id: Long) = rows.value[id]

        override suspend fun insert(request: DownloadRequest): Long {
            val id = nextId++
            rows.update { it + (id to DownloadTask(id, request, DownloadState.QUEUED, null)) }
            return id
        }

        override suspend fun setState(id: Long, state: DownloadState, error: AppError?) {
            rows.update { rows -> rows[id]?.let { rows + (id to it.copy(state = state, error = error)) } ?: rows }
        }

        override suspend fun delete(id: Long) = rows.update { it - id }

        fun state(id: Long) = rows.value[id]?.state
    }

    /** Each task runs until the test completes or fails its gate. */
    private class FakeRunner : DownloadRunner {
        val gates = mutableMapOf<Long, CompletableDeferred<Unit>>()
        val started = mutableListOf<Long>()
        val cleaned = mutableListOf<Long>()
        var running = 0
        var maxRunning = 0

        override suspend fun run(
            task: DownloadTask,
            onProgress: (DownloadProgress) -> Unit,
            onPostProcessing: suspend () -> Unit,
        ) {
            started += task.id
            running++
            maxRunning = maxOf(maxRunning, running)
            try {
                onProgress(DownloadProgress(0.5f, 3, "1.0MiB/s"))
                gates.getOrPut(task.id) { CompletableDeferred() }.await()
                onPostProcessing()
            } finally {
                running--
            }
        }

        override suspend fun cleanUp(taskId: Long) {
            cleaned += taskId
        }

        fun finish(id: Long) = gates.getOrPut(id) { CompletableDeferred() }.complete(Unit)

        fun fail(id: Long, error: AppError) =
            gates.getOrPut(id) { CompletableDeferred() }.completeExceptionally(AppException(error))
    }

    private val store = FakeStore()
    private val runner = FakeRunner()

    private fun TestScope.queue() = DownloadQueue(store, runner, backgroundScope, clock = { testScheduler.currentTime })

    private fun request(videoId: String) = DownloadRequest(videoId, "Title $videoId", "251", AudioCodec.OPUS, 135)

    @Test
    fun `at most three run at once, in FIFO order`() = runTest {
        val queue = queue()
        val ids = (1..5).map { queue.enqueue(request("video$it")) }
        runCurrent()
        assertEquals(ids.take(3), runner.started)
        assertEquals(DownloadState.QUEUED, store.state(ids[3]))

        runner.finish(ids[1])
        runCurrent()
        assertEquals(ids.take(4), runner.started)
        assertEquals(null, store.state(ids[1]))

        ids.forEach(runner::finish)
        runCurrent()
        assertEquals(ids, runner.started)
        assertEquals(3, runner.maxRunning)
        assertTrue(store.rows.value.isEmpty())
    }

    @Test
    fun `the same video is queued once while active`() = runTest {
        val queue = queue()
        val first = queue.enqueue(request("same"))
        val second = queue.enqueue(request("same"))
        assertEquals(first, second)
        assertEquals(1, store.rows.value.size)
    }

    @Test
    fun `two concurrent enqueues of one video queue it once`() = runTest {
        val queue = queue()
        store.suspendReads = true
        val first = async { queue.enqueue(request("same")) }
        val second = async { queue.enqueue(request("same")) }
        assertEquals(first.await(), second.await())
        assertEquals(1, store.rows.value.size)
    }

    @Test
    fun `cancel stops the task, cleans up and removes the row`() = runTest {
        val queue = queue()
        val id = queue.enqueue(request("a"))
        runCurrent()
        assertEquals(DownloadState.RUNNING, store.state(id))

        queue.cancel(id)
        assertEquals(null, store.state(id))
        assertEquals(listOf(id), runner.cleaned)
        assertEquals(0, runner.running)
        assertTrue(queue.progress.value.isEmpty())
    }

    @Test
    fun `cancelling a running task frees its slot for the next one`() = runTest {
        val queue = queue()
        val ids = (1..4).map { queue.enqueue(request("v$it")) }
        runCurrent()
        queue.cancel(ids[0])
        runCurrent()
        assertEquals(ids, runner.started)
    }

    @Test
    fun `a failure keeps the row with its error, and retry runs it again`() = runTest {
        val queue = queue()
        val id = queue.enqueue(request("a"))
        runCurrent()
        runner.fail(id, AppError.Network)
        runCurrent()
        assertEquals(DownloadState.FAILED, store.state(id))
        assertEquals(AppError.Network, store.rows.value.getValue(id).error)
        assertEquals(listOf(id), runner.cleaned)

        runner.gates.remove(id)
        queue.retry(id)
        runCurrent()
        assertEquals(DownloadState.RUNNING, store.state(id))
        assertEquals(null, store.rows.value.getValue(id).error)
        runner.finish(id)
        runCurrent()
        assertEquals(null, store.state(id))
    }

    @Test
    fun `enqueueing a video whose last attempt failed replaces the failed task`() = runTest {
        val queue = queue()
        val failed = queue.enqueue(request("a"))
        runCurrent()
        runner.fail(failed, AppError.Network)
        runCurrent()
        val again = queue.enqueue(request("a"))
        assertTrue(again != failed)
        assertEquals(listOf(again), store.rows.value.keys.toList())
    }

    @Test
    fun `restore re-queues tasks that were running when the process died, not failed ones`() = runTest {
        val running = store.insert(request("running"))
        store.setState(running, DownloadState.RUNNING)
        val post = store.insert(request("post"))
        store.setState(post, DownloadState.POST_PROCESSING)
        val failed = store.insert(request("failed"))
        store.setState(failed, DownloadState.FAILED, AppError.Network)

        queue().restore()
        runCurrent()
        assertEquals(listOf(running, post), runner.started)
        assertEquals(DownloadState.FAILED, store.state(failed))
    }

    @Test
    fun `interruptAll fails every active task as Interrupted`() = runTest {
        val queue = queue()
        val ids = (1..4).map { queue.enqueue(request("v$it")) }
        runCurrent()
        queue.interruptAll()
        ids.forEach { assertEquals(DownloadState.FAILED, store.state(it)) }
        ids.forEach { assertEquals(AppError.Interrupted, store.rows.value.getValue(it).error) }
        assertEquals(0, runner.running)
    }

    @Test
    fun `progress is published while running and completion is announced`() = runTest {
        val queue = queue()
        val completed = mutableListOf<CompletedDownload>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { queue.completed.collect { completed += it } }
        val id = queue.enqueue(request("a"))
        runCurrent()
        assertEquals(0.5f, queue.progress.value.getValue(id).fraction)

        runner.finish(id)
        runCurrent()
        assertTrue(queue.progress.value.isEmpty())
        assertEquals(listOf(CompletedDownload(id, "a", "Title a")), completed)
    }
}

package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.toAppError
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoInfoCacheTest {

    private var now = 0L
    private val fetched = mutableListOf<String>()
    private var gate: CompletableDeferred<Unit>? = null
    private var failNext = false
    private var failure: Exception = AppException(AppError.Network)

    private fun info(id: String) = VideoInfo(id, "t", 1.0, liveError = null, formats = emptyList())

    private fun cache(capacity: Int = 20) = VideoInfoCache(
        fetch = { id ->
            fetched += id
            gate?.await()
            if (failNext) {
                failNext = false
                throw failure
            }
            info(id)
        },
        clock = { now },
        capacity = capacity,
        ttlMillis = 1_000,
    )

    @Test
    fun `fresh entries are served from the cache, stale ones refetched`() = runTest {
        val cache = cache()
        cache.get("a")
        cache.get("a")
        assertEquals(listOf("a"), fetched)
        now = 1_000
        assertNull(cache.peek("a"))
        cache.get("a")
        assertEquals(listOf("a", "a"), fetched)
    }

    @Test
    fun `live and upcoming videos are never cached`() = runTest {
        val cache = VideoInfoCache(
            fetch = { id ->
                fetched += id
                info(id).copy(liveError = AppError.Live)
            },
            clock = { now },
        )
        cache.get("a")
        cache.get("a")
        assertEquals(listOf("a", "a"), fetched)
    }

    @Test
    fun `the least recently used entry is evicted`() = runTest {
        val cache = cache(capacity = 2)
        cache.get("a")
        cache.get("b")
        cache.get("a")
        cache.get("c")
        assertEquals(info("a"), cache.peek("a"))
        assertNull(cache.peek("b"))
    }

    @Test
    fun `concurrent requests share one fetch`() = runTest {
        val cache = cache()
        gate = CompletableDeferred()
        val first = async { cache.get("a") }
        val second = async { cache.get("a") }
        runCurrent()
        gate?.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(listOf("a"), fetched)
    }

    @Test
    fun `a cancelled prefetch doesn't cancel the sheet waiting on it`() = runTest {
        val cache = cache()
        gate = CompletableDeferred()
        val prefetch = async { cache.get("a") }
        runCurrent()
        val sheet = async { cache.get("a") }
        runCurrent()
        prefetch.cancel()
        runCurrent()
        gate?.complete(Unit)
        assertEquals(info("a"), sheet.await())
        assertEquals(listOf("a", "a"), fetched)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val cache = cache()
        failNext = true
        assertTrue(runCatching { cache.get("a") }.exceptionOrNull() is AppException)
        assertEquals(info("a"), cache.get("a"))
        assertEquals(listOf("a", "a"), fetched)
    }

    @Test
    fun `a non-AppException failure reaches the caller, which maps it for the sheet`() = runTest {
        val cache = cache()
        failNext = true
        failure = IOException("No space left on device")
        val error = runCatching { cache.get("a") }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(AppError.Unknown("No space left on device"), (error as Exception).toAppError())
        assertEquals(info("a"), cache.get("a"))
    }
}

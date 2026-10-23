package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Format info for recently seen videos (PLAN §7.4): at most [capacity] entries, each fresh for [ttlMillis].
 * Callers asking for the same video while it is being fetched share that one fetch. Failures aren't cached.
 */
class VideoInfoCache(
    private val fetch: suspend (videoId: String) -> VideoInfo,
    private val clock: Clock,
    private val capacity: Int = 20,
    private val ttlMillis: Long = 30 * 60 * 1000L,
) {
    private class Entry(val info: VideoInfo, val fetchedAt: Long)

    private val lock = Mutex()

    // accessOrder = true makes iteration least-recently-used first, so eviction drops the head.
    private val entries = LinkedHashMap<String, Entry>(capacity, 0.75f, true)
    private val inFlight = mutableMapOf<String, CompletableDeferred<VideoInfo>>()

    suspend fun get(videoId: String): VideoInfo {
        val (pending, owner) = lock.withLock {
            entries[videoId]?.takeIf { clock.nowMillis() - it.fetchedAt < ttlMillis }?.let { return it.info }
            inFlight[videoId]?.let { return@withLock it to false }
            CompletableDeferred<VideoInfo>().also { inFlight[videoId] = it } to true
        }
        if (!owner) {
            return try {
                pending.await()
            } catch (e: CancellationException) {
                // The fetch we joined was a cancelled prefetch; if we still want the info, fetch it ourselves.
                currentCoroutineContext().ensureActive()
                get(videoId)
            }
        }
        val result = runCatching { fetch(videoId) }
        // NonCancellable: a cancelled owner must still unregister, or later callers would wait forever.
        withContext(NonCancellable) {
            lock.withLock {
                inFlight.remove(videoId)
                result.onSuccess { info ->
                    entries[videoId] = Entry(info, clock.nowMillis())
                    if (entries.size > capacity) entries.remove(entries.keys.first())
                }
            }
        }
        pending.completeWith(result)
        return result.getOrThrow()
    }

    /** Fresh info if cached, without fetching. */
    suspend fun peek(videoId: String): VideoInfo? = lock.withLock {
        entries[videoId]?.takeIf { clock.nowMillis() - it.fetchedAt < ttlMillis }?.info
    }
}

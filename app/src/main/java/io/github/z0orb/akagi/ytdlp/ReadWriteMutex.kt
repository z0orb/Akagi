package io.github.z0orb.akagi.ytdlp

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/**
 * Many readers or one writer (PLAN §7.5): fetches and downloads read, the yt-dlp update writes, so an update
 * never swaps the binary under a running process. A waiting writer holds back new readers instead of starving.
 */
class ReadWriteMutex(private val maxReaders: Int = 64) {
    private val permits = Semaphore(maxReaders)
    private val writers = Mutex()

    suspend fun <T> read(block: suspend () -> T): T {
        // Passing through the writer mutex queues new readers behind a waiting writer.
        writers.withLock { permits.acquire() }
        try {
            return block()
        } finally {
            permits.release()
        }
    }

    suspend fun <T> write(block: suspend () -> T): T = writers.withLock {
        var acquired = 0
        try {
            repeat(maxReaders) {
                permits.acquire()
                acquired++
            }
            block()
        } finally {
            repeat(acquired) { permits.release() }
        }
    }
}

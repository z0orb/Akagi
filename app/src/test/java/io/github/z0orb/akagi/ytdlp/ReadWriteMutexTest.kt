package io.github.z0orb.akagi.ytdlp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadWriteMutexTest {

    @Test
    fun `readers share, the writer waits for them and blocks later readers`() = runTest {
        val lock = ReadWriteMutex(maxReaders = 4)
        val events = mutableListOf<String>()
        val releaseReaders = CompletableDeferred<Unit>()

        repeat(2) { i ->
            launch {
                lock.read {
                    events += "read$i"
                    releaseReaders.await()
                }
            }
        }
        runCurrent()
        launch { lock.write { events += "write" } }
        runCurrent()
        launch { lock.read { events += "late read" } }
        runCurrent()
        assertEquals(listOf("read0", "read1"), events)

        releaseReaders.complete(Unit)
        runCurrent()
        assertEquals(listOf("read0", "read1", "write", "late read"), events)
    }
}

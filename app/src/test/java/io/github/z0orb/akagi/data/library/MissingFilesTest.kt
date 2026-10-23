package io.github.z0orb.akagi.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingFilesTest {

    private fun file(trackId: Long, mediaId: String, missing: Boolean = false) =
        TrackFile(trackId, "content://media/external_primary/audio/media/$mediaId", missing)

    @Test
    fun `a file gone from MediaStore is marked missing`() {
        val changes = MissingFiles.changes(listOf(file(1, "10"), file(2, "20")), presentMediaIds = setOf(20))
        assertEquals(mapOf(1L to true), changes)
    }

    @Test
    fun `a file that came back is no longer missing`() {
        val changes = MissingFiles.changes(listOf(file(1, "10", missing = true)), presentMediaIds = setOf(10))
        assertEquals(mapOf(1L to false), changes)
    }

    @Test
    fun `flags that are already right produce no changes`() {
        val files = listOf(file(1, "10"), file(2, "20", missing = true))
        assertEquals(emptyMap<Long, Boolean>(), MissingFiles.changes(files, presentMediaIds = setOf(10)))
    }

    @Test
    fun `an unparseable URI is left alone`() {
        assertEquals(emptyMap<Long, Boolean>(), MissingFiles.changes(listOf(file(1, "abc")), emptySet()))
    }
}

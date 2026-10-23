package io.github.z0orb.akagi.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamerTest {

    private fun name(title: String) = FileNamer.fileName(title, "dQw4w9WgXcQ", "opus")

    @Test
    fun `title, id and extension`() {
        assertEquals("Never Gonna Give You Up [dQw4w9WgXcQ].opus", name("Never Gonna Give You Up"))
    }

    @Test
    fun `illegal characters become spaces and whitespace collapses`() {
        assertEquals("AC DC - Back In Black (Live) [dQw4w9WgXcQ].opus", name("AC/DC - Back In Black: (Live)?"))
        assertEquals("a b c [dQw4w9WgXcQ].opus", name("a\t\nb  \u0007 c"))
        assertEquals("x y [dQw4w9WgXcQ].opus", name("x*\"<>|\\y"))
    }

    @Test
    fun `dots and spaces are trimmed at the ends`() {
        assertEquals("Hidden [dQw4w9WgXcQ].opus", name(" ..Hidden... "))
    }

    @Test
    fun `an empty title becomes Untitled`() {
        assertEquals("Untitled [dQw4w9WgXcQ].opus", name(""))
        assertEquals("Untitled [dQw4w9WgXcQ].opus", name(" ?/: "))
    }

    @Test
    fun `unicode is kept and the name is capped at 200 UTF-8 bytes without splitting characters`() {
        assertEquals("夜に駆ける 🎵 [dQw4w9WgXcQ].opus", name("夜に駆ける 🎵"))
        val long = FileNamer.fileName("🎵".repeat(100), "dQw4w9WgXcQ", "opus")
        assertTrue(long.toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue(long.endsWith(" [dQw4w9WgXcQ].opus"))
        assertEquals(long.removeSuffix(" [dQw4w9WgXcQ].opus"), "🎵".repeat(45))
    }
}

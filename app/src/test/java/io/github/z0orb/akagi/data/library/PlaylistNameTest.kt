package io.github.z0orb.akagi.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistNameTest {

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(PlaylistName.Valid("Road trip"), PlaylistName.of("  Road trip \n"))
    }

    @Test
    fun `empty or whitespace-only names are blank`() {
        assertEquals(PlaylistName.Blank, PlaylistName.of(""))
        assertEquals(PlaylistName.Blank, PlaylistName.of("   \t"))
    }

    @Test
    fun `sixty characters is the limit`() {
        assertEquals(PlaylistName.Valid("a".repeat(60)), PlaylistName.of("a".repeat(60)))
        assertEquals(PlaylistName.TooLong, PlaylistName.of("a".repeat(61)))
    }

    @Test
    fun `an emoji counts as one character`() {
        val sixtyEmoji = "🎵".repeat(60)
        assertEquals(PlaylistName.Valid(sixtyEmoji), PlaylistName.of(sixtyEmoji))
    }
}

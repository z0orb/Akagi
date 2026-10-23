package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatMapperTest {

    @Test
    fun `keeps audio-only Opus and M4A, drops DRC, video, storyboards and unknown codecs`() {
        val info = FormatFixtures.info("standard")
        assertEquals(listOf("249", "250", "251", "139", "140"), info.formats.map { it.formatId })
        assertEquals("dQw4w9WgXcQ", info.videoId)
        assertEquals(213.0, info.durationSeconds)
        assertFalse(info.isLiveOrUpcoming)
    }

    @Test
    fun `codec, rounded bitrate and size come from the format`() {
        val formats = FormatFixtures.info("standard").formats.associateBy { it.formatId }
        assertEquals(AudioFormat("140", AudioCodec.M4A, 129, 3_449_447), formats["140"])
        // filesize_approx is used when filesize is missing.
        assertEquals(1_836_480L, formats.getValue("250").sizeBytes)
        // Neither is known: abr × duration / 8.
        assertEquals((135.12 * 1000 / 8 * 213).toLong(), formats.getValue("251").sizeBytes)
    }

    @Test
    fun `multi-language videos keep only the original track`() {
        val info = FormatFixtures.info("multi_language")
        assertEquals(listOf("251-0", "774", "140-0"), info.formats.map { it.formatId })
    }

    @Test
    fun `live and upcoming streams are flagged`() {
        val live = """{"id":"x","title":"t","live_status":"is_live","formats":[]}"""
        val upcoming = """{"id":"x","title":"t","live_status":"is_upcoming","formats":[]}"""
        val ended = """{"id":"x","title":"t","live_status":"was_live","formats":[]}"""
        assertTrue(AudioFormatMapper.parse(live).isLiveOrUpcoming)
        assertTrue(AudioFormatMapper.parse(upcoming).isLiveOrUpcoming)
        assertFalse(AudioFormatMapper.parse(ended).isLiveOrUpcoming)
    }

    @Test
    fun `a missing title falls back to the id and a missing duration stays unknown`() {
        val info = AudioFormatMapper.parse("""{"id":"abcdefghijk","formats":[]}""")
        assertEquals("abcdefghijk", info.title)
        assertEquals(null, info.durationSeconds)
    }
}

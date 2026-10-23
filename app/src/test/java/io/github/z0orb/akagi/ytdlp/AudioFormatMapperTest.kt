package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AudioCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFormatMapperTest {

    @Test
    fun `keeps audio-only Opus and M4A, drops DRC, video, storyboards and unknown codecs`() {
        val info = FormatFixtures.info("standard")
        assertEquals(listOf("249", "250", "251", "139", "140"), info.formats.map { it.formatId })
        assertEquals("dQw4w9WgXcQ", info.videoId)
        assertEquals(213.0, info.durationSeconds)
        assertNull(info.liveError)
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
    fun `live, upcoming and just-ended streams are flagged but finished ones are not`() {
        fun parse(status: String) =
            AudioFormatMapper.parse("""{"id":"x","title":"t","live_status":"$status","formats":[]}""")
        assertEquals(AppError.Live, parse("is_live").liveError)
        assertEquals(AppError.Upcoming(""), parse("is_upcoming").liveError)
        assertEquals(AppError.PostLive, parse("post_live").liveError)
        assertNull(parse("was_live").liveError)
        assertNull(parse("not_live").liveError)
        assertEquals(AppError.Live, AudioFormatMapper.parse("""{"id":"x","is_live":true,"formats":[]}""").liveError)
    }

    @Test
    fun `a missing title falls back to the id and a missing duration stays unknown`() {
        val info = AudioFormatMapper.parse("""{"id":"abcdefghijk","formats":[]}""")
        assertEquals("abcdefghijk", info.title)
        assertEquals(null, info.durationSeconds)
    }
}

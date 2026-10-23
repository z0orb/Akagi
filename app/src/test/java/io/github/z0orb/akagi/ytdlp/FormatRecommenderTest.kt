package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.ytdlp.FormatTier.DATA_SAVER
import io.github.z0orb.akagi.ytdlp.FormatTier.HIGHEST
import io.github.z0orb.akagi.ytdlp.FormatTier.RECOMMENDED
import io.github.z0orb.akagi.ytdlp.FormatTier.SMALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatRecommenderTest {

    private fun tiers(options: List<FormatOption>?) = options.orEmpty().map { it.bitrateKbps to it.tier }

    @Test
    fun `Opus is recommended at itag 251 and tiers label the rest`() {
        val choices = FormatRecommender.choicesFor(FormatFixtures.info("standard"))
        assertEquals(listOf(AudioCodec.OPUS, AudioCodec.M4A), choices.options.keys.toList())
        assertEquals(listOf(52 to DATA_SAVER, 69 to SMALL, 135 to RECOMMENDED), tiers(choices.options[AudioCodec.OPUS]))
        assertEquals(listOf(49 to DATA_SAVER, 129 to RECOMMENDED), tiers(choices.options[AudioCodec.M4A]))
        assertEquals("251", choices.recommended?.formatId)
        assertFalse(choices.isMp3Fallback)
    }

    @Test
    fun `bitrates over 200 kbps are labelled highest`() {
        val choices = FormatRecommender.choicesFor(FormatFixtures.info("multi_language"))
        assertEquals(listOf(150 to RECOMMENDED, 256 to HIGHEST), tiers(choices.options[AudioCodec.OPUS]))
    }

    @Test
    fun `M4A is recommended when there is no Opus`() {
        val standard = FormatFixtures.info("standard")
        val info = standard.copy(formats = standard.formats.filter { it.codec == AudioCodec.M4A })
        val choices = FormatRecommender.choicesFor(info)
        assertEquals(listOf(AudioCodec.M4A), choices.options.keys.toList())
        assertEquals("140", choices.recommended?.formatId)
    }

    @Test
    fun `MP3 appears only as the fallback, at 192 kbps recommended`() {
        val choices = FormatRecommender.choicesFor(FormatFixtures.info("no_opus_or_m4a"))
        assertTrue(choices.isMp3Fallback)
        assertEquals(listOf(128 to null, 192 to RECOMMENDED, 320 to HIGHEST), tiers(choices.options[AudioCodec.MP3]))
        assertEquals(FormatRecommender.MP3_FORMAT_ID, choices.recommended?.formatId)
        assertEquals(192_000L / 8 * 180, choices.recommended?.sizeBytes)
    }

    @Test
    fun `the remembered codec and tier win when offered`() {
        val choices = FormatRecommender.choicesFor(FormatFixtures.info("standard"))
        val pick = { remembered: RememberedChoice? ->
            FormatRecommender.initialSelection(choices, remembered)?.formatId
        }
        assertEquals("251", pick(null))
        assertEquals("249", pick(RememberedChoice(AudioCodec.OPUS, DATA_SAVER)))
        assertEquals("140", pick(RememberedChoice(AudioCodec.M4A, RECOMMENDED)))
        // M4A has no "small" tier here: fall back to that codec's recommendation.
        assertEquals("140", pick(RememberedChoice(AudioCodec.M4A, SMALL)))
        // MP3 isn't offered: fall back to the overall recommendation.
        assertEquals("251", pick(RememberedChoice(AudioCodec.MP3, RECOMMENDED)))
    }

    @Test
    fun `a video with no formats at all still offers MP3`() {
        val info =
            VideoInfo("abcdefghijk", "t", durationSeconds = null, liveError = null, formats = emptyList())
        val choices = FormatRecommender.choicesFor(info)
        assertTrue(choices.isMp3Fallback)
        assertEquals(null, choices.recommended?.sizeBytes)
    }
}

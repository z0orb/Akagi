package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpOptionsTest {

    @Test
    fun `Opus and M4A are extracted without an encode bitrate`() {
        val expected =
            listOf("-f", "251", "-x", "--audio-format", "opus", "--embed-metadata", "--newline", "-o", "/t/o") +
                YtDlpOptions.coverArt()
        assertEquals(expected, YtDlpOptions.download("251", AudioCodec.OPUS, 135, "/t/o"))
    }

    @Test
    fun `cover art is squared by Akagi's plugin before it is embedded`() {
        val expected = listOf(
            "--embed-thumbnail",
            "--convert-thumbnails",
            "jpg",
            "--use-postprocessor",
            "AkagiCover:when=before_dl",
        )
        assertEquals(expected, YtDlpOptions.coverArt())
    }

    @Test
    fun `MP3 is encoded at the chosen bitrate`() {
        val args = YtDlpOptions.download(FormatRecommender.MP3_FORMAT_ID, AudioCodec.MP3, 192, "o")
        assertEquals(listOf("-f", "bestaudio", "-x", "--audio-format", "mp3", "--audio-quality", "192K"), args.take(7))
    }

    @Test
    fun `speed is read from the progress line`() {
        assertEquals("1.20MiB/s", YtDlpOptions.speedOf("[download]  42.0% of    3.40MiB at    1.20MiB/s ETA 00:02"))
        assertNull(YtDlpOptions.speedOf("[download]   0.0% of    3.40MiB at  Unknown B/s ETA Unknown"))
        assertNull(YtDlpOptions.speedOf("[ExtractAudio] Destination: x.opus"))
    }
}

package io.github.z0orb.akagi.playback

import io.github.z0orb.akagi.playback.PcmEncoding.FLOAT
import io.github.z0orb.akagi.playback.PcmEncoding.PCM_16
import io.github.z0orb.akagi.playback.PcmEncoding.PCM_24
import io.github.z0orb.akagi.playback.PcmEncoding.PCM_32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MixerFormatChooserTest {

    private val dac = listOf(
        PcmFormat(44_100, PCM_16, 2),
        PcmFormat(44_100, PCM_24, 2),
        PcmFormat(48_000, PCM_16, 2),
        PcmFormat(48_000, PCM_24, 2),
        PcmFormat(48_000, PCM_32, 2),
        PcmFormat(96_000, PCM_24, 2),
        PcmFormat(48_000, FLOAT, 1),
    )

    @Test
    fun `picks the track's rate`() {
        assertEquals(PcmFormat(44_100, PCM_24, 2), MixerFormatChooser.choose(dac, PcmFormat(44_100, FLOAT, 2)))
    }

    @Test
    fun `picks the deepest encoding at that rate`() {
        assertEquals(PcmFormat(48_000, PCM_32, 2), MixerFormatChooser.choose(dac, PcmFormat(48_000, FLOAT, 2)))
    }

    @Test
    fun `a 16-bit track still gets the deepest format`() {
        assertEquals(PcmFormat(48_000, PCM_32, 2), MixerFormatChooser.choose(dac, PcmFormat(48_000, PCM_16, 2)))
        assertEquals(PcmFormat(48_000, FLOAT, 1), MixerFormatChooser.choose(dac, PcmFormat(48_000, PCM_16, 1)))
    }

    @Test
    fun `channel count must match`() {
        assertNull(MixerFormatChooser.choose(dac, PcmFormat(44_100, FLOAT, 6)))
    }

    @Test
    fun `a rate the DAC lacks falls back to 48 kHz`() {
        assertEquals(PcmFormat(48_000, PCM_32, 2), MixerFormatChooser.choose(dac, PcmFormat(22_050, FLOAT, 2)))
        assertEquals(PcmFormat(48_000, FLOAT, 1), MixerFormatChooser.choose(dac, PcmFormat(44_100, FLOAT, 1)))
    }

    @Test
    fun `match off keeps 48 kHz for every track`() {
        assertEquals(
            PcmFormat(48_000, PCM_32, 2),
            MixerFormatChooser.choose(dac, PcmFormat(44_100, PCM_16, 2), matchRate = false),
        )
    }

    @Test
    fun `no usable format leaves the choice to Android`() {
        assertNull(MixerFormatChooser.choose(listOf(PcmFormat(96_000, PCM_24, 2)), PcmFormat(44_100, FLOAT, 2)))
        assertNull(MixerFormatChooser.choose(emptyList(), PcmFormat(48_000, FLOAT, 2)))
    }
}

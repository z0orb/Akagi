package io.github.z0orb.akagi.playback

import io.github.z0orb.akagi.playback.PcmEncoding.FLOAT
import io.github.z0orb.akagi.playback.PcmEncoding.PCM_16
import io.github.z0orb.akagi.playback.PcmEncoding.PCM_24
import io.github.z0orb.akagi.playback.PcmEncoding.PCM_32
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputFormatTest {

    private val us = Locale.US

    @Test
    fun `rates in kHz without trailing zeros`() {
        assertEquals("44.1 kHz", OutputFormat.rate(44_100, us))
        assertEquals("48 kHz", OutputFormat.rate(48_000, us))
        assertEquals("352.8 kHz", OutputFormat.rate(352_800, us))
        assertEquals("22.05 kHz", OutputFormat.rate(22_050, us))
    }

    @Test
    fun `depths and formats`() {
        assertEquals("24-bit", OutputFormat.depth(PCM_24))
        assertEquals("32-bit float", OutputFormat.depth(FLOAT))
        assertEquals("44.1 kHz · 24-bit", OutputFormat.format(PcmFormat(44_100, PCM_24, 2), us))
    }

    @Test
    fun `capability summary is a rate range plus depths`() {
        val caps = DacCapabilities(
            ratesHz = listOf(384_000, 44_100, 48_000, 44_100, 0),
            encodings = setOf(PCM_32, PCM_16, PCM_24),
        )
        assertEquals("44.1–384 kHz · 16/24/32-bit", OutputFormat.capabilities(caps, us))
    }

    @Test
    fun `capability summary with one rate and float`() {
        val caps = DacCapabilities(ratesHz = listOf(48_000), encodings = setOf(FLOAT, PCM_16))
        assertEquals("48 kHz · 16-bit · 32-bit float", OutputFormat.capabilities(caps, us))
    }

    @Test
    fun `capability summary is null when nothing is reported`() {
        assertNull(OutputFormat.capabilities(DacCapabilities(), us))
    }
}

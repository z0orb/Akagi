package io.github.z0orb.akagi.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputsTest {

    @Test
    fun `on API 33+ only the media route counts`() {
        assertTrue(mediaOnSpeaker(OutputKind.SPEAKER, listOf(OutputKind.USB_DAC)))
        assertFalse(mediaOnSpeaker(OutputKind.USB_DAC, emptyList()))
    }

    @Test
    fun `below API 33 any external output still connected means not yet`() {
        assertFalse(mediaOnSpeaker(null, listOf(OutputKind.SPEAKER, OutputKind.WIRED)))
        assertFalse(mediaOnSpeaker(null, listOf(OutputKind.SPEAKER, OutputKind.OTHER)))
        assertTrue(mediaOnSpeaker(null, listOf(OutputKind.SPEAKER)))
    }
}

package io.github.z0orb.akagi.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorPolicyTest {

    @Test
    fun `a missing file is removed even at the end of the queue`() {
        assertEquals(
            PlaybackErrorAction.REMOVE_AND_CONTINUE,
            PlaybackErrorPolicy.decide(fileMissing = true, hasNext = false, failuresInARow = 1, queueSize = 1),
        )
    }

    @Test
    fun `an unplayable file is skipped while a next track exists`() {
        assertEquals(
            PlaybackErrorAction.SKIP_TO_NEXT,
            PlaybackErrorPolicy.decide(fileMissing = false, hasNext = true, failuresInARow = 1, queueSize = 5),
        )
    }

    @Test
    fun `an unplayable last track stops playback`() {
        assertEquals(
            PlaybackErrorAction.STOP,
            PlaybackErrorPolicy.decide(fileMissing = false, hasNext = false, failuresInARow = 1, queueSize = 5),
        )
    }

    @Test
    fun `with repeat all, a queue where every track fails stops instead of looping`() {
        assertEquals(
            PlaybackErrorAction.SKIP_TO_NEXT,
            PlaybackErrorPolicy.decide(fileMissing = false, hasNext = true, failuresInARow = 2, queueSize = 3),
        )
        assertEquals(
            PlaybackErrorAction.STOP,
            PlaybackErrorPolicy.decide(fileMissing = false, hasNext = true, failuresInARow = 3, queueSize = 3),
        )
    }
}

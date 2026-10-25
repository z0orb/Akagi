package io.github.z0orb.akagi.playback

/** What the player does when a track can't be played (B-08). */
enum class PlaybackErrorAction {
    /** The file is gone or no longer readable: drop it from the queue and carry on. */
    REMOVE_AND_CONTINUE,

    /** The file is there but won't play (corrupt, truncated, unsupported): leave it and play the next one. */
    SKIP_TO_NEXT,

    /** Nothing playable is left; pause rather than retry the same track. */
    STOP,
}

object PlaybackErrorPolicy {
    /**
     * [failuresInARow] counts this error too. Once it reaches [queueSize], every track has failed since the last
     * one that played, so skipping on would loop for ever with Repeat All.
     */
    fun decide(fileMissing: Boolean, hasNext: Boolean, failuresInARow: Int, queueSize: Int): PlaybackErrorAction =
        when {
            fileMissing -> PlaybackErrorAction.REMOVE_AND_CONTINUE
            hasNext && failuresInARow < queueSize -> PlaybackErrorAction.SKIP_TO_NEXT
            else -> PlaybackErrorAction.STOP
        }
}

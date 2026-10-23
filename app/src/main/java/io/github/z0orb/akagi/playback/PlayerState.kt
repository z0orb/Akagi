package io.github.z0orb.akagi.playback

/** What the player UI needs; position is read separately, so it doesn't re-emit every frame. */
data class PlayerState(
    /** Null when the queue is empty: the player is hidden. */
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val source: QueueSource? = null,
)

data class NowPlaying(val trackId: Long, val title: String, val thumbnailPath: String?, val durationMs: Long)

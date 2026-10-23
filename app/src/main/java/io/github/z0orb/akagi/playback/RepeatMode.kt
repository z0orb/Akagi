package io.github.z0orb.akagi.playback

/** The repeat button cycles Off → All (the queue) → One (PLAN §7.9). */
enum class RepeatMode {
    OFF,
    ALL,
    ONE,
    ;

    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}

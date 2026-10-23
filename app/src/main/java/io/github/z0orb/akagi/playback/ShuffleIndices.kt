package io.github.z0orb.akagi.playback

import kotlin.random.Random

object ShuffleIndices {
    /**
     * A random permutation of `0 until count` that begins with [first], so the track the user picked
     * plays first and every other track still follows once.
     */
    fun startingWith(count: Int, first: Int, random: Random = Random): IntArray {
        if (first !in 0 until count) return (0 until count).shuffled(random).toIntArray()
        val rest = (0 until count).filter { it != first }.shuffled(random)
        return (listOf(first) + rest).toIntArray()
    }
}

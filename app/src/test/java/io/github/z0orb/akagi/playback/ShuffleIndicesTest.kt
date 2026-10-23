package io.github.z0orb.akagi.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class ShuffleIndicesTest {

    @Test
    fun `the chosen index comes first and every index appears once`() {
        repeat(20) { seed ->
            val order = ShuffleIndices.startingWith(count = 8, first = 5, random = Random(seed))
            assertEquals(5, order.first())
            assertEquals((0 until 8).toList(), order.sorted())
        }
    }

    @Test
    fun `the rest is actually shuffled`() {
        val orders = (0 until 20).map { ShuffleIndices.startingWith(8, 0, Random(it)).toList() }.toSet()
        assert(orders.size > 1)
    }

    @Test
    fun `an out-of-range first index still gives a full permutation`() {
        assertEquals((0 until 4).toList(), ShuffleIndices.startingWith(4, first = -1).sorted())
        assertEquals(emptyList<Int>(), ShuffleIndices.startingWith(0, first = 0).toList())
    }

    @Test
    fun `repeat cycles off, all, one`() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }
}

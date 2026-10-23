package io.github.z0orb.akagi.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * ExoPlayer's default shuffle order puts the start track anywhere, so tracks shuffled before it
 * would be skipped (unless repeat is on). This one always starts with the track the user picked.
 */
@UnstableApi
class StartFirstShuffleOrder private constructor(private val order: ShuffleOrder) : ShuffleOrder by order {

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder =
        StartFirstShuffleOrder(order.cloneAndInsert(insertionIndex, insertionCount))

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder =
        StartFirstShuffleOrder(order.cloneAndRemove(indexFrom, indexToExclusive))

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder =
        StartFirstShuffleOrder(order.cloneAndMove(indexFrom, indexToExclusive, newIndexFrom))

    override fun cloneAndClear(): ShuffleOrder = StartFirstShuffleOrder(order.cloneAndClear())

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        startingAt(insertionCount, startIndex)

    companion object {
        fun startingAt(count: Int, first: Int = C.INDEX_UNSET): StartFirstShuffleOrder =
            StartFirstShuffleOrder(DefaultShuffleOrder(ShuffleIndices.startingWith(count, first), Random.nextLong()))
    }
}

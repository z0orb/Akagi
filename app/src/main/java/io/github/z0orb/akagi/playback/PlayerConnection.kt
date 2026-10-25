package io.github.z0orb.akagi.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's handle on [PlaybackService] (PLAN §5): a [MediaController] turned into a [StateFlow].
 * Every call happens on the main thread, which is the controller's looper.
 */
@Singleton
class PlayerConnection @Inject constructor(@ApplicationContext private val context: Context) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val controller = MutableStateFlow<MediaController?>(null)
    private var pending: ListenableFuture<MediaController>? = null

    // Kept here, not in the session: it only labels the queue, and this object outlives the service.
    private var source: QueueSource? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    /**
     * Binds to the service while the UI is visible. Holding the binding in the background would keep
     * a paused service (and the process) alive, so [disconnect] runs when the activity stops.
     */
    fun connect() {
        if (pending != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        // The service went away under us (not our own disconnect): its queue is gone too.
                        if (this@PlayerConnection.controller.value === controller) {
                            forget()
                            _state.value = PlayerState()
                        }
                    }
                },
            )
            .buildAsync()
        pending = future
        future.addListener({ onConnected(future) }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        val future = pending
        forget()
        future?.let { MediaController.releaseFuture(it) }
    }

    /** Replaces the queue and starts playing it. False when the player service couldn't be reached. */
    suspend fun play(queue: PlayQueue): Boolean {
        val player = awaitController() ?: return false
        source = queue.source
        queue.shuffle?.let { player.shuffleModeEnabled = it }
        player.setMediaItems(queue.items.map { it.toMediaItem() }, queue.startIndex, 0L)
        player.prepare()
        player.play()
        return true
    }

    @OptIn(UnstableApi::class)
    fun togglePlayPause() {
        // Also restarts a finished queue and re-prepares after an error.
        controller.value?.let { Util.handlePlayPauseButtonAction(it) }
    }

    fun pause() {
        controller.value?.pause()
    }

    fun next() {
        controller.value?.seekToNext()
    }

    /** Restarts the track after the first few seconds, otherwise goes to the previous one. */
    fun previous() {
        controller.value?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller.value?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller.value?.let { it.repeatMode = it.repeatMode.toRepeatMode().next().toPlayerRepeatMode() }
    }

    /** Takes a deleted track out of the queue; the next one plays if it was current (P5.10). */
    fun remove(trackId: Long) {
        val player = controller.value ?: return
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).trackId == trackId) player.removeMediaItem(index)
        }
    }

    /** Read on demand by the seekbar's ticker rather than pushed through [state]. */
    fun positionMs(): Long = controller.value?.currentPosition ?: 0L

    // A bind that fails or never answers used to leave the tap waiting for ever (B-11). One fresh attempt is made,
    // since a stale pending future would otherwise block every later connect().
    private suspend fun awaitController(): MediaController? {
        repeat(CONNECT_ATTEMPTS) {
            connect()
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { controller.filterNotNull().first() }?.let { return it }
            disconnect()
        }
        return null
    }

    private fun onConnected(future: ListenableFuture<MediaController>) {
        if (pending !== future) return
        val player = try {
            future.get()
        } catch (_: ExecutionException) {
            // The service couldn't be bound; the next connect() tries again.
            pending = null
            return
        }
        player.addListener(listener)
        controller.value = player
        publish(player)
    }

    private fun forget() {
        controller.value?.removeListener(listener)
        controller.value = null
        pending = null
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        if (item == null) source = null
        _state.value = PlayerState(
            current = item?.let {
                val metadata = it.mediaMetadata
                NowPlaying(
                    trackId = it.trackId ?: return@let null,
                    title = metadata.title?.toString().orEmpty(),
                    thumbnailPath = metadata.artworkUri?.path,
                    durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: metadata.durationMs ?: 0L,
                )
            },
            isPlaying = player.isPlaying,
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode.toRepeatMode(),
            source = source,
        )
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private companion object {
        const val CONNECT_ATTEMPTS = 2
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}

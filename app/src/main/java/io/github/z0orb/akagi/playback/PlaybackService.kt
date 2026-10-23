package io.github.z0orb.akagi.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import io.github.z0orb.akagi.MainActivity
import io.github.z0orb.akagi.R

/**
 * Owns the player, so music keeps going with the screen off and after the app is swiped away
 * (PLAN §7.8). Media3 runs it in the foreground while playing and lets it stop once paused.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                // Another app taking focus pauses Akagi; a transient loss ducks or pauses and resumes (R11).
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setShuffleOrder(StartFirstShuffleOrder.startingAt(0))
        player.addListener(ShuffleFromCurrentTrack(player))
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notif_channel_playback)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_akagi) },
        )
        session = MediaSession.Builder(this, TitleAndArtworkOnly(player))
            .setSessionActivity(openAppIntent())
            .setCallback(MinimalSystemControls)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The files carry tags such as the artist; the system shows only what Akagi sets, title and artwork (R9). */
    private class TitleAndArtworkOnly(player: Player) : ForwardingPlayer(player) {
        override fun getMediaMetadata(): MediaMetadata = currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
    }

    /** Turning shuffle on mid-queue starts the shuffled order at the current track, so none is skipped. */
    private class ShuffleFromCurrentTrack(private val player: ExoPlayer) : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled && player.mediaItemCount > 0) {
                player.setShuffleOrder(
                    StartFirstShuffleOrder.startingAt(player.mediaItemCount, player.currentMediaItemIndex),
                )
            }
        }
    }

    /**
     * The notification, lock screen and Bluetooth controls get only play/pause, previous and next:
     * no seek-in-track command means no system seekbar, and no custom layout means no shuffle/repeat (§7.8).
     */
    private object MinimalSystemControls : MediaSession.Callback {
        private val systemCommands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_STOP,
            )
            .build()

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = if (session.isMediaNotificationController(controller)) {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(systemCommands)
                .setAvailableSessionCommands(SessionCommands.EMPTY)
                .build()
        } else {
            super.onConnect(session, controller)
        }
    }
}

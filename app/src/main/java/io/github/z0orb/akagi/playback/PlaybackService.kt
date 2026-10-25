package io.github.z0orb.akagi.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import dagger.hilt.android.AndroidEntryPoint
import io.github.z0orb.akagi.MainActivity
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.data.prefs.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the player, so music keeps going with the screen off and after the app is swiped away
 * (PLAN §7.8). Media3 runs it in the foreground while playing and lets it stop once paused.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var headphones: HeadphoneDisconnectHandler

    @Inject
    lateinit var routes: AudioRouteMonitor

    @Inject
    lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dacOutput: DacOutput? = null

    private var session: MediaSession? = null
    private var noisyReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val dac = DacOutput(this, routes).also { dacOutput = it }
        val player = ExoPlayer.Builder(this, dac.renderersFactory())
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                // Another app taking focus pauses Akagi; a transient loss ducks or pauses and resumes (R11).
                true,
            )
            // Unplugging is handled below: ExoPlayer would pause first, hiding whether Akagi was playing.
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setShuffleOrder(StartFirstShuffleOrder.startingAt(0))
        player.addListener(ShuffleFromCurrentTrack(player))
        player.addAnalyticsListener(ReportOutputFormat(routes))
        player.addListener(
            SkipMissingFiles(player) {
                Toast.makeText(this, R.string.player_skipped_missing, Toast.LENGTH_SHORT).show()
            },
        )
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
        followUsbDac(player, dac)
        noisyReceiver = headphones.register(this) {
            if (player.playWhenReady) {
                player.pause()
                headphones.muteSpeakerOnceRouteSettles()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        dacOutput?.release()
        dacOutput = null
        noisyReceiver?.let(::unregisterReceiver)
        noisyReceiver = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Plays through a connected USB DAC while "Play through the USB DAC" is on, and keeps its mixer
     * format in step with the settings (PLAN §7.19).
     */
    private fun followUsbDac(player: ExoPlayer, dac: DacOutput) {
        val dacRoute = routes.route.map { (it.kind == OutputKind.USB_DAC) to it.dacConnected }.distinctUntilChanged()
        val options = settings.settings.map { it.preferUsbDac to it.matchSampleRate }.distinctUntilChanged()
        var applied: Triple<Int?, Boolean, Boolean>? = null
        scope.launch {
            combine(dacRoute, options, ::Pair).collect { (dacRoute, options) ->
                val onDac = dacRoute.first
                val (prefer, matchRate) = options
                val usbDac = routes.usbDac
                val target = usbDac?.takeIf { prefer || onDac }
                val key = Triple(target?.id, prefer, matchRate)
                val apply = {
                    player.setPreferredAudioDevice(usbDac?.takeIf { prefer })
                    dac.update(target, matchRate)
                }
                // The open AudioTrack keeps its old device and mixer (on HyperOS it can go silent), so the
                // change only takes hold on a new one. An unplug needs none: Android moves it to the speaker.
                if (applied != null && key != applied && usbDac != null) reopenAudio(player, apply) else apply()
                applied = key
            }
        }
    }

    /**
     * Rebuilds the AudioTrack around [apply] at the same position; stop() keeps the queue, position and
     * playWhenReady. Clearing the DAC's mixer format reopens its output in the background, and a track
     * built during that stays silent on HyperOS, so the new one waits for it.
     */
    private suspend fun reopenAudio(player: ExoPlayer, apply: () -> Unit) {
        val active = player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        if (active) player.stop()
        apply()
        if (!active) return
        delay(REOPEN_SETTLE_MS)
        player.prepare()
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

    /**
     * A queued file deleted outside Akagi would stop playback with an error; it leaves the queue instead
     * and the next track plays. The library flags it as missing on the next resume (D-042).
     */
    private class SkipMissingFiles(private val player: ExoPlayer, private val onSkip: () -> Unit) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode != PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) return
            player.removeMediaItem(player.currentMediaItemIndex)
            if (player.mediaItemCount > 0) player.prepare()
            onSkip()
        }
    }

    /** Tells the monitor what Akagi sends, for Settings → Audio output and the player chip. */
    private class ReportOutputFormat(private val routes: AudioRouteMonitor) : AnalyticsListener {
        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            config: AudioSink.AudioTrackConfig,
        ) = routes.reportSent(pcmFormatOf(config.sampleRate, config.encoding, config.channelConfig))

        override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) =
            routes.reportSent(null)
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

    private companion object {
        const val REOPEN_SETTLE_MS = 400L
    }
}

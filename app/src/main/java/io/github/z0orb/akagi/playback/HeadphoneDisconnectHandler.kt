package io.github.z0orb.akagi.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After headphones (wired, Bluetooth, TWS, a USB DAC or any other external output) drop while Akagi is
 * playing, sets the music volume to 0 once, so pressing play again can't blast from the speaker
 * (R4, PLAN §7.11).
 */
@Singleton
class HeadphoneDisconnectHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val routes: AudioRouteMonitor,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var muting: Job? = null

    /**
     * Android keeps one volume per output device. Zeroing it before the route has moved to the speaker
     * would zero the headphones' volume and leave the speaker's as loud as before, so this waits first.
     */
    fun muteSpeakerOnceRouteSettles() {
        if (muting?.isActive == true) return
        muting = scope.launch {
            val settled = withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                while (!mediaOnSpeaker(routes.mediaRouteKind(), routes.connectedKinds())) delay(POLL_MS)
            }
            // Still on a headset after the timeout (e.g. a second pair is connected): leave its volume alone.
            if (settled != null) {
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
            } else {
                Log.w(TAG, "Media still on ${routes.mediaRouteKind()} after unplug; volume left as is")
            }
        }
    }

    /** Registers [onNoisy] for "audio becoming noisy" (the output is about to fall back to the speaker). */
    fun register(context: Context, onNoisy: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onNoisy()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    private companion object {
        const val TAG = "HeadphoneDisconnect"

        // Android drops a noisy device about 1 s after the broadcast; a USB DAC can take longer than wired.
        const val SETTLE_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
    }
}

package io.github.z0orb.akagi.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
 * After headphones (wired, Bluetooth, TWS) drop while Akagi is playing, sets the music volume to 0 once,
 * so pressing play again can't blast from the speaker (R4, PLAN §7.11).
 */
@Singleton
class HeadphoneDisconnectHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
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
                while (headsetConnected()) delay(POLL_MS)
            }
            // Still on a headset after the timeout (e.g. a second pair is connected): leave its volume alone.
            if (settled != null) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
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

    private fun headsetConnected(): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 1_500L
        const val POLL_MS = 100L

        // The BLE and USB types are plain ints, so listing them is safe below the API they were added in.
        val headsetTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
    }
}

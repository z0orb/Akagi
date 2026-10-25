package io.github.z0orb.akagi.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Sends high-resolution (float) PCM and, on Android 14+, opens a USB DAC at each track's own rate
 * so Android doesn't resample it, or at a fixed 48 kHz with "Match" off (PLAN §7.19, D-067).
 * Owned by [PlaybackService].
 */
@OptIn(UnstableApi::class)
class DacOutput(private val context: Context, private val routes: AudioRouteMonitor) {

    private val audio = context.getSystemService(AudioManager::class.java)

    @Volatile
    private var target: AudioDeviceInfo? = null

    @Volatile
    private var matchSampleRate = true

    // What was last asked of Android, applied or not, so a rebuilt AudioTrack can't loop on a refusal.
    private var lastDevice: AudioDeviceInfo? = null
    private var lastFormat: PcmFormat? = null

    fun renderersFactory(): RenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioOutputProvider(
                AudioTrackAudioOutputProvider.Builder(context)
                    // Runs on the playback thread right before each AudioTrack is built.
                    .setAudioTrackBuilderModifier { _, config -> onTrackBuilding(config) }
                    .build(),
            )
            .build()
    }

    /**
     * [dac] is the USB DAC media will play on, or null. Applied at once for the track playing now,
     * so a DAC plugged in mid-track runs at its rate too.
     */
    fun update(dac: AudioDeviceInfo?, matchSampleRate: Boolean) {
        target = dac
        this.matchSampleRate = matchSampleRate
        choose(routes.route.value.sent)
    }

    fun release() {
        target = null
        choose(null)
    }

    private fun onTrackBuilding(config: AudioOutputProvider.OutputConfig) {
        if (config.isOffload || config.isTunneling) return
        choose(pcmFormatOf(config.sampleRate, config.encoding, config.channelMask))
    }

    @Synchronized
    private fun choose(track: PcmFormat?) {
        val dac = target?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE }
        val format = if (dac == null || track == null) {
            null
        } else {
            MixerFormatChooser.choose(routes.route.value.caps?.mixerFormats.orEmpty(), track, matchSampleRate)
        }
        // No track playing keeps the DAC's last format, so a pause or a gap doesn't reopen it.
        if (dac != null && track == null) return
        if (dac?.id == lastDevice?.id && format == lastFormat) return
        clear()
        if (dac == null || format == null) return
        apply(dac, format)
    }

    private fun apply(dac: AudioDeviceInfo, format: PcmFormat) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        lastDevice = dac
        lastFormat = format
        val attrs = routes.mixerAttributesFor(format) ?: return
        val applied = runCatching {
            audio.setPreferredMixerAttributes(AudioRouteMonitor.mediaAttributes, dac, attrs)
        }.getOrDefault(false)
        if (applied) {
            routes.reportDacFormat(format)
        } else {
            Log.w(TAG, "Android refused $format on ${dac.productName}; it chooses the format instead")
            clearPreferred(dac)
        }
    }

    private fun clear() {
        val device = lastDevice ?: return
        lastDevice = null
        lastFormat = null
        clearPreferred(device)
        routes.reportDacFormat(null)
    }

    private fun clearPreferred(dac: AudioDeviceInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        // Throws once the DAC is unplugged; Android has dropped the preference with it then.
        runCatching { audio.clearPreferredMixerAttributes(AudioRouteMonitor.mediaAttributes, dac) }
    }

    private companion object {
        const val TAG = "DacOutput"
    }
}

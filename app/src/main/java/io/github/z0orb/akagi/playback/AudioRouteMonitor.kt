package io.github.z0orb.akagi.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where media audio goes right now and what a USB DAC can do (PLAN §7.19). Shared by the playback
 * service and the UI in the app process, like [PlayerConnection].
 */
@Singleton
class AudioRouteMonitor @Inject constructor(@ApplicationContext context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _route = MutableStateFlow(AudioRoute())
    val route: StateFlow<AudioRoute> = _route.asStateFlow()

    /** The connected USB DAC; read from the playback thread by [DacOutput]. */
    @Volatile
    var usbDac: AudioDeviceInfo? = null
        private set

    // The DAC's mixer formats as Android objects, so DacOutput passes back exactly what was offered.
    @Volatile
    private var mixerAttributes: Map<PcmFormat, AudioMixerAttributes> = emptyMap()

    private val refreshTask = Runnable { refresh() }

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshSoon()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshSoon()
    }

    init {
        audio.registerAudioDeviceCallback(callback, main)
        refresh()
    }

    /** The kind of output media plays on now; null below API 33, where Android doesn't say. */
    fun mediaRouteKind(): OutputKind? = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {
        mediaDevice()?.let { outputKindOf(it.type) }
    } else {
        null
    }

    fun connectedKinds(): List<OutputKind> = outputs().mapNotNull { outputKindOf(it.type) }

    fun mixerAttributesFor(format: PcmFormat): AudioMixerAttributes? = mixerAttributes[format]

    /** What Akagi's AudioTrack sends; called by the player when a track starts or stops. */
    fun reportSent(format: PcmFormat?) = _route.update { it.copy(sent = format) }

    /** The mixer format Akagi set on the DAC, or null when Android chooses. */
    fun reportDacFormat(format: PcmFormat?) = _route.update { it.copy(dacFormat = format) }

    // Routing can lag the device callback a little, so look again once it has settled.
    private fun refreshSoon() {
        refresh()
        main.removeCallbacks(refreshTask)
        main.postDelayed(refreshTask, SETTLE_MS)
    }

    private fun refresh() {
        val outputs = outputs()
        val dac = outputs.firstOrNull { outputKindOf(it.type) == OutputKind.USB_DAC }
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) mediaDevice() else byPriority(outputs)
        val kind = device?.let { outputKindOf(it.type) } ?: OutputKind.SPEAKER
        if (dac?.id != usbDac?.id) mixerAttributes = dac?.let(::supportedMixerAttributes).orEmpty()
        usbDac = dac
        val caps = dac?.let(::capabilities)
        _route.update { old ->
            AudioRoute(
                kind = kind,
                name = device?.productName?.toString()?.takeIf {
                    kind == OutputKind.USB_DAC ||
                        kind == OutputKind.BLUETOOTH
                },
                dacConnected = dac != null,
                caps = caps,
                sent = old.sent,
                dacFormat = old.dacFormat.takeIf { dac != null },
            )
        }
    }

    private fun outputs(): List<AudioDeviceInfo> = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun mediaDevice(): AudioDeviceInfo? = runCatching {
        audio.getAudioDevicesForAttributes(mediaAttributes).firstOrNull()
    }.getOrNull()

    // Android 10–12 don't say where media goes; this is the order they route it in.
    private fun byPriority(outputs: List<AudioDeviceInfo>): AudioDeviceInfo? = listOf(
        OutputKind.USB_DAC,
        OutputKind.WIRED,
        OutputKind.BLUETOOTH,
        OutputKind.SPEAKER,
    ).firstNotNullOfOrNull { kind -> outputs.firstOrNull { outputKindOf(it.type) == kind } }

    private fun capabilities(dac: AudioDeviceInfo): DacCapabilities {
        val mixer = mixerAttributes.keys
        return DacCapabilities(
            ratesHz = (dac.sampleRates.toList() + mixer.map { it.rateHz }).distinct().sorted(),
            encodings = (dac.encodings.toList().mapNotNull { pcmEncodingOf(it) } + mixer.map { it.encoding }).toSet(),
            mixerFormats = mixer.toList(),
        )
    }

    private fun supportedMixerAttributes(dac: AudioDeviceInfo): Map<PcmFormat, AudioMixerAttributes> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyMap()
        val supported = runCatching { audio.getSupportedMixerAttributes(dac) }.getOrNull().orEmpty()
        // Bit-perfect is out of scope (D-063): it would bypass Android's volume and other apps' sounds.
        return supported
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT }
            .mapNotNull { attrs ->
                val format = attrs.format
                pcmFormatOf(format.sampleRate, format.encoding, format.channelMask)?.let { it to attrs }
            }
            .toMap()
    }

    companion object {
        private const val SETTLE_MS = 500L

        val mediaAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}

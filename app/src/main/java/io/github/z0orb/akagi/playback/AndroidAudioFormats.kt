package io.github.z0orb.akagi.playback

import android.media.AudioDeviceInfo
import android.media.AudioFormat

/** Null for encodings Akagi doesn't show or choose, such as 8-bit or compressed ones. */
internal fun pcmEncodingOf(androidEncoding: Int): PcmEncoding? = when (androidEncoding) {
    AudioFormat.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> PcmEncoding.PCM_24
    AudioFormat.ENCODING_PCM_32BIT -> PcmEncoding.PCM_32
    AudioFormat.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
    else -> null
}

internal fun pcmFormatOf(sampleRate: Int, androidEncoding: Int, channelMask: Int): PcmFormat? =
    pcmEncodingOf(androidEncoding)?.let { PcmFormat(sampleRate, it, Integer.bitCount(channelMask)) }

/**
 * Null for outputs that aren't a place media can go (earpiece, telephony, …). Every external output
 * counts, so unplugging any of them is handled like headphones (B-01). The USB and BLE types are
 * plain ints, so listing them is safe below the API they were added in.
 */
internal fun outputKindOf(type: Int): OutputKind? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputKind.SPEAKER

    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.USB_DAC

    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE,
    -> OutputKind.WIRED

    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID,
    -> OutputKind.BLUETOOTH

    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_DOCK_ANALOG,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC,
    -> OutputKind.OTHER

    else -> null
}

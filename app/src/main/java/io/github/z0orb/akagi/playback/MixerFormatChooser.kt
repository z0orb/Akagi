package io.github.z0orb.akagi.playback

/** Picks the DAC mixer format for a track, so Android doesn't resample it (PLAN §7.19). */
object MixerFormatChooser {
    private val deepestFirst = listOf(PcmEncoding.FLOAT, PcmEncoding.PCM_32, PcmEncoding.PCM_24, PcmEncoding.PCM_16)

    /** The rate a DAC runs at when "Match" is off or can't be honoured; what Android itself opens most DACs at. */
    const val FIXED_RATE_HZ = 48_000

    /**
     * Same rate and channel count as [track] when [matchRate], in the deepest encoding the DAC offers.
     * Android mixes in float and applies the volume there, so a deeper DAC format keeps quiet volumes
     * clean even for a 16-bit track (the phone's decoders give 16-bit, D-066).
     * Otherwise, or when the DAC can't run at the track's rate, [FIXED_RATE_HZ]: Android's own path to a
     * USB DAC is silent on some phones (HyperOS, D-067), so Akagi always sets a mixer format when it can.
     * Null when the DAC offers neither.
     */
    fun choose(supported: List<PcmFormat>, track: PcmFormat, matchRate: Boolean = true): PcmFormat? {
        val rates = if (matchRate) listOf(track.rateHz, FIXED_RATE_HZ) else listOf(FIXED_RATE_HZ)
        return rates.firstNotNullOfOrNull { rate ->
            val matching = supported.filter { it.rateHz == rate && it.channels == track.channels }
            deepestFirst.firstNotNullOfOrNull { encoding -> matching.firstOrNull { it.encoding == encoding } }
        }
    }
}

package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
import kotlin.math.abs

/** A label shown next to a bitrate in the format sheet (PLAN §7.4). */
enum class FormatTier { RECOMMENDED, DATA_SAVER, SMALL, HIGHEST }

data class FormatOption(
    val formatId: String,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    val sizeBytes: Long?,
    val tier: FormatTier?,
)

data class FormatChoices(
    /** Codecs in display order; each list is sorted by bitrate. MP3 appears only as the fallback. */
    val options: Map<AudioCodec, List<FormatOption>>,
    val recommended: FormatOption?,
) {
    val isMp3Fallback: Boolean get() = options.keys == setOf(AudioCodec.MP3)

    fun recommendedFor(codec: AudioCodec): FormatOption? = options[codec]?.firstOrNull {
        it.tier ==
            FormatTier.RECOMMENDED
    }
}

/** The codec and tier the user picked last time; the sheet starts there when it can. */
data class RememberedChoice(val codec: AudioCodec, val tier: FormatTier?)

object FormatRecommender {
    /** itag 251 (Opus ~160 kbps) is transparent for most listeners; itag 140 (AAC 128 kbps) for M4A. */
    private val targetKbps = mapOf(AudioCodec.OPUS to 160, AudioCodec.M4A to 128)
    private const val DATA_SAVER_MAX_KBPS = 58
    private const val SMALL_MAX_KBPS = 90
    private const val HIGHEST_MIN_KBPS = 200

    /** Encoded from yt-dlp's `bestaudio` with ffmpeg when YouTube offers no Opus or M4A. */
    const val MP3_FORMAT_ID = "bestaudio"
    private val mp3Kbps = listOf(128, 192, 320)
    private const val MP3_RECOMMENDED_KBPS = 192

    fun choicesFor(info: VideoInfo): FormatChoices {
        val byCodec = info.formats.groupBy { it.codec }
        val options = listOf(AudioCodec.OPUS, AudioCodec.M4A)
            .mapNotNull { codec -> byCodec[codec]?.let { codec to rank(codec, it) } }
            .toMap()
            .ifEmpty { mapOf(AudioCodec.MP3 to mp3Options(info.durationSeconds)) }
        val recommended = options.values.first().firstOrNull { it.tier == FormatTier.RECOMMENDED }
        return FormatChoices(options, recommended)
    }

    /** Where the sheet's selection starts: the remembered codec and tier if offered, else the recommendation. */
    fun initialSelection(choices: FormatChoices, remembered: RememberedChoice?): FormatOption? {
        val codecOptions = remembered?.let { choices.options[it.codec] } ?: return choices.recommended
        return codecOptions.firstOrNull { it.tier == remembered.tier && it.tier != null }
            ?: choices.recommendedFor(remembered.codec)
            ?: choices.recommended
    }

    private fun rank(codec: AudioCodec, formats: List<AudioFormat>): List<FormatOption> {
        val target = targetKbps.getValue(codec)
        val best = formats.minWith(
            compareBy<AudioFormat> {
                abs(it.bitrateKbps - target)
            }.thenByDescending { it.bitrateKbps },
        )
        return formats.sortedBy { it.bitrateKbps }.map { format ->
            FormatOption(
                formatId = format.formatId,
                codec = codec,
                bitrateKbps = format.bitrateKbps,
                sizeBytes = format.sizeBytes,
                tier = if (format == best) FormatTier.RECOMMENDED else tierOf(format.bitrateKbps),
            )
        }
    }

    private fun tierOf(kbps: Int): FormatTier? = when {
        kbps <= DATA_SAVER_MAX_KBPS -> FormatTier.DATA_SAVER
        kbps <= SMALL_MAX_KBPS -> FormatTier.SMALL
        kbps > HIGHEST_MIN_KBPS -> FormatTier.HIGHEST
        else -> null
    }

    private fun mp3Options(durationSeconds: Double?): List<FormatOption> = mp3Kbps.map { kbps ->
        FormatOption(
            formatId = MP3_FORMAT_ID,
            codec = AudioCodec.MP3,
            bitrateKbps = kbps,
            sizeBytes = durationSeconds?.let { AudioFormatMapper.estimateBytes(kbps.toDouble(), it) },
            tier = if (kbps == MP3_RECOMMENDED_KBPS) FormatTier.RECOMMENDED else tierOf(kbps),
        )
    }
}

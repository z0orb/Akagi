package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AudioCodec
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Maps yt-dlp's `-J` JSON to [VideoInfo] (PLAN §7.4). Keeps audio-only Opus and M4A formats, drops DRC
 * variants and DRM, and for multi-language videos keeps only the original/default audio track.
 */
object AudioFormatMapper {
    private val liveStatuses = setOf("is_live", "is_upcoming")

    fun parse(json: String): VideoInfo {
        val root = JSONObject(json)
        val duration = root.optDouble("duration").takeUnless { it.isNaN() }
        val candidates = root.optJSONArray("formats")
            ?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }
            .orEmpty()
            .filter(::isPlainAudio)
        val preferredLanguage = candidates.maxOfOrNull(::languagePreference)
        val formats = candidates
            .filter { languagePreference(it) == preferredLanguage }
            .mapNotNull { toAudioFormat(it, duration) }
            // Different clients can list the same stream twice; keep one per codec and bitrate.
            .groupBy { it.codec to it.bitrateKbps }
            .map { (_, same) -> same.firstOrNull { it.sizeBytes != null } ?: same.first() }
            .sortedWith(compareBy(AudioFormat::codec, AudioFormat::bitrateKbps))
        return VideoInfo(
            videoId = root.getString("id"),
            title = root.optString("title").ifBlank { root.getString("id") },
            durationSeconds = duration,
            isLiveOrUpcoming = root.optString("live_status") in liveStatuses || root.optBoolean("is_live"),
            formats = formats,
        )
    }

    private fun isPlainAudio(format: JSONObject): Boolean {
        val id = format.optString("format_id")
        val note = format.optString("format_note")
        return format.optString("vcodec") == "none" &&
            format.optString("acodec").let { it.isNotEmpty() && it != "none" } &&
            !id.endsWith("-drc") &&
            !note.contains("DRC", ignoreCase = true) &&
            !format.optBoolean("has_drm")
    }

    // yt-dlp gives the original track the highest language_preference; formats without one tie at the default.
    private fun languagePreference(format: JSONObject): Int = format.optInt("language_preference", -1)

    private fun toAudioFormat(format: JSONObject, duration: Double?): AudioFormat? {
        val acodec = format.optString("acodec")
        val codec = when {
            acodec.startsWith("opus") -> AudioCodec.OPUS
            acodec.startsWith("mp4a") -> AudioCodec.M4A
            else -> return null
        }
        val kbps = sequenceOf("abr", "tbr").map { format.optDouble(it) }.firstOrNull { !it.isNaN() && it > 0 }
            ?: return null
        val size = sequenceOf("filesize", "filesize_approx").map { format.optLong(it) }.firstOrNull { it > 0 }
            ?: duration?.let { estimateBytes(kbps, it) }
        return AudioFormat(format.getString("format_id"), codec, kbps.roundToInt(), size)
    }

    fun estimateBytes(kbps: Double, durationSeconds: Double): Long = (kbps * 1000 / 8 * durationSeconds).toLong()
}

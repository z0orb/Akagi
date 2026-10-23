package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AppError

/** Turns yt-dlp's stderr into an [AppError] (PLAN §7.16). Order matters: the most specific signal wins. */
object YtDlpErrorClassifier {
    private val rules: List<Pair<AppError, List<String>>> = listOf(
        AppError.AgeRestricted to listOf(
            "confirm your age",
            "age-restricted",
            "age restricted",
            "inappropriate for some users",
        ),
        AppError.MembersOnly to listOf("members-only", "members only", "join this channel"),
        AppError.BotCheck to listOf("not a bot", "sign in to confirm you"),
        AppError.Private to listOf("private video"),
        AppError.GeoBlocked to listOf("available in your country", "geo restrict", "geo-restrict"),
        AppError.LiveOrUpcoming to listOf("live event will begin", "premieres in", "this live stream", "is_live"),
        AppError.Network to listOf(
            "unable to resolve host",
            "failed to resolve",
            "name or service not known",
            "no address associated",
            "temporary failure in name resolution",
            "network is unreachable",
            "timed out",
            "connection refused",
            "connection reset",
        ),
        AppError.Unavailable to listOf("video unavailable", "this video is unavailable", "has been removed"),
        AppError.ExtractorOutdated to listOf(
            "http error 403",
            "unable to extract",
            "nsig",
            "signature",
            "n challenge",
            "js challenge",
            "please report this issue",
            "requested format is not available",
            "only images are available",
        ),
    )

    fun classify(output: String): AppError {
        val text = output.lowercase()
        return rules.firstOrNull { (_, signals) -> signals.any(text::contains) }?.first
            ?: AppError.Unknown(firstErrorLine(output))
    }

    /** The line users can act on: yt-dlp's own `ERROR:` line when there is one. */
    fun firstErrorLine(output: String): String {
        val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty)
        return (lines.firstOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull())
            ?.removePrefix("ERROR:")?.trim()
            .orEmpty()
    }
}

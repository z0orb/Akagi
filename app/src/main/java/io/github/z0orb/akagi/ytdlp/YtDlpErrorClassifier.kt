package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AppError

/** Turns yt-dlp's stderr into an [AppError] (PLAN §7.16). Order matters: the most specific signal wins. */
object YtDlpErrorClassifier {
    private class Rule(val signals: List<String>, val error: (output: String) -> AppError)

    private fun rule(error: AppError, vararg signals: String) = Rule(signals.toList()) { error }

    private val rules: List<Rule> = listOf(
        rule(
            AppError.AgeRestricted,
            "confirm your age",
            "age-restricted",
            "age restricted",
            "inappropriate for some users",
        ),
        rule(AppError.MembersOnly, "members-only", "members only", "join this channel"),
        rule(AppError.BotCheck, "not a bot", "sign in to confirm you"),
        rule(AppError.Private, "private video"),
        rule(AppError.GeoBlocked, "available in your country", "geo restrict", "geo-restrict"),
        // yt-dlp refuses upcoming streams and premieres with YouTube's own wording, which says when they start.
        Rule(listOf("live event will begin", "premieres in", "premiere will begin", "will begin shortly")) {
            AppError.Upcoming(reason(it))
        },
        rule(
            AppError.Network,
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
        rule(
            AppError.Unavailable,
            "video unavailable",
            "this video is unavailable",
            "has been removed",
            "recording is not available",
        ),
        rule(
            AppError.ExtractorOutdated,
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
        return rules.firstOrNull { rule -> rule.signals.any(text::contains) }?.error?.invoke(output)
            ?: AppError.Unknown(firstErrorLine(output))
    }

    /** The line users can act on: yt-dlp's own `ERROR:` line when there is one. */
    fun firstErrorLine(output: String): String {
        val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty)
        return (lines.firstOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull())
            ?.removePrefix("ERROR:")?.trim()
            .orEmpty()
    }

    private val extractorPrefix = Regex("""^\[[^\]]+]\s*[\w-]+:\s*""")

    /** YouTube's message without yt-dlp's "[youtube] videoId: " prefix. */
    fun reason(output: String): String = firstErrorLine(output).replaceFirst(extractorPrefix, "")
}

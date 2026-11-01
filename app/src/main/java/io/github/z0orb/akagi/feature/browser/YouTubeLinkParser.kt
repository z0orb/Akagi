package io.github.z0orb.akagi.feature.browser

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Turns pasted or typed text into something to open (PLAN §7.2), and recognises video pages for the FAB.
 * Only the video ID and the playlist survive (D-073): `t`, `si`, `pp` and friends are dropped on purpose.
 */
object YouTubeLinkParser {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val listIdPattern = Regex("^[A-Za-z0-9_-]{2,128}$")
    private const val MUSIC_HOST = "music.youtube.com"

    // Share sheets wrap the link in a title or sentence, so search for it. The lookbehind rejects
    // hosts like "notyoutube.com" and paths like "example.com/youtube.com"; the lookahead rejects
    // "youtube.com.evil.com".
    private val linkPattern = Regex(
        """(?<![\w.\-/])(?:https?://)?(?:[a-z0-9-]+\.)*(?:youtube\.com|youtube-nocookie\.com|youtu\.be)(?=[/?#:\s]|$)\S*""",
        RegexOption.IGNORE_CASE,
    )
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>\"'"
    private val idPathPrefixes = setOf("shorts", "live", "embed", "v", "e")
    private const val URI_ILLEGAL_CHARS = " \"<>\\^`{|}"

    /** Returns null for blank input. Plain text searches [site], the site on screen. */
    fun parse(input: String, site: Site = Site.YOUTUBE): LinkTarget? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val link = linkPattern.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }
        val uri = link?.let(::toUri) ?: return LinkTarget.Search(text, site)
        val videoId = videoIdOf(uri) ?: return LinkTarget.Page(pageUrlOf(uri))
        val linkSite = if (uri.host.equals(MUSIC_HOST, ignoreCase = true)) Site.YOUTUBE_MUSIC else Site.YOUTUBE
        return LinkTarget.Video(videoId, linkSite, queryParams(uri)["list"]?.takeIf(listIdPattern::matches))
    }

    /** The site of a page the WebView navigated to; null outside YouTube (sign-in, about:blank). */
    fun siteOf(url: String): Site? {
        val host = toUri(url)?.host?.lowercase() ?: return null
        return when {
            host == MUSIC_HOST -> Site.YOUTUBE_MUSIC
            isYouTubeHost(host) -> Site.YOUTUBE
            else -> null
        }
    }

    /** The video shown by a page the WebView navigated to: only watch and Shorts pages count. */
    fun videoPageOf(url: String): VideoRef? {
        val uri = toUri(url) ?: return null
        if (!isYouTubeHost(uri.host?.lowercase() ?: return null)) return null
        val segments = pathSegments(uri)
        val (id, isShort) = when (segments.firstOrNull()?.lowercase()) {
            "watch" -> queryParams(uri)["v"] to false
            "shorts" -> segments.getOrNull(1) to true
            else -> return null
        }
        return id?.takeIf(videoIdPattern::matches)?.let { VideoRef(it, isShort) }
    }

    private fun videoIdOf(uri: URI): String? {
        val host = uri.host?.lowercase() ?: return null
        val segments = pathSegments(uri)
        val first = segments.firstOrNull()?.lowercase()
        val candidate = when {
            // Here the first segment is the case-sensitive ID itself.
            host == "youtu.be" || host == "www.youtu.be" -> segments.firstOrNull()

            !isYouTubeHost(host) -> null

            first == "watch" -> queryParams(uri)["v"]

            first in idPathPrefixes -> segments.getOrNull(1)

            // Old share links: /attribution_link?u=/watch%3Fv%3DID
            first == "attribution_link" -> queryParams(uri)["u"]?.let {
                toUri("https://www.youtube.com$it")
            }?.let(::videoIdOf)

            else -> null
        }
        return candidate?.takeIf(videoIdPattern::matches)
    }

    private fun pageUrlOf(uri: URI): String {
        val host = uri.host?.lowercase().orEmpty()
        if (!isYouTubeHost(host) || host.endsWith("youtube-nocookie.com")) return YouTubeUrls.HOME
        // Keep subdomains like music.youtube.com; send desktop links to the mobile site.
        val targetHost = if (host == "youtube.com" || host == "www.youtube.com") "m.youtube.com" else host
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "https://$targetHost${uri.rawPath.orEmpty().ifEmpty { "/" }}$query"
    }

    private fun isYouTubeHost(host: String): Boolean = host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" ||
        host.endsWith(".youtube-nocookie.com")

    private fun toUri(link: String): URI? {
        val withScheme = if (link.contains("://")) link else "https://$link"
        // Browsers tolerate characters like '|' that java.net.URI rejects; encode them instead of failing.
        val escaped = withScheme.map { char ->
            if (char in
                URI_ILLEGAL_CHARS
            ) {
                "%" + char.code.toString(16).uppercase().padStart(2, '0')
            } else {
                char.toString()
            }
        }.joinToString("")
        return try {
            URI(escaped).takeIf { it.scheme.equals("https", true) || it.scheme.equals("http", true) }
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun pathSegments(uri: URI): List<String> = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }

    private fun queryParams(uri: URI): Map<String, String> = uri.rawQuery.orEmpty()
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val key = pair.substringBefore('=')
            decode(key) to decode(pair.substringAfter('=', ""))
        }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }
}

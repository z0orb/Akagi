package io.github.z0orb.akagi.account

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Writes the browser's cookies as the Netscape `cookies.txt` that yt-dlp reads with `--cookies` (PLAN §7.15).
 * Input entries are Set-Cookie style ("name=value; domain=.youtube.com; path=/; expires=…; secure"), as
 * `CookieManagerCompat.getCookieInfo` returns them; a bare "name=value" is a cookie of [defaultDomain].
 */
object CookieExporter {
    const val HEADER = "# Netscape HTTP Cookie File"

    /** Used when a cookie has no usable expiry; yt-dlp only needs the file for one run. */
    private const val FALLBACK_LIFETIME_SECONDS = 24 * 60 * 60L

    fun toNetscape(cookies: List<String>, defaultDomain: String, nowSeconds: Long): String = buildString {
        appendLine(HEADER)
        cookies.mapNotNull { parse(it, defaultDomain, nowSeconds) }.forEach { appendLine(it.line()) }
    }

    /** Splits a plain `Cookie:` header ("a=1; b=2") into entries for [toNetscape]. */
    fun entriesOfHeader(header: String?): List<String> =
        header.orEmpty().split(';').map(String::trim).filter { it.contains('=') }

    private class Cookie(
        val domain: String,
        val path: String,
        val secure: Boolean,
        val expiresSeconds: Long,
        val name: String,
        val value: String,
    ) {
        // Python's cookie jar insists that the subdomain flag matches the leading dot.
        fun line(): String = listOf(
            domain,
            flag(domain.startsWith(".")),
            path,
            flag(secure),
            expiresSeconds.toString(),
            name,
            value,
        ).joinToString("\t")

        private fun flag(value: Boolean) = if (value) "TRUE" else "FALSE"
    }

    private fun parse(entry: String, defaultDomain: String, nowSeconds: Long): Cookie? {
        val parts = entry.split(';').map(String::trim)
        val name = parts.first().substringBefore('=', missingDelimiterValue = "").trim()
        val value = parts.first().substringAfter('=', missingDelimiterValue = "").trim()
        val attributes = parts.drop(1).associate { part ->
            part.substringBefore('=').trim().lowercase() to part.substringAfter('=', missingDelimiterValue = "").trim()
        }
        // A tab or line break would corrupt the tab-separated file.
        if (name.isEmpty() || (name + value).any { it == '\t' || it == '\n' || it == '\r' }) return null
        val secure = "secure" in attributes || name.startsWith("__Secure-") || name.startsWith("__Host-")
        return Cookie(
            domain = attributes["domain"]?.takeIf(String::isNotEmpty) ?: defaultDomain,
            path = attributes["path"]?.takeIf { it.startsWith("/") } ?: "/",
            secure = secure,
            expiresSeconds = expiryOf(attributes, nowSeconds),
            name = name,
            value = value,
        )
    }

    private fun expiryOf(attributes: Map<String, String>, nowSeconds: Long): Long {
        attributes["max-age"]?.toLongOrNull()?.let { return nowSeconds + it }
        val expires = attributes["expires"]?.let { text ->
            try {
                ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
            } catch (_: DateTimeParseException) {
                null
            }
        }
        return expires ?: (nowSeconds + FALLBACK_LIFETIME_SECONDS)
    }
}

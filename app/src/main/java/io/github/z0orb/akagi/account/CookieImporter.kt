package io.github.z0orb.akagi.account

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One imported cookie, ready for `CookieManager.setCookie(url, setCookie)`. */
data class ImportedCookie(val url: String, val name: String, val setCookie: String)

/**
 * Reads a Netscape `cookies.txt` exported from a desktop browser (PLAN §7.15 fallback). Only YouTube and
 * Google cookies are kept: the file may hold every site the user visits, and Akagi has no use for those.
 */
object CookieImporter {
    private const val HTTP_ONLY_PREFIX = "#HttpOnly_"
    private const val FIELDS = 7

    fun parse(text: String, nowSeconds: Long): List<ImportedCookie> = text.lineSequence()
        .map { it.trimEnd('\r', '\n') }
        .mapNotNull { parseLine(it, nowSeconds) }
        .toList()

    private fun parseLine(line: String, nowSeconds: Long): ImportedCookie? {
        val httpOnly = line.startsWith(HTTP_ONLY_PREFIX)
        val body = if (httpOnly) line.removePrefix(HTTP_ONLY_PREFIX) else line
        if (body.isBlank() || body.startsWith("#")) return null
        // Some exporters drop the empty value column, so six fields are accepted too.
        val fields = body.split('\t')
        if (fields.size !in FIELDS - 1..FIELDS) return null
        val domain = fields[0].trim().lowercase()
        val path = fields[2].ifBlank { "/" }
        val secure = fields[3].equals("TRUE", ignoreCase = true)
        val expires = fields[4].trim().toLongOrNull() ?: return null
        val name = fields[5].trim()
        val value = fields.getOrElse(6) { "" }
        val host = domain.removePrefix(".")
        if (name.isEmpty() || !isAccountHost(host) || (expires != 0L && expires <= nowSeconds)) return null
        val setCookie = buildString {
            append("$name=$value; Path=$path")
            // __Host- cookies must be host-only; Chromium rejects them with a Domain attribute.
            if (domain.startsWith(".") && !name.startsWith("__Host-")) append("; Domain=$domain")
            if (secure || name.startsWith("__Secure-") || name.startsWith("__Host-")) append("; Secure")
            if (httpOnly) append("; HttpOnly")
            if (expires != 0L) append("; Expires=${httpDate(expires)}")
        }
        return ImportedCookie(url = "https://$host$path", name = name, setCookie = setCookie)
    }

    private fun isAccountHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") || host == "google.com" || host.endsWith(".google.com")

    private fun httpDate(epochSeconds: Long): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC))
}

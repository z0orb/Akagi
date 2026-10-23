package io.github.z0orb.akagi.data.storage

/** `{Title} [{videoId}].{ext}` (PLAN §7.6, D-006): readable, unique and traceable back to the video. */
object FileNamer {
    private const val MAX_BYTES = 200
    private const val FALLBACK_TITLE = "Untitled"
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val whitespace = Regex("""\s+""")

    fun fileName(title: String, videoId: String, extension: String): String {
        val suffix = " [$videoId].$extension"
        val clean = title.replace(illegal, " ").replace(whitespace, " ").trim { it == ' ' || it == '.' }
        val fitted = truncateUtf8(clean, MAX_BYTES - suffix.utf8Size()).trimEnd { it == ' ' || it == '.' }
        return fitted.ifEmpty { FALLBACK_TITLE } + suffix
    }

    // Cuts on code-point boundaries so emoji and CJK are never split into broken bytes.
    private fun truncateUtf8(text: String, maxBytes: Int): String {
        var bytes = 0
        var end = 0
        while (end < text.length) {
            val next = text.offsetByCodePoints(end, 1)
            val size = text.substring(end, next).utf8Size()
            if (bytes + size > maxBytes) break
            bytes += size
            end = next
        }
        return text.substring(0, end)
    }

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size
}

package io.github.z0orb.akagi.data.library

/** A playlist name as typed, checked against the rules in PLAN P4.5: not blank, at most 60 characters. */
sealed interface PlaylistName {
    data class Valid(val value: String) : PlaylistName

    data object Blank : PlaylistName

    data object TooLong : PlaylistName

    companion object {
        const val MAX_LENGTH = 60

        fun of(input: String): PlaylistName {
            val trimmed = input.trim()
            return when {
                trimmed.isEmpty() -> Blank

                // Code points, so an emoji counts as one character, as the user sees it.
                trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH -> TooLong

                else -> Valid(trimmed)
            }
        }
    }
}

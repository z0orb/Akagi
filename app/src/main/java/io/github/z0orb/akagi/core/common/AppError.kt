package io.github.z0orb.akagi.core.common

/** Everything that can make a fetch or download fail, as the user sees it (PLAN §7.16). */
sealed interface AppError {
    data object AgeRestricted : AppError

    data object MembersOnly : AppError

    data object BotCheck : AppError

    /** YouTube changed something yt-dlp doesn't handle yet; an update usually fixes it. */
    data object ExtractorOutdated : AppError

    data object Private : AppError

    data object Unavailable : AppError

    data object GeoBlocked : AppError

    data object LiveOrUpcoming : AppError

    data object Network : AppError

    data object Storage : AppError

    /** The system stopped the download service (Android 15's dataSync limit) or the process died. */
    data object Interrupted : AppError

    data class Unknown(val detail: String) : AppError

    /** Stable key for persisting the type; spelled out because R8 renames classes in release builds. */
    val key: String get() = if (this is Unknown) UNKNOWN_KEY else keys.getValue(this)

    companion object {
        private const val UNKNOWN_KEY = "Unknown"

        private val byKey: Map<String, AppError> by lazy {
            mapOf(
                "AgeRestricted" to AgeRestricted,
                "MembersOnly" to MembersOnly,
                "BotCheck" to BotCheck,
                "ExtractorOutdated" to ExtractorOutdated,
                "Private" to Private,
                "Unavailable" to Unavailable,
                "GeoBlocked" to GeoBlocked,
                "LiveOrUpcoming" to LiveOrUpcoming,
                "Network" to Network,
                "Storage" to Storage,
                "Interrupted" to Interrupted,
            )
        }
        private val keys: Map<AppError, String> by lazy { byKey.entries.associate { (key, error) -> error to key } }

        fun fromKey(key: String, detail: String?): AppError = byKey[key] ?: Unknown(detail.orEmpty())
    }
}

/** Carries an [AppError] through code that fails by throwing (engines, storage). */
class AppException(val error: AppError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.toString(), cause)

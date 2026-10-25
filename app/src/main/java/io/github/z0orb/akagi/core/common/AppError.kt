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

    /** Streaming right now; it can be downloaded once it ends. */
    data object Live : AppError

    /** A stream or premiere that hasn't started; [reason] is YouTube's own text ("Premieres in 2 hours"), if any. */
    data class Upcoming(val reason: String) : AppError

    /** The stream just ended and YouTube is still processing it, so only part of it is available yet. */
    data object PostLive : AppError

    data object Network : AppError

    data object Storage : AppError

    /** The system stopped the download service (Android 15's dataSync limit) or the process died. */
    data object Interrupted : AppError

    data class Unknown(val detail: String) : AppError

    /** Stable key for persisting the type; spelled out because R8 renames classes in release builds. */
    val key: String
        get() = when (this) {
            is Unknown -> UNKNOWN_KEY
            is Upcoming -> UPCOMING_KEY
            else -> keys.getValue(this)
        }

    /** The text persisted next to [key], for the errors that carry one. */
    val persistedDetail: String?
        get() = when (this) {
            is Unknown -> detail
            is Upcoming -> reason
            else -> null
        }

    companion object {
        private const val UNKNOWN_KEY = "Unknown"
        private const val UPCOMING_KEY = "Upcoming"

        private val byKey: Map<String, AppError> by lazy {
            mapOf(
                "AgeRestricted" to AgeRestricted,
                "MembersOnly" to MembersOnly,
                "BotCheck" to BotCheck,
                "ExtractorOutdated" to ExtractorOutdated,
                "Private" to Private,
                "Unavailable" to Unavailable,
                "GeoBlocked" to GeoBlocked,
                "Live" to Live,
                "PostLive" to PostLive,
                "Network" to Network,
                "Storage" to Storage,
                "Interrupted" to Interrupted,
            )
        }
        private val keys: Map<AppError, String> by lazy { byKey.entries.associate { (key, error) -> error to key } }

        fun fromKey(key: String, detail: String?): AppError = when (key) {
            UPCOMING_KEY -> Upcoming(detail.orEmpty())

            // Phase 3–5 debug builds stored live and upcoming videos under one key.
            "LiveOrUpcoming" -> Live

            else -> byKey[key] ?: Unknown(detail.orEmpty())
        }
    }
}

/** Carries an [AppError] through code that fails by throwing (engines, storage). */
class AppException(val error: AppError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.toString(), cause)

/**
 * The error to show for a failure caught at the edge of an intent coroutine. Anything that isn't an
 * [AppException] is a bug or an I/O surprise; it must reach the UI as [AppError.Unknown], not crash the app.
 * Callers rethrow [kotlinx.coroutines.CancellationException] first.
 */
fun Exception.toAppError(): AppError = (this as? AppException)?.error ?: AppError.Unknown(message ?: javaClass.name)

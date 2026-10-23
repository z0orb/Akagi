package io.github.z0orb.akagi.core.designsystem

import android.content.res.Resources
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.SignInAdvice
import io.github.z0orb.akagi.core.common.SignInState
import io.github.z0orb.akagi.core.common.signInAdvice

/** The message for each error in the catalogue (PLAN §7.16). */
fun AppError.describe(resources: Resources): String = when (this) {
    AppError.AgeRestricted -> resources.getString(R.string.error_age_restricted)

    AppError.MembersOnly -> resources.getString(R.string.error_members_only)

    AppError.BotCheck -> resources.getString(R.string.error_bot_check)

    AppError.ExtractorOutdated -> resources.getString(R.string.error_extractor_outdated)

    AppError.Private -> resources.getString(R.string.error_private)

    AppError.Unavailable -> resources.getString(R.string.error_unavailable)

    AppError.GeoBlocked -> resources.getString(R.string.error_geo_blocked)

    AppError.Live -> resources.getString(R.string.error_live)

    is AppError.Upcoming -> reason.takeIf(String::isNotBlank)
        ?.let { resources.getString(R.string.error_upcoming_reason, sentence(it)) }
        ?: resources.getString(R.string.error_upcoming)

    AppError.PostLive -> resources.getString(R.string.error_post_live)

    AppError.Network -> resources.getString(R.string.error_network)

    AppError.Storage -> resources.getString(R.string.error_storage)

    AppError.Interrupted -> resources.getString(R.string.error_interrupted)

    is AppError.Unknown -> detail.ifBlank { resources.getString(R.string.error_unknown) }
}

/**
 * The message for [this] given what an account can still do about it (PLAN §7.15). Errors an account can't fix,
 * and [SignInAdvice.NONE], read as in the plain catalogue.
 */
fun AppError.describe(resources: Resources, advice: SignInAdvice): String {
    val message = when (advice) {
        SignInAdvice.NONE -> null
        SignInAdvice.SIGN_IN -> signInMessages[this]
        SignInAdvice.ALLOW_SIGN_IN -> allowSignInMessages[this]
        SignInAdvice.ACCOUNT_REFUSED -> refusedMessages[this]
    }
    return message?.let(resources::getString) ?: describe(resources)
}

fun AppError.describe(resources: Resources, signIn: SignInState): String = describe(resources, signInAdvice(signIn))

private val signInMessages = mapOf(
    AppError.AgeRestricted to R.string.error_age_restricted,
    AppError.MembersOnly to R.string.error_members_only_sign_in,
    AppError.BotCheck to R.string.error_bot_check,
    AppError.Private to R.string.error_private_sign_in,
)

private val allowSignInMessages = mapOf(
    AppError.AgeRestricted to R.string.error_age_restricted_allow,
    AppError.MembersOnly to R.string.error_members_only_allow,
    AppError.BotCheck to R.string.error_bot_check_allow,
    AppError.Private to R.string.error_private_allow,
)

private val refusedMessages = mapOf(
    AppError.AgeRestricted to R.string.error_age_restricted_refused,
    AppError.MembersOnly to R.string.error_members_only_refused,
    AppError.BotCheck to R.string.error_bot_check_refused,
    AppError.Private to R.string.error_private_refused,
)

// YouTube writes "Premieres in 2 hours" without a full stop; the message continues after it.
private fun sentence(text: String): String = text.trim().let { if (it.last() in ".!?") it else "$it." }

/** Errors that a newer yt-dlp usually fixes, so the UI offers "Update yt-dlp". */
val AppError.suggestsUpdate: Boolean get() = this == AppError.ExtractorOutdated || this == AppError.BotCheck

/** Errors worth simply trying again. */
val AppError.isRetryable: Boolean
    get() = this == AppError.Network || this == AppError.Storage || this == AppError.Interrupted ||
        this == AppError.PostLive || this is AppError.Unknown

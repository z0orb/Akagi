package io.github.z0orb.akagi.core.designsystem

import android.content.res.Resources
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.AppError

/** The message for each error in the catalogue (PLAN §7.16). */
fun AppError.describe(resources: Resources): String = when (this) {
    AppError.AgeRestricted -> resources.getString(R.string.error_age_restricted)
    AppError.MembersOnly -> resources.getString(R.string.error_members_only)
    AppError.BotCheck -> resources.getString(R.string.error_bot_check)
    AppError.ExtractorOutdated -> resources.getString(R.string.error_extractor_outdated)
    AppError.Private -> resources.getString(R.string.error_private)
    AppError.Unavailable -> resources.getString(R.string.error_unavailable)
    AppError.GeoBlocked -> resources.getString(R.string.error_geo_blocked)
    AppError.LiveOrUpcoming -> resources.getString(R.string.error_live)
    AppError.Network -> resources.getString(R.string.error_network)
    AppError.Storage -> resources.getString(R.string.error_storage)
    AppError.Interrupted -> resources.getString(R.string.error_interrupted)
    is AppError.Unknown -> detail.ifBlank { resources.getString(R.string.error_unknown) }
}

/** Errors that a newer yt-dlp usually fixes, so the UI offers "Update yt-dlp". */
val AppError.suggestsUpdate: Boolean get() = this == AppError.ExtractorOutdated || this == AppError.BotCheck

/** Errors worth simply trying again. */
val AppError.isRetryable: Boolean
    get() = this == AppError.Network || this == AppError.Storage || this == AppError.Interrupted ||
        this is AppError.Unknown

package io.github.z0orb.akagi.core.common

/** The account side of an error message (PLAN §7.15): is the browser signed in, and may yt-dlp use it? */
data class SignInState(val signedIn: Boolean = false, val cookiesAllowed: Boolean = true)

/** What the user can still do about an error with their YouTube account. */
enum class SignInAdvice {
    /** Not an account problem, or nothing more an account can do. */
    NONE,

    /** Signing in would let yt-dlp retry with the account. */
    SIGN_IN,

    /** Signed in, but the cookie policy is "Never". */
    ALLOW_SIGN_IN,

    /** yt-dlp already tried with the account and YouTube still refused. */
    ACCOUNT_REFUSED,
}

/** Errors a YouTube account can fix; the only ones "Only when needed" retries with cookies (D-020). */
val AppError.accountCanHelp: Boolean
    get() = this == AppError.AgeRestricted || this == AppError.MembersOnly || this == AppError.BotCheck ||
        this == AppError.Private

fun AppError.signInAdvice(state: SignInState): SignInAdvice = when {
    !accountCanHelp -> SignInAdvice.NONE
    !state.signedIn -> SignInAdvice.SIGN_IN
    !state.cookiesAllowed -> SignInAdvice.ALLOW_SIGN_IN
    else -> SignInAdvice.ACCOUNT_REFUSED
}

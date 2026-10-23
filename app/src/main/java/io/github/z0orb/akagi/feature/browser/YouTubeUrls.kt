package io.github.z0orb.akagi.feature.browser

import java.net.URLEncoder

/** Canonical mobile-site URLs the browser opens. */
object YouTubeUrls {
    const val HOME = "https://m.youtube.com/"

    /** Google's sign-in for YouTube, returning to the mobile site the way YouTube's own "Sign in" does. */
    const val SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm" +
        "%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

    fun watch(videoId: String): String = "https://m.youtube.com/watch?v=$videoId"

    // The String charset overload: the Charset one needs API 33.
    fun search(query: String): String =
        "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
}

package io.github.z0orb.akagi.feature.browser

import java.net.URLEncoder

/** Canonical mobile-site URLs the browser opens. */
object YouTubeUrls {
    const val HOME = "https://m.youtube.com/"
    const val MUSIC_HOME = "https://music.youtube.com/"

    /** Google's sign-in for YouTube, returning to the mobile site the way YouTube's own "Sign in" does. */
    const val SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm" +
        "%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

    fun home(site: Site): String = when (site) {
        Site.YOUTUBE -> HOME
        Site.YOUTUBE_MUSIC -> MUSIC_HOME
    }

    /** [listId] keeps the playlist the link came from, so the whole list can be downloaded from the watch page. */
    fun watch(videoId: String, site: Site = Site.YOUTUBE, listId: String? = null): String =
        home(site) + "watch?v=$videoId" + listId?.let { "&list=$it" }.orEmpty()

    // The String charset overload: the Charset one needs API 33.
    fun search(query: String, site: Site = Site.YOUTUBE): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (site) {
            Site.YOUTUBE -> "${HOME}results?search_query=$encoded"
            Site.YOUTUBE_MUSIC -> "${MUSIC_HOME}search?q=$encoded"
        }
    }
}

package io.github.z0orb.akagi.feature.browser

import java.net.URLEncoder

/** Canonical mobile-site URLs the browser opens. */
object YouTubeUrls {
    const val HOME = "https://m.youtube.com/"

    fun watch(videoId: String): String = "https://m.youtube.com/watch?v=$videoId"

    // The String charset overload: the Charset one needs API 33.
    fun search(query: String): String =
        "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
}

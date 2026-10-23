package io.github.z0orb.akagi.feature.browser

/** What the link/search field opens. */
sealed interface LinkTarget {
    val url: String

    data class Video(val videoId: String) : LinkTarget {
        override val url: String get() = YouTubeUrls.watch(videoId)
    }

    /** A YouTube link that isn't a video (channel, playlist, home…). */
    data class Page(override val url: String) : LinkTarget

    data class Search(val query: String) : LinkTarget {
        override val url: String get() = YouTubeUrls.search(query)
    }
}

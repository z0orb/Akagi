package io.github.z0orb.akagi.feature.browser

/** What the link/search field opens. */
sealed interface LinkTarget {
    val url: String

    /** A pasted video link opens on the site it came from, inside its playlist when it had one (D-073). */
    data class Video(val videoId: String, val site: Site = Site.YOUTUBE, val listId: String? = null) : LinkTarget {
        override val url: String get() = YouTubeUrls.watch(videoId, site, listId)
    }

    /** A YouTube link that isn't a video (channel, playlist, home…). */
    data class Page(override val url: String) : LinkTarget

    data class Search(val query: String, val site: Site = Site.YOUTUBE) : LinkTarget {
        override val url: String get() = YouTubeUrls.search(query, site)
    }
}

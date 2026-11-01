package io.github.z0orb.akagi.feature.browser

import androidx.compose.runtime.Immutable

@Immutable
data class BrowserUiState(
    /** Where the WebView starts: the last page after process death, otherwise YouTube home. */
    val startUrl: String,
    /** The video on screen; the download FAB shows only when this is set. */
    val currentVideo: VideoRef? = null,
    val canGoBack: Boolean = false,
    /** The site of the page on screen; pages outside YouTube (sign-in) keep the previous one. */
    val site: Site = Site.YOUTUBE,
    /** A navigation the UI should perform once, then acknowledge with [BrowserViewModel.onLoadHandled]. */
    val pendingLoad: PendingLoad? = null,
)

@Immutable
data class PendingLoad(val url: String, val id: Long)

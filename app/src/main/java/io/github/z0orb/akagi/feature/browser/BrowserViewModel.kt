package io.github.z0orb.akagi.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class BrowserViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState(startUrl = savedState[KEY_LAST_URL] ?: YouTubeUrls.HOME))
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private var nextLoadId = 0L

    fun onPageChange(url: String, canGoBack: Boolean) {
        // Only restore real YouTube pages after process death, never a sign-in step or about:blank.
        if (url.startsWith("https://") && NavigationPolicy.decide(url) == NavigationDecision.LOAD) {
            savedState[KEY_LAST_URL] = url
        }
        _uiState.update { it.copy(currentVideo = YouTubeLinkParser.videoPageOf(url), canGoBack = canGoBack) }
    }

    fun onQuerySubmit(text: String) {
        YouTubeLinkParser.parse(text)?.let { load(it.url) }
    }

    fun onHomeClick() = load(YouTubeUrls.HOME)

    fun onLoadHandled(id: Long) {
        _uiState.update { state -> if (state.pendingLoad?.id == id) state.copy(pendingLoad = null) else state }
    }

    private fun load(url: String) {
        _uiState.update { it.copy(pendingLoad = PendingLoad(url, ++nextLoadId)) }
    }

    private companion object {
        const val KEY_LAST_URL = "last_url"
    }
}

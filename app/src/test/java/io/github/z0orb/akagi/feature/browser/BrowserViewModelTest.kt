package io.github.z0orb.akagi.feature.browser

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewModelTest {

    private val watchUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&pp=abc"

    @Test
    fun `starts at YouTube home`() {
        assertEquals(YouTubeUrls.HOME, BrowserViewModel(SavedStateHandle()).uiState.value.startUrl)
    }

    @Test
    fun `restores the last YouTube page after process death`() {
        val saved = SavedStateHandle()
        BrowserViewModel(saved).onPageChange(watchUrl, canGoBack = true)
        assertEquals(watchUrl, BrowserViewModel(saved).uiState.value.startUrl)
    }

    @Test
    fun `sign-in pages are not restored`() {
        val saved = SavedStateHandle()
        val viewModel = BrowserViewModel(saved)
        viewModel.onPageChange(watchUrl, canGoBack = false)
        viewModel.onPageChange("https://example.com/", canGoBack = true)
        viewModel.onPageChange("about:blank", canGoBack = true)
        assertEquals(watchUrl, BrowserViewModel(saved).uiState.value.startUrl)
    }

    @Test
    fun `current video follows navigation`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        viewModel.onPageChange(watchUrl, canGoBack = true)
        assertEquals(VideoRef("dQw4w9WgXcQ", isShort = false), viewModel.uiState.value.currentVideo)
        assertTrue(viewModel.uiState.value.canGoBack)

        viewModel.onPageChange(YouTubeUrls.HOME, canGoBack = false)
        assertNull(viewModel.uiState.value.currentVideo)
        assertFalse(viewModel.uiState.value.canGoBack)
    }

    @Test
    fun `submitted link becomes a one-shot clean load`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        viewModel.onQuerySubmit("https://youtu.be/dQw4w9WgXcQ?si=xyz&t=3")
        val load = viewModel.uiState.value.pendingLoad
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", load?.url)

        viewModel.onLoadHandled(requireNotNull(load).id)
        assertNull(viewModel.uiState.value.pendingLoad)
    }

    @Test
    fun `acknowledging a stale load keeps the newer one`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        viewModel.onQuerySubmit("first")
        val stale = viewModel.uiState.value.pendingLoad
        viewModel.onHomeClick()
        viewModel.onLoadHandled(requireNotNull(stale).id)
        assertEquals(YouTubeUrls.HOME, viewModel.uiState.value.pendingLoad?.url)
    }

    @Test
    fun `the site follows the page on screen`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        assertEquals(Site.YOUTUBE, viewModel.uiState.value.site)

        viewModel.onPageChange("https://music.youtube.com/", canGoBack = false)
        assertEquals(Site.YOUTUBE_MUSIC, viewModel.uiState.value.site)

        // A sign-in page keeps the previous site.
        viewModel.onPageChange("https://accounts.google.com/ServiceLogin", canGoBack = true)
        assertEquals(Site.YOUTUBE_MUSIC, viewModel.uiState.value.site)

        viewModel.onPageChange(watchUrl, canGoBack = true)
        assertEquals(Site.YOUTUBE, viewModel.uiState.value.site)
    }

    @Test
    fun `starts on the site of the restored page`() {
        val saved = SavedStateHandle()
        BrowserViewModel(saved).onPageChange("https://music.youtube.com/watch?v=5F28ye50-Kc", canGoBack = true)
        assertEquals(Site.YOUTUBE_MUSIC, BrowserViewModel(saved).uiState.value.site)
    }

    @Test
    fun `search goes to the site on screen`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        viewModel.onPageChange("https://music.youtube.com/", canGoBack = false)
        viewModel.onQuerySubmit("teh hijau")
        assertEquals("https://music.youtube.com/search?q=teh+hijau", viewModel.uiState.value.pendingLoad?.url)
    }

    @Test
    fun `choosing a site opens its home`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        viewModel.onSiteClick(Site.YOUTUBE_MUSIC)
        assertEquals(YouTubeUrls.MUSIC_HOME, viewModel.uiState.value.pendingLoad?.url)
        viewModel.onSiteClick(Site.YOUTUBE)
        assertEquals(YouTubeUrls.HOME, viewModel.uiState.value.pendingLoad?.url)
    }

    @Test
    fun `blank submit does nothing`() {
        val viewModel = BrowserViewModel(SavedStateHandle())
        viewModel.onQuerySubmit("   ")
        assertNull(viewModel.uiState.value.pendingLoad)
    }
}

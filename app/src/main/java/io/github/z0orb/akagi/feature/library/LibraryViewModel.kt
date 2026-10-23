package io.github.z0orb.akagi.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.data.library.LibraryRepository
import io.github.z0orb.akagi.download.DownloadQueue
import io.github.z0orb.akagi.download.DownloadTask
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LibraryUiState(
    val tasks: List<DownloadTask> = emptyList(),
    val progress: Map<Long, DownloadProgress> = emptyMap(),
    val tracks: List<TrackEntity> = emptyList(),
)

/** State of the library side panel (PLAN §7.12). */
@HiltViewModel
class LibraryViewModel @Inject constructor(private val queue: DownloadQueue, library: LibraryRepository) :
    ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        combine(queue.tasks, queue.progress, library.observeTracks(), ::LibraryUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onCancelTask(taskId: Long) {
        viewModelScope.launch { queue.cancel(taskId) }
    }

    fun onRetryTask(taskId: Long) {
        viewModelScope.launch { queue.retry(taskId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

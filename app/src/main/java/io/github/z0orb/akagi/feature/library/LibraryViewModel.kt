package io.github.z0orb.akagi.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.data.library.LibraryRepository
import io.github.z0orb.akagi.data.library.PlaylistName
import io.github.z0orb.akagi.data.library.PlaylistRepository
import io.github.z0orb.akagi.download.DownloadQueue
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the library side panel (PLAN §7.12). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val queue: DownloadQueue,
    library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val dialog = MutableStateFlow<LibraryDialog?>(null)

    private val openPlaylist = savedState.getStateFlow<Long?>(OPEN_PLAYLIST_KEY, null).flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(playlists.observe(id), playlists.observeTracks(id)) { playlist, tracks ->
                playlist?.let { PlaylistDetail(it.id, it.name, tracks) }
            }
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        combine(queue.tasks, queue.progress, library.observeTracks(), ::Triple),
        playlists.observeSummaries(),
        openPlaylist,
        // Saved here rather than in the panel, whose content leaves composition while it is closed.
        savedState.getStateFlow(TAB_KEY, LibraryTab.DOWNLOADS),
        dialog,
    ) { (tasks, progress, tracks), summaries, detail, tab, dialog ->
        LibraryUiState(tasks, progress, tracks, summaries, detail, tab, dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onSelectTab(tab: LibraryTab) {
        savedState[TAB_KEY] = tab
    }

    fun onCancelTask(taskId: Long) {
        viewModelScope.launch { queue.cancel(taskId) }
    }

    fun onRetryTask(taskId: Long) {
        viewModelScope.launch { queue.retry(taskId) }
    }

    fun onOpenPlaylist(id: Long) {
        savedState[OPEN_PLAYLIST_KEY] = id
    }

    fun onClosePlaylist() {
        savedState[OPEN_PLAYLIST_KEY] = null
    }

    fun onDismissDialog() {
        dialog.value = null
    }

    fun onAddToPlaylistClick(track: TrackEntity) {
        viewModelScope.launch { dialog.value = LibraryDialog.AddToPlaylist(track, playlists.playlistIdsOf(track.id)) }
    }

    fun onToggleMembership(playlistId: Long) = dialog.update { current ->
        if (current !is LibraryDialog.AddToPlaylist) return@update current
        val selected = current.selected
        current.copy(selected = if (playlistId in selected) selected - playlistId else selected + playlistId)
    }

    /** "New playlist" inside the checklist: creates it and ticks it, so Done adds the track there too. */
    fun onCreatePlaylistInline(input: String) {
        val current = dialog.value as? LibraryDialog.AddToPlaylist ?: return
        when (val name = PlaylistName.of(input)) {
            is PlaylistName.Valid -> viewModelScope.launch {
                val id = playlists.create(name)
                dialog.update {
                    (it as? LibraryDialog.AddToPlaylist)?.copy(
                        selected = it.selected + id,
                        nameError = null,
                        draftGeneration = it.draftGeneration + 1,
                    ) ?: it
                }
            }

            else -> dialog.value = current.copy(nameError = name.toError())
        }
    }

    fun onConfirmMemberships() {
        val current = dialog.value as? LibraryDialog.AddToPlaylist ?: return
        dialog.value = null
        viewModelScope.launch { playlists.setMemberships(current.track.id, current.selected) }
    }

    fun onNewPlaylistClick() {
        dialog.value = LibraryDialog.NamePlaylist(playlistId = null, initialName = "")
    }

    fun onRenamePlaylistClick(playlistId: Long, currentName: String) {
        dialog.value = LibraryDialog.NamePlaylist(playlistId, currentName)
    }

    fun onSubmitPlaylistName(input: String) {
        val current = dialog.value as? LibraryDialog.NamePlaylist ?: return
        when (val name = PlaylistName.of(input)) {
            is PlaylistName.Valid -> {
                dialog.value = null
                viewModelScope.launch {
                    current.playlistId?.let { playlists.rename(it, name) } ?: playlists.create(name)
                }
            }

            else -> dialog.value = current.copy(error = name.toError())
        }
    }

    fun onDeletePlaylistClick(playlistId: Long, name: String) {
        dialog.value = LibraryDialog.DeletePlaylist(playlistId, name)
    }

    fun onConfirmDeletePlaylist() {
        val current = dialog.value as? LibraryDialog.DeletePlaylist ?: return
        dialog.value = null
        if (savedState.get<Long>(OPEN_PLAYLIST_KEY) == current.playlistId) onClosePlaylist()
        viewModelScope.launch { playlists.delete(current.playlistId) }
    }

    fun onRemoveFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { playlists.remove(playlistId, trackId) }
    }

    fun onReorder(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch { playlists.reorder(playlistId, trackIds) }
    }

    private fun PlaylistName.toError(): NameError? = when (this) {
        PlaylistName.Blank -> NameError.BLANK
        PlaylistName.TooLong -> NameError.TOO_LONG
        is PlaylistName.Valid -> null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val OPEN_PLAYLIST_KEY = "openPlaylist"
        const val TAB_KEY = "tab"
    }
}

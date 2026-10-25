package io.github.z0orb.akagi.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.account.AccountRepository
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.data.library.LibraryRepository
import io.github.z0orb.akagi.data.library.PlaylistName
import io.github.z0orb.akagi.data.library.PlaylistRepository
import io.github.z0orb.akagi.data.storage.AudioDeletion
import io.github.z0orb.akagi.download.DownloadQueue
import io.github.z0orb.akagi.playback.PlayQueue
import io.github.z0orb.akagi.playback.PlayerConnection
import io.github.z0orb.akagi.playback.QueueFactory
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val player: PlayerConnection,
    private val savedState: SavedStateHandle,
    private val account: AccountRepository,
) : ViewModel() {

    private val dialog = MutableStateFlow<LibraryDialog?>(null)
    private val event = MutableStateFlow<LibraryEvent?>(null)
    private var nextEventId = 0L

    // The track whose file deletion waits for the system dialog; the row goes once the user agrees.
    private var awaitingConsent: TrackEntity? = null

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
        combine(dialog, event, account.state, ::Triple),
    ) { (tasks, progress, tracks), summaries, detail, tab, (dialog, event, signIn) ->
        LibraryUiState(tasks, progress, tracks, summaries, detail, tab, dialog, event, signIn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onSelectTab(tab: LibraryTab) {
        savedState[TAB_KEY] = tab
    }

    /** A Downloads row: the whole list becomes the queue (R15). */
    fun onPlayTrack(track: TrackEntity) {
        if (track.missing) {
            emit { LibraryEvent.Toast(it, R.string.track_missing_toast) }
            return
        }
        play(QueueFactory.fromDownloads(uiState.value.tracks, track.id))
    }

    /** A row in the open playlist: only that playlist is queued. */
    fun onPlayPlaylistTrack(track: TrackEntity) {
        val detail = uiState.value.openPlaylist ?: return
        if (track.missing) {
            emit { LibraryEvent.Toast(it, R.string.track_missing_toast) }
            return
        }
        play(QueueFactory.fromPlaylist(detail.id, detail.tracks, track.id))
    }

    /** The open playlist's Play or Shuffle button. */
    fun onPlayPlaylist(shuffle: Boolean) {
        val detail = uiState.value.openPlaylist ?: return
        val queue = QueueFactory.playAll(detail.id, detail.tracks, shuffle)
        if (queue == null) emit { LibraryEvent.Toast(it, R.string.playlist_nothing_playable) } else play(queue)
    }

    private fun play(queue: PlayQueue?) {
        queue ?: return
        viewModelScope.launch {
            if (!player.play(queue)) emit { LibraryEvent.Toast(it, R.string.player_unavailable) }
        }
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

    fun onDeleteTrackClick(track: TrackEntity) {
        viewModelScope.launch { dialog.value = LibraryDialog.DeleteTrack(track, library.playlistCount(track.id)) }
    }

    fun onConfirmDeleteTrack() {
        val track = (dialog.value as? LibraryDialog.DeleteTrack)?.track ?: return
        dialog.value = null
        viewModelScope.launch {
            try {
                when (val result = library.delete(track)) {
                    AudioDeletion.Deleted -> player.remove(track.id)

                    is AudioDeletion.NeedsConsent -> {
                        awaitingConsent = track
                        emit { LibraryEvent.ConfirmFileDeletion(it, result.request) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // MediaStore can also throw SecurityException or IllegalStateException; none of them may crash.
                emit { LibraryEvent.Toast(it, R.string.error_storage) }
            }
        }
    }

    fun onDeleteConsentResult(granted: Boolean) {
        val track = awaitingConsent ?: return
        awaitingConsent = null
        if (granted) {
            player.remove(track.id)
            viewModelScope.launch { library.forget(track) }
        }
    }

    /** Files can vanish while Akagi is in the background (another app, a file manager), and the sign-in can change. */
    fun onResume() {
        account.refresh()
        viewModelScope.launch { library.reconcileMissingFiles() }
    }

    fun onEventHandled(id: Long) = event.update { if (it?.id == id) null else it }

    private fun emit(create: (Long) -> LibraryEvent) {
        event.value = create(++nextEventId)
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

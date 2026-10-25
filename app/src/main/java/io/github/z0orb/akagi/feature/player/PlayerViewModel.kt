package io.github.z0orb.akagi.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.data.library.LibraryRepository
import io.github.z0orb.akagi.data.library.PlaylistRepository
import io.github.z0orb.akagi.playback.AudioRouteMonitor
import io.github.z0orb.akagi.playback.HeadphoneDisconnectHandler
import io.github.z0orb.akagi.playback.OutputKind
import io.github.z0orb.akagi.playback.PlayerConnection
import io.github.z0orb.akagi.playback.QueueSource
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** The mini and expanded player (PLAN §7.13). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerConnection,
    private val headphones: HeadphoneDisconnectHandler,
    library: LibraryRepository,
    playlists: PlaylistRepository,
    routes: AudioRouteMonitor,
) : ViewModel() {

    // Looked up once per track, not on every play/pause.
    private val waveform: Flow<Waveform?> = player.state.map { it.current?.trackId }.distinctUntilChanged()
        .mapLatest { id -> id?.let { library.waveform(it) }?.let(::Waveform) }

    // Follows renames of the playlist being played.
    private val playingFrom: Flow<PlayingFrom?> = player.state.map { it.source }.distinctUntilChanged()
        .flatMapLatest { source ->
            when (source) {
                null -> flowOf(null)

                QueueSource.Downloads -> flowOf(PlayingFrom.Downloads)

                is QueueSource.Playlist -> playlists.observe(source.id).map {
                    it?.let { p -> PlayingFrom.Playlist(p.name) }
                }
            }
        }

    private val usbDac: Flow<UsbDacOutput?> = routes.route
        .map { route -> route.takeIf { it.kind == OutputKind.USB_DAC }?.let { UsbDacOutput(it.dacFormat) } }
        .distinctUntilChanged()

    val uiState: StateFlow<PlayerUiState> = combine(player.state, waveform, playingFrom, usbDac) {
            state,
            waveform,
            from,
            dac,
        ->
        PlayerUiState(state.current, state.isPlaying, state.shuffle, state.repeat, from, waveform, dac)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState())

    fun onPlayPause() = player.togglePlayPause()

    fun onNext() = player.next()

    fun onPrevious() = player.previous()

    fun onSeek(positionMs: Long) = player.seekTo(positionMs)

    fun onToggleShuffle() = player.toggleShuffle()

    fun onCycleRepeat() = player.cycleRepeat()

    /** YouTube started audible playback: it takes over, as another app would (R11). */
    fun onWebMediaStarted() = player.pause()

    /** Headphones dropped while YouTube was playing; the page is already paused. */
    fun onWebAudioUnplugged() = headphones.muteSpeakerOnceRouteSettles()

    /** Polled by the seekbar's ticker while it is visible and playing. */
    fun positionMs(): Long = player.positionMs()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

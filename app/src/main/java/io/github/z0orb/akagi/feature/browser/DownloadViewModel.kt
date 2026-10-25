package io.github.z0orb.akagi.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.account.AccountRepository
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.common.toAppError
import io.github.z0orb.akagi.data.library.LibraryRepository
import io.github.z0orb.akagi.data.prefs.SettingsRepository
import io.github.z0orb.akagi.download.DownloadQueue
import io.github.z0orb.akagi.download.DownloadRequest
import io.github.z0orb.akagi.playback.PlayerConnection
import io.github.z0orb.akagi.playback.QueueFactory
import io.github.z0orb.akagi.ytdlp.FormatChoices
import io.github.z0orb.akagi.ytdlp.FormatOption
import io.github.z0orb.akagi.ytdlp.FormatRecommender
import io.github.z0orb.akagi.ytdlp.FormatTier
import io.github.z0orb.akagi.ytdlp.RememberedChoice
import io.github.z0orb.akagi.ytdlp.VideoInfo
import io.github.z0orb.akagi.ytdlp.VideoInfoCache
import io.github.z0orb.akagi.ytdlp.YtDlpUpdater
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The download FAB and format sheet for the video on screen (PLAN §7.3–7.5). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val infoCache: VideoInfoCache,
    private val queue: DownloadQueue,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val updater: YtDlpUpdater,
    private val player: PlayerConnection,
    private val account: AccountRepository,
) : ViewModel() {

    private val currentVideo = MutableStateFlow<String?>(null)
    private val sheet = MutableStateFlow<SheetModel?>(null)

    // Videos whose quick download (long-press) is still fetching formats.
    private val quickFetching = MutableStateFlow<Set<String>>(emptySet())
    private val event = MutableStateFlow<DownloadEvent?>(null)
    private var nextEventId = 0L
    private var prefetchJob: Job? = null
    private var loadJob: Job? = null

    val uiState: StateFlow<DownloadUiState> = combine(
        currentVideo.flatMapLatest(::statusOf),
        sheet.flatMapLatest { model -> statusOf(model?.videoId).map { model to it } },
        combine(currentVideo, quickFetching) { videoId, fetching -> videoId in fetching },
        event,
        account.state,
    ) { fabStatus, (model, sheetStatus), fetching, event, signIn ->
        DownloadUiState(
            fab = fabStateOf(fabStatus, fetching),
            sheet = model?.let {
                SheetUiState(it.title ?: (it.load as? Load.Ready)?.info?.title, sheetContentOf(it, sheetStatus, signIn))
            },
            event = event,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadUiState())

    /** Called on every page change; prefetches formats once the user has stayed on a video for a moment. */
    fun onVideoChanged(videoId: String?) {
        if (videoId == currentVideo.value) return
        currentVideo.value = videoId
        prefetchJob?.cancel()
        if (videoId == null) return
        prefetchJob = viewModelScope.launch {
            if (!settings.current().prefetchFormats) return@launch
            delay(PREFETCH_DWELL_MS)
            runCatching { infoCache.get(videoId) }
        }
    }

    fun onFabClick() {
        val videoId = currentVideo.value ?: return
        account.refresh()
        sheet.value = SheetModel(videoId)
        load(videoId)
    }

    /** Long-press: download right away with the remembered choice, or open the sheet if there's a decision to make. */
    fun onFabLongClick() {
        val videoId = currentVideo.value ?: return
        account.refresh()
        if (uiState.value.fab != FabState.Idle) return onFabClick()
        viewModelScope.launch {
            quickFetching.update { it + videoId }
            try {
                val info = infoCache.get(videoId)
                info.liveError?.let { throw AppException(it) }
                val choices = FormatRecommender.choicesFor(info)
                val option = FormatRecommender.initialSelection(choices, remembered())
                if (option == null) {
                    // Nothing to download: the sheet says why, instead of the spinner just stopping (B-13).
                    sheet.value = SheetModel(videoId)
                    load(videoId)
                    return@launch
                }
                enqueue(info, option, replaceTrackId = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sheet.value = SheetModel(videoId, load = Load.Failed(e.toAppError()))
            } finally {
                quickFetching.update { it - videoId }
            }
        }
    }

    fun onDismissSheet() {
        loadJob?.cancel()
        sheet.value = null
    }

    fun onCodecSelected(codec: AudioCodec) = updateChoice { choices ->
        choices.recommendedFor(codec) ?: choices.options[codec]?.firstOrNull()
    }

    fun onOptionSelected(option: FormatOption) = updateChoice { option }

    fun onDownloadClick() {
        val model = sheet.value ?: return
        val ready = model.load as? Load.Ready ?: return
        val option = model.selected ?: return
        // Closed first, so a second tap can't reach a sheet whose download is already being queued (B-04).
        sheet.value = null
        viewModelScope.launch {
            val existing = library.observeTrack(model.videoId).first()
            enqueue(ready.info, option, replaceTrackId = existing?.id)
            settings.rememberChoice(option.codec, option.tier?.name)
        }
    }

    fun onDownloadAgainClick() {
        sheet.update { it?.copy(choosing = true) }
    }

    fun onPlayClick() {
        val content = uiState.value.sheet?.content as? SheetContent.InLibrary ?: return
        sheet.value = null
        // Same as tapping the track in Downloads: the whole list is queued.
        viewModelScope.launch {
            val queue = QueueFactory.fromDownloads(library.observeTracks().first(), content.trackId)
            when {
                queue == null -> emit { DownloadEvent.Toast(it, R.string.track_missing_toast) }
                !player.play(queue) -> emit { DownloadEvent.Toast(it, R.string.player_unavailable) }
            }
        }
    }

    fun onCancelTask(taskId: Long) {
        viewModelScope.launch { queue.cancel(taskId) }
    }

    fun onRetryTask(taskId: Long) {
        viewModelScope.launch { queue.retry(taskId) }
    }

    fun onRetryFetch() {
        sheet.value?.let { load(it.videoId) }
    }

    fun onUpdateYtDlp() {
        val model = sheet.value ?: return
        loadJob?.cancel()
        sheet.value = model.copy(load = Load.UpdatingYtDlp)
        loadJob = viewModelScope.launch {
            try {
                updater.updateNow()
                load(model.videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoad(model.videoId, Load.Failed(e.toAppError()))
            }
        }
    }

    fun onEventHandled(id: Long) {
        event.update { if (it?.id == id) null else it }
    }

    private fun load(videoId: String) {
        loadJob?.cancel()
        setLoad(videoId, Load.Loading)
        loadJob = viewModelScope.launch {
            val load = try {
                val info = infoCache.get(videoId)
                val liveError = info.liveError
                if (liveError != null) {
                    Load.Failed(liveError)
                } else {
                    val choices = FormatRecommender.choicesFor(info)
                    val selected = FormatRecommender.initialSelection(choices, remembered())
                    // Another video's sheet is left as it is (C-01).
                    sheet.update { model -> if (model?.videoId == videoId) model.copy(selected = selected) else model }
                    Load.Ready(info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.toAppError())
            }
            setLoad(videoId, load)
        }
    }

    private fun setLoad(videoId: String, load: Load) {
        sheet.update { model -> if (model?.videoId == videoId) model.copy(load = load) else model }
    }

    private fun updateChoice(pick: (FormatChoices) -> FormatOption?) {
        sheet.update { model ->
            val ready = model?.load as? Load.Ready ?: return@update model
            model.copy(selected = pick(FormatRecommender.choicesFor(ready.info)) ?: model.selected)
        }
    }

    private suspend fun enqueue(info: VideoInfo, option: FormatOption, replaceTrackId: Long?) {
        queue.enqueue(
            DownloadRequest(
                info.videoId,
                info.title,
                option.formatId,
                option.codec,
                option.bitrateKbps,
                replaceTrackId,
            ),
        )
        emit { DownloadEvent.Toast(it, R.string.download_queued_toast) }
    }

    private suspend fun remembered(): RememberedChoice? {
        val current = settings.current()
        val codec = current.lastCodec ?: return null
        return RememberedChoice(codec, FormatTier.entries.firstOrNull { it.name == current.lastTier })
    }

    private fun statusOf(videoId: String?): Flow<VideoStatus?> {
        if (videoId == null) return flowOf(null)
        return combine(
            library.observeTrack(videoId),
            queue.tasks.map { tasks -> tasks.firstOrNull { it.request.videoId == videoId } },
            queue.progress,
        ) { track, task, progress -> VideoStatus(track, task, task?.let { progress[it.id] }) }
    }

    private fun emit(build: (Long) -> DownloadEvent) {
        event.value = build(++nextEventId)
    }

    private companion object {
        /** D-021: fetch formats after the user has stayed on a video for this long. */
        const val PREFETCH_DWELL_MS = 1_500L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

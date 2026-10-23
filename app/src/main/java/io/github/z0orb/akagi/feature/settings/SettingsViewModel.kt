package io.github.z0orb.akagi.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.data.prefs.AutoUpdate
import io.github.z0orb.akagi.data.prefs.Settings
import io.github.z0orb.akagi.data.prefs.SettingsRepository
import io.github.z0orb.akagi.ytdlp.FormatTier
import io.github.z0orb.akagi.ytdlp.UpdateResult
import io.github.z0orb.akagi.ytdlp.YtDlpEngine
import io.github.z0orb.akagi.ytdlp.YtDlpUpdater
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Null until yt-dlp has finished its first start. */
    val ytDlpVersion: String? = null,
    val update: UpdateStatus = UpdateStatus.Idle,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus

    data object Running : UpdateStatus

    data object UpToDate : UpdateStatus

    data class Updated(val version: String?) : UpdateStatus

    data class Failed(val error: AppError) : UpdateStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val engine: YtDlpEngine,
    private val updater: YtDlpUpdater,
) : ViewModel() {

    private val version = MutableStateFlow<String?>(null)
    private val update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, version, update, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    init {
        viewModelScope.launch { version.value = runCatching { engine.version() }.getOrNull() }
    }

    fun onCheckUpdate() {
        if (update.value == UpdateStatus.Running) return
        update.value = UpdateStatus.Running
        viewModelScope.launch {
            update.value = try {
                when (val result = updater.updateNow()) {
                    UpdateResult.AlreadyUpToDate -> UpdateStatus.UpToDate
                    is UpdateResult.Updated -> UpdateStatus.Updated(result.version)
                }
            } catch (e: AppException) {
                UpdateStatus.Failed(e.error)
            }
            version.value = runCatching { engine.version() }.getOrNull()
        }
    }

    fun onNightlyChanged(nightly: Boolean) = save { repository.setNightlyYtDlp(nightly) }

    fun onAutoUpdateChanged(mode: AutoUpdate) = save { repository.setAutoUpdate(mode) }

    fun onPrefetchChanged(enabled: Boolean) = save { repository.setPrefetchFormats(enabled) }

    /** The format sheet starts on this codec's recommendation next time. */
    fun onPreferredCodecChanged(codec: AudioCodec) =
        save { repository.rememberChoice(codec, FormatTier.RECOMMENDED.name) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

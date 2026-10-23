package io.github.z0orb.akagi.feature.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.account.AccountRepository
import io.github.z0orb.akagi.account.CookieImportResult
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.common.SignInState
import io.github.z0orb.akagi.data.prefs.AutoUpdate
import io.github.z0orb.akagi.data.prefs.CookiePolicy
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Null until yt-dlp has finished its first start. */
    val ytDlpVersion: String? = null,
    val update: UpdateStatus = UpdateStatus.Idle,
    val signIn: SignInState = SignInState(),
    /** The outcome of the last cookies.txt import, shown under the account buttons. */
    val cookieImport: CookieImportResult? = null,
    /** Something the UI should do once, then acknowledge with [SettingsViewModel.onEventHandled]. */
    val event: SettingsEvent? = null,
)

sealed interface SettingsEvent {
    val id: Long

    /** The browser should reload, so YouTube shows the signed-out page. */
    data class SignedOut(override val id: Long) : SettingsEvent
}

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
    private val account: AccountRepository,
) : ViewModel() {

    private val version = MutableStateFlow<String?>(null)
    private val update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    private val cookieImport = MutableStateFlow<CookieImportResult?>(null)
    private val event = MutableStateFlow<SettingsEvent?>(null)
    private var nextEventId = 0L

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(repository.settings, version, update, ::Triple),
        account.state,
        cookieImport,
        event,
    ) { (settings, version, update), signIn, cookieImport, event ->
        SettingsUiState(settings, version, update, signIn, cookieImport, event)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    init {
        account.refresh()
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

    fun onCookiePolicyChanged(policy: CookiePolicy) = save { repository.setCookiePolicy(policy) }

    fun onImportCookies(uri: Uri) {
        viewModelScope.launch { cookieImport.value = account.importCookies(uri) }
    }

    fun onSignOut() {
        viewModelScope.launch {
            account.signOut()
            cookieImport.value = null
            event.value = SettingsEvent.SignedOut(++nextEventId)
        }
    }

    fun onEventHandled(id: Long) = event.update { if (it?.id == id) null else it }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

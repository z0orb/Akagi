package io.github.z0orb.akagi.feature.settings

import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.account.AccountRepository
import io.github.z0orb.akagi.account.CookieImportResult
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.common.SignInState
import io.github.z0orb.akagi.core.common.toAppError
import io.github.z0orb.akagi.data.db.LibrarySize
import io.github.z0orb.akagi.data.library.LibraryRepository
import io.github.z0orb.akagi.data.prefs.AutoUpdate
import io.github.z0orb.akagi.data.prefs.CookiePolicy
import io.github.z0orb.akagi.data.prefs.Settings
import io.github.z0orb.akagi.data.prefs.SettingsRepository
import io.github.z0orb.akagi.data.storage.AppStorage
import io.github.z0orb.akagi.data.storage.StorageUsage
import io.github.z0orb.akagi.playback.AudioRoute
import io.github.z0orb.akagi.playback.AudioRouteMonitor
import io.github.z0orb.akagi.ytdlp.FormatTier
import io.github.z0orb.akagi.ytdlp.UpdateResult
import io.github.z0orb.akagi.ytdlp.YtDlpEngine
import io.github.z0orb.akagi.ytdlp.YtDlpUpdater
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    /** Tracks in the library and their total size; null until loaded. */
    val library: LibrarySize? = null,
    /** Akagi's own storage; null until measured. */
    val appStorage: AppStorage? = null,
    /** Where audio goes now, for the Audio output section (PLAN §7.19). */
    val audio: AudioRoute = AudioRoute(),
    /** Matching the DAC's rate needs Android 14's mixer attributes. */
    val matchSampleRateSupported: Boolean = false,
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
    library: LibraryRepository,
    private val storageUsage: StorageUsage,
    routes: AudioRouteMonitor,
) : ViewModel() {

    private val version = MutableStateFlow<String?>(null)
    private val update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    private val cookieImport = MutableStateFlow<CookieImportResult?>(null)
    private val event = MutableStateFlow<SettingsEvent?>(null)
    private val appStorage = MutableStateFlow<AppStorage?>(null)
    private var nextEventId = 0L

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(repository.settings, version, update, ::Triple),
        account.state,
        combine(cookieImport, event, ::Pair),
        combine(library.observeLibrarySize(), appStorage, ::Pair),
        routes.route,
    ) { (settings, version, update), signIn, (cookieImport, event), (librarySize, storage), audio ->
        SettingsUiState(
            settings = settings,
            ytDlpVersion = version,
            update = update,
            signIn = signIn,
            cookieImport = cookieImport,
            library = librarySize,
            appStorage = storage,
            audio = audio,
            matchSampleRateSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            event = event,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /**
     * Called each time the overlay opens. This ViewModel lives as long as the activity, so values read once in
     * `init` went stale after downloads or the weekly yt-dlp update (B-09).
     */
    fun onShown() {
        account.refresh()
        viewModelScope.launch { version.value = runCatching { engine.version() }.getOrNull() }
        viewModelScope.launch { appStorage.value = storageUsage.measure() }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateStatus.Failed(e.toAppError())
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

    fun onPreferUsbDacChanged(enabled: Boolean) = save { repository.setPreferUsbDac(enabled) }

    fun onMatchSampleRateChanged(enabled: Boolean) = save { repository.setMatchSampleRate(enabled) }

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

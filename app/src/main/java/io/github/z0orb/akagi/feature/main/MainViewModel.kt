package io.github.z0orb.akagi.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z0orb.akagi.core.common.DispatcherProvider
import io.github.z0orb.akagi.core.crash.CrashLogStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val crashLogStore: CrashLogStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val report = withContext(dispatchers.io) { crashLogStore.read() }
            _uiState.update { it.copy(crashReport = report) }
        }
    }

    /** Shared or dismissed: either way the report is not offered again. */
    fun onCrashReportHandled() {
        _uiState.update { it.copy(crashReport = null) }
        viewModelScope.launch(dispatchers.io) { crashLogStore.clear() }
    }
}

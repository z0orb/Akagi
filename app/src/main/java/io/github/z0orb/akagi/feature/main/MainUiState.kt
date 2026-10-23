package io.github.z0orb.akagi.feature.main

import androidx.compose.runtime.Immutable

@Immutable
data class MainUiState(
    /** The report of a crash in the previous run, offered for sharing once. */
    val crashReport: String? = null,
)

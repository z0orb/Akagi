package io.github.z0orb.akagi.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.playback.AudioRoute
import io.github.z0orb.akagi.playback.OutputFormat
import io.github.z0orb.akagi.playback.OutputKind

/**
 * Where Akagi's audio goes and in what format (PLAN §7.19). It doubles as the diagnostics view when
 * a DAC is tested without adb, since the phone's only port is taken by the DAC.
 */
@Composable
internal fun ColumnScope.AudioOutputSection(
    state: SettingsUiState,
    onPreferUsbDacChange: (Boolean) -> Unit,
    onMatchSampleRateChange: (Boolean) -> Unit,
) {
    val route = state.audio
    SectionTitle(stringResource(R.string.settings_section_audio))
    LabelValue(stringResource(R.string.settings_audio_output), outputLabel(route))
    if (route.dacConnected) {
        LabelValue(
            stringResource(R.string.settings_audio_dac_formats),
            route.caps?.let { OutputFormat.capabilities(it) } ?: stringResource(R.string.settings_audio_not_reported),
        )
    }
    LabelValue(
        stringResource(R.string.settings_audio_sends),
        route.sent?.let { OutputFormat.format(it) } ?: stringResource(R.string.settings_audio_nothing_playing),
    )
    if (route.kind == OutputKind.USB_DAC) {
        LabelValue(
            stringResource(R.string.settings_audio_dac_runs_at),
            route.dacFormat?.let { OutputFormat.format(it) } ?: stringResource(R.string.settings_audio_set_by_android),
        )
    }
    SwitchRow(
        title = stringResource(R.string.settings_prefer_usb_dac),
        summary = stringResource(R.string.settings_prefer_usb_dac_summary),
        checked = state.settings.preferUsbDac,
        onCheckedChange = onPreferUsbDacChange,
    )
    SwitchRow(
        title = stringResource(R.string.settings_match_sample_rate),
        summary = stringResource(
            if (state.matchSampleRateSupported) {
                R.string.settings_match_sample_rate_summary
            } else {
                R.string.settings_match_sample_rate_unsupported
            },
        ),
        checked = state.settings.matchSampleRate && state.matchSampleRateSupported,
        enabled = state.matchSampleRateSupported,
        onCheckedChange = onMatchSampleRateChange,
    )
}

@Composable
internal fun outputLabel(route: AudioRoute): String {
    val name = route.name?.takeIf { it.isNotBlank() }
    return when (route.kind) {
        OutputKind.SPEAKER -> stringResource(R.string.output_speaker)

        OutputKind.WIRED -> stringResource(R.string.output_wired)

        OutputKind.BLUETOOTH -> name?.let { stringResource(R.string.output_bluetooth_named, it) }
            ?: stringResource(R.string.output_bluetooth)

        OutputKind.USB_DAC -> name?.let { stringResource(R.string.output_usb_dac_named, it) }
            ?: stringResource(R.string.output_usb_dac)

        OutputKind.OTHER -> stringResource(R.string.output_other)
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val color = MaterialTheme.colorScheme.run { if (enabled) onSurface else onSurfaceVariant }
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

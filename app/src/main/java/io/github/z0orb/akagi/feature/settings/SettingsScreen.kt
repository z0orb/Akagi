package io.github.z0orb.akagi.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.designsystem.ChoiceRow
import io.github.z0orb.akagi.core.designsystem.OverlayTopBar
import io.github.z0orb.akagi.core.designsystem.describe
import io.github.z0orb.akagi.data.prefs.AutoUpdate
import io.github.z0orb.akagi.data.prefs.Settings

/**
 * Settings overlay (PLAN §7.14–7.15, D-004): an overlay, so the WebView underneath never reloads. [onSignIn] opens
 * YouTube's sign-in in the browser; [onSignedOut] reloads it after the cookies are gone.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(state.event) {
        val event = state.event ?: return@LaunchedEffect
        when (event) {
            is SettingsEvent.SignedOut -> currentOnSignedOut()
        }
        viewModel.onEventHandled(event.id)
    }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OverlayTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YtDlpSection(
                state = state,
                onCheckUpdate = viewModel::onCheckUpdate,
                onNightlyChange = viewModel::onNightlyChanged,
                onAutoUpdateChange = viewModel::onAutoUpdateChanged,
            )
            Spacer(Modifier.height(8.dp))
            DownloadsSection(
                settings = state.settings,
                onPreferredCodecChange = viewModel::onPreferredCodecChanged,
                onPrefetchChange = viewModel::onPrefetchChanged,
            )
            Spacer(Modifier.height(8.dp))
            AccountSection(
                state = state,
                actions = AccountActions(
                    onSignIn = onSignIn,
                    onSignOut = viewModel::onSignOut,
                    onImportCookies = viewModel::onImportCookies,
                    onCookiePolicyChange = viewModel::onCookiePolicyChanged,
                ),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ColumnScope.YtDlpSection(
    state: SettingsUiState,
    onCheckUpdate: () -> Unit,
    onNightlyChange: (Boolean) -> Unit,
    onAutoUpdateChange: (AutoUpdate) -> Unit,
) {
    val settings = state.settings
    SectionTitle(stringResource(R.string.settings_section_ytdlp))
    LabelValue(
        stringResource(R.string.settings_ytdlp_version),
        state.ytDlpVersion ?: stringResource(R.string.settings_ytdlp_version_unknown),
    )
    Label(stringResource(R.string.settings_ytdlp_channel))
    Choices(
        options = listOf(false to R.string.settings_channel_stable, true to R.string.settings_channel_nightly),
        selected = settings.nightlyYtDlp,
        onSelect = onNightlyChange,
    )
    // The status line moves under the button when both don't fit, instead of wrapping letter by letter.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onCheckUpdate, enabled = state.update != UpdateStatus.Running) {
            Text(stringResource(R.string.settings_check_update))
        }
        Text(
            text = updateLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.run {
                if (state.update is UpdateStatus.Failed) error else onSurfaceVariant
            },
        )
    }
    Label(stringResource(R.string.settings_auto_update))
    Choices(
        options = listOf(
            AutoUpdate.OFF to R.string.settings_auto_off,
            AutoUpdate.WIFI_ONLY to R.string.settings_auto_wifi,
            AutoUpdate.ANY_NETWORK to R.string.settings_auto_any,
        ),
        selected = settings.autoUpdate,
        onSelect = onAutoUpdateChange,
    )
}

@Composable
private fun ColumnScope.DownloadsSection(
    settings: Settings,
    onPreferredCodecChange: (AudioCodec) -> Unit,
    onPrefetchChange: (Boolean) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_section_downloads))
    Label(stringResource(R.string.settings_default_codec))
    Choices(
        options = listOf(AudioCodec.OPUS to R.string.codec_opus, AudioCodec.M4A to R.string.codec_m4a),
        selected = settings.lastCodec ?: AudioCodec.OPUS,
        onSelect = onPreferredCodecChange,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = settings.prefetchFormats, role = Role.Switch, onValueChange = onPrefetchChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_prefetch), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_prefetch_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = settings.prefetchFormats, onCheckedChange = null)
    }
}

@Composable
private fun updateLine(state: SettingsUiState): String = when (val update = state.update) {
    UpdateStatus.Running -> stringResource(R.string.settings_updating)

    UpdateStatus.UpToDate -> stringResource(R.string.settings_up_to_date)

    is UpdateStatus.Updated -> stringResource(R.string.settings_updated, update.version.orEmpty())

    is UpdateStatus.Failed -> update.error.describe(LocalContext.current.resources)

    UpdateStatus.Idle -> state.settings.lastUpdateCheckMillis.takeIf { it > 0 }
        ?.let { stringResource(R.string.settings_last_checked, DateUtils.getRelativeTimeSpanString(it).toString()) }
        ?: stringResource(R.string.settings_never_checked)
}

@Composable
internal fun <T> Choices(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    ChoiceRow(
        options = options.map { (value, label) -> value to stringResource(label) },
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun LabelValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

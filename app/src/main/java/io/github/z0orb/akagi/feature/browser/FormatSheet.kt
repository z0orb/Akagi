package io.github.z0orb.akagi.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.common.SizeFormatter
import io.github.z0orb.akagi.core.designsystem.codecLabel
import io.github.z0orb.akagi.core.designsystem.describe
import io.github.z0orb.akagi.core.designsystem.isRetryable
import io.github.z0orb.akagi.core.designsystem.suggestsUpdate
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.ytdlp.FormatOption
import io.github.z0orb.akagi.ytdlp.FormatTier

/** Everything the sheet can ask for; the screen forwards these to [DownloadViewModel]. */
class FormatSheetActions(
    val onDismiss: () -> Unit,
    val onCodecSelected: (AudioCodec) -> Unit,
    val onOptionSelected: (FormatOption) -> Unit,
    val onDownload: () -> Unit,
    val onDownloadAgain: () -> Unit,
    val onPlay: () -> Unit,
    val onCancelTask: (Long) -> Unit,
    val onRetryTask: (Long) -> Unit,
    val onRetryFetch: () -> Unit,
    val onUpdateYtDlp: () -> Unit,
)

/** Codec toggle, bitrate list with sizes and the recommendation, or the video's download status (PLAN §7.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(state: SheetUiState, actions: FormatSheetActions, modifier: Modifier = Modifier) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (val content = state.content) {
                SheetContent.Loading -> Busy(stringResource(R.string.sheet_loading))
                SheetContent.UpdatingYtDlp -> Busy(stringResource(R.string.settings_updating))
                is SheetContent.Failed -> Failure(content.error, actions)
                is SheetContent.Choose -> Choose(content, actions)
                is SheetContent.InLibrary -> InLibrary(content, actions)
                is SheetContent.Active -> Active(content, actions)
            }
        }
    }
}

@Composable
private fun Busy(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColumnScope.Failure(error: AppError, actions: FormatSheetActions) {
    ErrorText(error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error.suggestsUpdate) {
            Button(onClick = actions.onUpdateYtDlp) {
                Text(stringResource(R.string.action_update_ytdlp))
            }
        }
        if (error.isRetryable || error.suggestsUpdate) {
            OutlinedButton(onClick = actions.onRetryFetch) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun ErrorText(error: AppError) {
    Text(
        text = error.describe(LocalContext.current.resources),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ColumnScope.Choose(content: SheetContent.Choose, actions: FormatSheetActions) {
    val codecs = content.choices.options.keys.toList()
    if (content.choices.isMp3Fallback) {
        Text(
            stringResource(R.string.sheet_mp3_fallback),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (codecs.size > 1) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            codecs.forEachIndexed { index, codec ->
                SegmentedButton(
                    selected = codec == content.codec,
                    onClick = { actions.onCodecSelected(codec) },
                    shape = SegmentedButtonDefaults.itemShape(index, codecs.size),
                ) { Text(codecLabel(codec)) }
            }
        }
    }
    Column(modifier = Modifier.selectableGroup()) {
        content.choices.options[content.codec].orEmpty().forEach { option ->
            OptionRow(option, selected = option == content.selected, onSelect = { actions.onOptionSelected(option) })
        }
    }
    if (content.selected.tier == FormatTier.RECOMMENDED) {
        Text(
            stringResource(R.string.sheet_recommended_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = actions.onDownload, modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.sheet_download))
    }
}

@Composable
private fun OptionRow(option: FormatOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.sheet_kbps, option.bitrateKbps), style = MaterialTheme.typography.bodyLarge)
        option.tier?.let {
            Spacer(Modifier.width(8.dp))
            TierChip(it)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = option.sizeBytes?.let { stringResource(R.string.sheet_size_approx, SizeFormatter.megabytes(it)) }
                ?: stringResource(R.string.sheet_size_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TierChip(tier: FormatTier) {
    val colors = MaterialTheme.colorScheme
    val recommended = tier == FormatTier.RECOMMENDED
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (recommended) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (recommended) colors.onPrimaryContainer else colors.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(tierLabel(tier)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ColumnScope.InLibrary(content: SheetContent.InLibrary, actions: FormatSheetActions) {
    Text(
        stringResource(
            R.string.sheet_already_downloaded,
            codecLabel(content.codec),
            SizeFormatter.megabytes(content.sizeBytes),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = actions.onPlay) { Text(stringResource(R.string.action_play)) }
        OutlinedButton(onClick = actions.onDownloadAgain) { Text(stringResource(R.string.action_download_again)) }
    }
}

@Composable
private fun ColumnScope.Active(content: SheetContent.Active, actions: FormatSheetActions) {
    val error = content.error
    if (content.state == DownloadState.FAILED && error != null) {
        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.onRetryTask(content.taskId) }) { Text(stringResource(R.string.action_retry)) }
            TextButton(onClick = {
                actions.onCancelTask(content.taskId)
            }) { Text(stringResource(R.string.action_cancel)) }
        }
        return
    }
    val progress = content.progress
    Text(
        text = when {
            content.state == DownloadState.QUEUED -> stringResource(R.string.sheet_queued)
            content.state == DownloadState.POST_PROCESSING -> stringResource(R.string.sheet_processing)
            else -> stringResource(R.string.sheet_downloading, ((progress ?: 0f) * 100).toInt())
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (progress != null && content.state == DownloadState.RUNNING) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = { actions.onCancelTask(content.taskId) }) { Text(stringResource(R.string.action_cancel)) }
}

private fun tierLabel(tier: FormatTier): Int = when (tier) {
    FormatTier.RECOMMENDED -> R.string.tier_recommended
    FormatTier.DATA_SAVER -> R.string.tier_data_saver
    FormatTier.SMALL -> R.string.tier_small
    FormatTier.HIGHEST -> R.string.tier_highest
}

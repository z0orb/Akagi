package io.github.z0orb.akagi.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.SizeFormatter
import io.github.z0orb.akagi.core.designsystem.codecLabel
import io.github.z0orb.akagi.core.designsystem.describe
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.download.DownloadTask
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import java.io.File

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(24.dp),
    )
}

@Composable
internal fun TaskRow(
    task: DownloadTask,
    progress: DownloadProgress?,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.request.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TaskStatus(task, progress)
        }
        if (task.state == DownloadState.FAILED) {
            IconButton(onClick = { onRetry(task.id) }) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_retry))
            }
        }
        IconButton(onClick = { onCancel(task.id) }) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun TaskStatus(task: DownloadTask, progress: DownloadProgress?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { TaskStatusLines(task, progress) }
}

@Composable
private fun ColumnScope.TaskStatusLines(task: DownloadTask, progress: DownloadProgress?) {
    val error = task.error
    when {
        task.state == DownloadState.FAILED && error != null -> Text(
            stringResource(R.string.downloads_failed, error.describe(LocalContext.current.resources)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        task.state == DownloadState.RUNNING && progress != null -> {
            Text(
                stringResource(
                    R.string.downloads_progress,
                    (progress.fraction * 100).toInt(),
                    progress.speed.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }

        else -> {
            val status = if (task.state == DownloadState.QUEUED) R.string.sheet_queued else R.string.sheet_processing
            Text(
                stringResource(status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Track row (PLAN §7.12): thumbnail, title, size and codec; [trailing] holds row actions. */
@Composable
internal fun TrackRow(
    track: TrackEntity,
    guard: ScrollGuard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = 16.dp,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .deliberateClickable(guard, interaction, onClick)
            .padding(start = startPadding, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        leading()
        Thumbnail(track.thumbnailPath, modifier = if (track.missing) Modifier.alpha(MISSING_ALPHA) else Modifier)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val colors = MaterialTheme.colorScheme
                Text(
                    if (track.missing) {
                        stringResource(
                            R.string.track_missing,
                        )
                    } else {
                        SizeFormatter.megabytes(track.sizeBytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (track.missing) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Weighted, so at narrow widths the size shortens and the codec chip stays whole.
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!track.missing) {
                    Spacer(Modifier.width(8.dp))
                    CodecChip(codecLabel(track.codec))
                }
            }
        }
        trailing()
    }
}

@Composable
internal fun Thumbnail(path: String?, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (path == null) {
            Icon(
                painterResource(R.drawable.ic_library),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CodecChip(label: String) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

internal class MenuEntry(val label: Int, val onClick: () -> Unit)

/** The ⋮ button at the end of a row and its dropdown. */
@Composable
internal fun RowMenu(entries: List<MenuEntry>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.track_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label)) },
                    onClick = {
                        expanded = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}
private const val MISSING_ALPHA = 0.4f

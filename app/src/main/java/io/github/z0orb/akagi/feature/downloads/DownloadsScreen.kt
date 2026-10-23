package io.github.z0orb.akagi.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.SizeFormatter
import io.github.z0orb.akagi.core.designsystem.OverlayTopBar
import io.github.z0orb.akagi.core.designsystem.codecLabel
import io.github.z0orb.akagi.core.designsystem.describe
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.download.DownloadTask
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import java.io.File

/** Plain list of active downloads and the library (P3.15), so downloads can be tested before the sidebar. */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OverlayTopBar(title = stringResource(R.string.downloads_title), onBack = onBack)
        if (state.tasks.isEmpty() && state.tracks.isEmpty()) {
            Text(
                stringResource(R.string.downloads_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.tasks.isNotEmpty()) {
                item(key = "active-header", contentType = "header") {
                    Header(stringResource(R.string.downloads_active))
                }
                items(state.tasks, key = { "task-${it.id}" }, contentType = { "task" }) { task ->
                    TaskRow(task, state.progress[task.id], viewModel::onCancel, viewModel::onRetry)
                }
            }
            if (state.tracks.isNotEmpty()) {
                item(key = "library-header", contentType = "header") {
                    Header(stringResource(R.string.downloads_library))
                }
                items(state.tracks, key = { "track-${it.id}" }, contentType = { "track" }) { track ->
                    TrackRow(track, onClick = { onOpenTrack(track) })
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    progress: DownloadProgress?,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.request.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    Text(
                        stringResource(
                            if (task.state ==
                                DownloadState.QUEUED
                            ) {
                                R.string.sheet_queued
                            } else {
                                R.string.sheet_processing
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
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
private fun TrackRow(track: TrackEntity, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            track.thumbnailPath?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                SizeFormatter.megabytes(track.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                codecLabel(track.codec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

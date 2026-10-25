package io.github.z0orb.akagi.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R

private const val ANIMATION_MS = 220

/**
 * Download button for the video on screen (PLAN §7.3): tap opens the format sheet, long-press downloads
 * with the remembered choice. Shows fetching, progress and already-downloaded states. [state] is read inside the
 * button, so a progress tick redraws only the button (PF-02).
 */
@Composable
fun DownloadFab(
    visible: Boolean,
    state: () -> FabState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(tween(ANIMATION_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(ANIMATION_MS)),
        exit = scaleOut(tween(ANIMATION_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(ANIMATION_MS)),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp),
        ) {
            // Inside the surface, so the ripple is clipped to its shape.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.action_download),
                    onLongClickLabel = stringResource(R.string.action_quick_download),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            ) {
                FabContent(state())
            }
        }
    }
}

@Composable
private fun BoxScope.FabContent(state: FabState) {
    val ringModifier = Modifier.size(40.dp)
    when (state) {
        FabState.Fetching -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)

        is FabState.Downloading -> {
            val progress = state.progress
            if (progress == null) {
                CircularProgressIndicator(modifier = ringModifier, strokeWidth = 2.5.dp)
            } else {
                CircularProgressIndicator(progress = { progress }, modifier = ringModifier, strokeWidth = 2.5.dp)
            }
            FabIcon(R.drawable.ic_download, R.string.fab_downloading, Modifier.size(18.dp))
        }

        FabState.Downloaded -> FabIcon(R.drawable.ic_check, R.string.fab_downloaded)

        FabState.Idle -> FabIcon(R.drawable.ic_download, R.string.action_download)
    }
}

@Composable
private fun FabIcon(icon: Int, description: Int, modifier: Modifier = Modifier) {
    Icon(painter = painterResource(icon), contentDescription = stringResource(description), modifier = modifier)
}

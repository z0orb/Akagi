package io.github.z0orb.akagi.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.z0orb.akagi.R

private const val ANIMATION_MS = 220

/** Download button for the video on screen (PLAN §7.3). Progress states arrive with downloads in Phase 3. */
@Composable
fun DownloadFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(tween(ANIMATION_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(ANIMATION_MS)),
        exit = scaleOut(tween(ANIMATION_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(ANIMATION_MS)),
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = stringResource(R.string.action_download),
            )
        }
    }
}

package io.github.z0orb.akagi.feature.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.z0orb.akagi.R

@Composable
fun CrashReportDialog(onShare: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.crash_dialog_title)) },
        text = { Text(stringResource(R.string.crash_dialog_body)) },
        confirmButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.crash_dialog_share)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crash_dialog_dismiss)) } },
    )
}

package io.github.z0orb.akagi.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.account.CookieImportResult
import io.github.z0orb.akagi.data.prefs.CookiePolicy

internal class AccountActions(
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    val onImportCookies: (Uri) -> Unit,
    val onCookiePolicyChange: (CookiePolicy) -> Unit,
)

/** Sign-in status, sign in/out, the cookies.txt fallback and the cookie policy (PLAN §7.15). */
@Composable
internal fun ColumnScope.AccountSection(state: SettingsUiState, actions: AccountActions) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    // Exporters label cookies.txt anything from text/plain to octet-stream.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions.onImportCookies)
    }
    val signedIn = state.signIn.signedIn
    SectionTitle(stringResource(R.string.settings_section_account))
    LabelValue(
        stringResource(R.string.settings_account_status),
        stringResource(if (signedIn) R.string.settings_account_signed_in else R.string.settings_account_signed_out),
    )
    Hint(stringResource(R.string.settings_account_summary))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (signedIn) {
            OutlinedButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.settings_sign_out)) }
        } else {
            Button(onClick = actions.onSignIn) { Text(stringResource(R.string.action_sign_in)) }
        }
        // A fallback for when Google refuses the in-app sign-in, so it stays low-key.
        TextButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) {
            Text(stringResource(R.string.settings_import_cookies))
        }
    }
    val imported = state.cookieImport
    if (imported != null) {
        Text(
            text = stringResource(importMessage(imported)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.run { if (imported == CookieImportResult.SignedIn) primary else error },
        )
    } else if (!signedIn) {
        Hint(stringResource(R.string.settings_import_hint))
    }
    Label(stringResource(R.string.settings_cookie_policy))
    Choices(
        options = listOf(
            CookiePolicy.ONLY_WHEN_NEEDED to R.string.settings_cookie_when_needed,
            CookiePolicy.ALWAYS to R.string.settings_cookie_always,
            CookiePolicy.NEVER to R.string.settings_cookie_never,
        ),
        selected = state.settings.cookiePolicy,
        onSelect = actions.onCookiePolicyChange,
    )
    Hint(stringResource(policySummary(state.settings.cookiePolicy)))
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    actions.onSignOut()
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun importMessage(result: CookieImportResult): Int = when (result) {
    CookieImportResult.SignedIn -> R.string.settings_import_signed_in
    CookieImportResult.NoSignIn -> R.string.settings_import_no_sign_in
    CookieImportResult.NoYouTubeCookies -> R.string.settings_import_no_youtube
    CookieImportResult.Unreadable -> R.string.settings_import_unreadable
}

private fun policySummary(policy: CookiePolicy): Int = when (policy) {
    CookiePolicy.ONLY_WHEN_NEEDED -> R.string.settings_cookie_when_needed_summary
    CookiePolicy.ALWAYS -> R.string.settings_cookie_always_summary
    CookiePolicy.NEVER -> R.string.settings_cookie_never_summary
}

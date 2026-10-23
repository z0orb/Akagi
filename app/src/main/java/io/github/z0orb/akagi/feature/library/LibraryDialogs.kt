package io.github.z0orb.akagi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.data.db.PlaylistSummary

@Composable
internal fun LibraryDialogHost(dialog: LibraryDialog, playlists: List<PlaylistSummary>, actions: LibraryActions) {
    when (dialog) {
        is LibraryDialog.AddToPlaylist -> AddToPlaylistDialog(dialog, playlists, actions)

        is LibraryDialog.NamePlaylist -> NamePlaylistDialog(dialog, actions)

        is LibraryDialog.DeleteTrack -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.track_delete_title, dialog.track.title)) },
            text = {
                val body = stringResource(R.string.track_delete_body)
                Text(
                    if (dialog.playlistCount == 0) {
                        body
                    } else {
                        body + " " +
                            pluralStringResource(
                                R.plurals.track_delete_playlists,
                                dialog.playlistCount,
                                dialog.playlistCount,
                            )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDeleteTrack) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is LibraryDialog.DeletePlaylist -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.playlist_delete_title, dialog.name)) },
            text = { Text(stringResource(R.string.playlist_delete_body)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDeletePlaylist) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun NamePlaylistDialog(dialog: LibraryDialog.NamePlaylist, actions: LibraryActions) {
    var name by rememberSaveable { mutableStateOf(dialog.initialName) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val creating = dialog.playlistId == null
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(if (creating) R.string.playlist_new else R.string.playlist_rename)) },
        text = {
            NameField(
                value = name,
                onValueChange = { name = it },
                error = dialog.error,
                onDone = { actions.onSubmitPlaylistName(name) },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { actions.onSubmitPlaylistName(name) }) {
                Text(stringResource(if (creating) R.string.action_create else R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AddToPlaylistDialog(
    dialog: LibraryDialog.AddToPlaylist,
    playlists: List<PlaylistSummary>,
    actions: LibraryActions,
) {
    var draft by rememberSaveable(dialog.draftGeneration) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(R.string.track_add_to_playlist)) },
        text = {
            Column {
                Text(
                    dialog.track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { actions.onToggleMembership(playlist.id) },
                        ) {
                            Checkbox(checked = playlist.id in dialog.selected, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NameField(
                        value = draft,
                        onValueChange = { draft = it },
                        error = dialog.nameError,
                        onDone = { actions.onCreatePlaylistInline(draft) },
                        label = R.string.playlist_new,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { actions.onCreatePlaylistInline(draft) }) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.action_create),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmMemberships) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    error: NameError?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    label: Int = R.string.playlist_name_hint,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    stringResource(
                        when (it) {
                            NameError.BLANK -> R.string.playlist_name_blank
                            NameError.TOO_LONG -> R.string.playlist_name_too_long
                        },
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
    )
}

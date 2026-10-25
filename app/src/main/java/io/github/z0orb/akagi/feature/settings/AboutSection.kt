package io.github.z0orb.akagi.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.z0orb.akagi.BuildConfig
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.designsystem.OverlayTopBar

private const val SOURCE_URL = "https://github.com/z0orb/Akagi"
private const val APACHE_2 = "https://www.apache.org/licenses/LICENSE-2.0"

/** A bundled component and the licence it is distributed under (README "Credits and licences"). */
private class Component(val name: String, val licence: String, val url: String)

private val components = listOf(
    Component("yt-dlp", "Unlicense", "https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE"),
    Component("youtubedl-android", "GPL-3.0", "https://github.com/JunkFood02/youtubedl-android"),
    Component("FFmpeg", "GPL / LGPL", "https://ffmpeg.org/legal.html"),
    Component("Python", "PSF License", "https://docs.python.org/3/license.html"),
    Component("QuickJS", "MIT", "https://bellard.org/quickjs/"),
    Component("mutagen", "GPL-2.0-or-later", "https://github.com/quodlibet/mutagen"),
    Component("AndroidX, Jetpack Compose, Room, DataStore", "Apache-2.0", APACHE_2),
    Component("Media3 (ExoPlayer, MediaSession)", "Apache-2.0", APACHE_2),
    Component("Material Components and Material Symbols", "Apache-2.0", APACHE_2),
    Component("Kotlin and kotlinx.coroutines", "Apache-2.0", APACHE_2),
    Component("Dagger / Hilt", "Apache-2.0", APACHE_2),
    Component("Coil", "Apache-2.0", APACHE_2),
    Component("Reorderable", "Apache-2.0", APACHE_2),
    Component("Jackson, Apache Commons Compress", "Apache-2.0", APACHE_2),
)

/** Version, licence and source (P7.5); the GPL asks that users can find the source. */
@Composable
internal fun ColumnScope.AboutSection(onShowLicences: () -> Unit) {
    val openLink = rememberLinkOpener()
    SectionTitle(stringResource(R.string.settings_section_about))
    LabelValue(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
    Text(
        stringResource(R.string.settings_about_licence),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
        TextButton(onClick = { openLink(SOURCE_URL) }) { Text(stringResource(R.string.settings_source)) }
        TextButton(onClick = onShowLicences) { Text(stringResource(R.string.settings_licences)) }
    }
}

/** The bundled components and their licences; each row opens the licence text in the browser. */
@Composable
internal fun LicencesPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val openLink = rememberLinkOpener()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OverlayTopBar(title = stringResource(R.string.settings_licences), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(components, key = { it.name }) { component ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { openLink(component.url) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(component.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        component.licence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Opens a link in the browser, or says so when no app can (all browsers disabled) instead of crashing (B-17). */
@Composable
private fun rememberLinkOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    return remember(uriHandler, context) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.link_no_app, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

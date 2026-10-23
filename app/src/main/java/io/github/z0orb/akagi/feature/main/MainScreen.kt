package io.github.z0orb.akagi.feature.main

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.feature.browser.BrowserHost
import io.github.z0orb.akagi.feature.browser.BrowserTopBar
import io.github.z0orb.akagi.feature.browser.BrowserViewModel
import io.github.z0orb.akagi.feature.browser.DownloadFab
import io.github.z0orb.akagi.feature.browser.FullscreenVideo
import io.github.z0orb.akagi.feature.browser.rememberBrowserController

/**
 * App root (PLAN §5): top bar, the WebView area with its overlays, and the fullscreen video layer.
 * The sidebar (Phase 4) and player (Phase 5) slots are marked below.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    browserViewModel: BrowserViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
) {
    val browserState by browserViewModel.uiState.collectAsStateWithLifecycle()
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val controller = rememberBrowserController()
    val context = LocalContext.current
    val fullscreenView = controller.fullscreenView

    LaunchedEffect(browserState.pendingLoad) {
        browserState.pendingLoad?.let { load ->
            controller.load(load.url)
            browserViewModel.onLoadHandled(load.id)
        }
    }

    // Back order (PLAN §7.1): fullscreen → [player, sidebar: later phases] → WebView history → exit.
    // The handler registered last wins, so the most specific one comes last.
    BackHandler(enabled = browserState.canGoBack) { controller.goBack() }
    BackHandler(enabled = fullscreenView != null) { controller.exitFullscreen() }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            BrowserTopBar(onSubmit = browserViewModel::onQuerySubmit, onHomeClick = browserViewModel::onHomeClick)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BrowserHost(
                    controller = controller,
                    startUrl = browserState.startUrl,
                    onPageChange = browserViewModel::onPageChange,
                    modifier = Modifier.fillMaxSize(),
                )
                DownloadFab(
                    visible = browserState.currentVideo != null && fullscreenView == null,
                    onClick = { showToast(context, R.string.download_coming_soon) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
                // Sidebar handle overlay: Phase 4.
            }
            // Mini player: Phase 5.
        }
        // Sidebar panel: Phase 4.
        fullscreenView?.let { FullscreenVideo(view = it, modifier = Modifier.fillMaxSize()) }

        if (mainState.crashReport != null) {
            CrashReportDialog(
                onShare = {
                    mainState.crashReport?.let { shareCrashReport(context, it) }
                    mainViewModel.onCrashReportHandled()
                },
                onDismiss = mainViewModel::onCrashReportHandled,
            )
        }
    }
}

private fun showToast(context: Context, message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun shareCrashReport(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_share_subject))
        .putExtra(Intent.EXTRA_TEXT, report)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.crash_dialog_share)))
}

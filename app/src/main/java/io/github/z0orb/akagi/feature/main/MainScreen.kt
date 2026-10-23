package io.github.z0orb.akagi.feature.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.feature.browser.BrowserHost
import io.github.z0orb.akagi.feature.browser.BrowserTopBar
import io.github.z0orb.akagi.feature.browser.BrowserViewModel
import io.github.z0orb.akagi.feature.browser.DownloadEvent
import io.github.z0orb.akagi.feature.browser.DownloadFab
import io.github.z0orb.akagi.feature.browser.DownloadViewModel
import io.github.z0orb.akagi.feature.browser.FormatSheet
import io.github.z0orb.akagi.feature.browser.FormatSheetActions
import io.github.z0orb.akagi.feature.browser.FullscreenVideo
import io.github.z0orb.akagi.feature.browser.rememberBrowserController
import io.github.z0orb.akagi.feature.library.LibraryActions
import io.github.z0orb.akagi.feature.library.LibraryEvent
import io.github.z0orb.akagi.feature.library.LibraryPanel
import io.github.z0orb.akagi.feature.library.LibraryViewModel
import io.github.z0orb.akagi.feature.library.SidePanel
import io.github.z0orb.akagi.feature.library.rememberSidePanelState
import io.github.z0orb.akagi.feature.player.PlayerActions
import io.github.z0orb.akagi.feature.player.PlayerBar
import io.github.z0orb.akagi.feature.player.PlayerViewModel
import io.github.z0orb.akagi.feature.settings.SettingsScreen
import kotlinx.coroutines.launch

/** Full-screen pages drawn over the browser, so the WebView never leaves composition (D-004). */
private enum class Overlay { NONE, SETTINGS }

/**
 * App root (PLAN §5): top bar, the WebView area with its overlays, the player, and the fullscreen video layer.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    browserViewModel: BrowserViewModel = viewModel(),
    downloadViewModel: DownloadViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val browserState by browserViewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by downloadViewModel.uiState.collectAsStateWithLifecycle()
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    val playerActions = remember(playerViewModel) {
        PlayerActions(
            onPlayPause = playerViewModel::onPlayPause,
            onNext = playerViewModel::onNext,
            onPrevious = playerViewModel::onPrevious,
            onSeek = playerViewModel::onSeek,
            onToggleShuffle = playerViewModel::onToggleShuffle,
            onCycleRepeat = playerViewModel::onCycleRepeat,
            positionMs = playerViewModel::positionMs,
        )
    }
    val controller = rememberBrowserController()
    val sidePanel = rememberSidePanelState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fullscreenView = controller.fullscreenView
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    // Downloads work without it; progress then shows in the app only (PLAN §7.5).
    val deleteConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        libraryViewModel.onDeleteConsentResult(granted = it.resultCode == Activity.RESULT_OK)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val sheetActions = remember(downloadViewModel) {
        FormatSheetActions(
            onDismiss = downloadViewModel::onDismissSheet,
            onCodecSelected = downloadViewModel::onCodecSelected,
            onOptionSelected = downloadViewModel::onOptionSelected,
            onDownload = downloadViewModel::onDownloadClick,
            onDownloadAgain = downloadViewModel::onDownloadAgainClick,
            onPlay = downloadViewModel::onPlayClick,
            onCancelTask = downloadViewModel::onCancelTask,
            onRetryTask = downloadViewModel::onRetryTask,
            onRetryFetch = downloadViewModel::onRetryFetch,
            onUpdateYtDlp = downloadViewModel::onUpdateYtDlp,
        )
    }
    val libraryActions = remember(libraryViewModel) {
        LibraryActions(
            onSelectTab = libraryViewModel::onSelectTab,
            onPlayTrack = libraryViewModel::onPlayTrack,
            onPlayPlaylistTrack = libraryViewModel::onPlayPlaylistTrack,
            onPlayPlaylist = libraryViewModel::onPlayPlaylist,
            onCancelTask = libraryViewModel::onCancelTask,
            onRetryTask = libraryViewModel::onRetryTask,
            onAddToPlaylist = libraryViewModel::onAddToPlaylistClick,
            onDeleteTrack = libraryViewModel::onDeleteTrackClick,
            onConfirmDeleteTrack = libraryViewModel::onConfirmDeleteTrack,
            onOpenPlaylist = libraryViewModel::onOpenPlaylist,
            onClosePlaylist = libraryViewModel::onClosePlaylist,
            onNewPlaylist = libraryViewModel::onNewPlaylistClick,
            onRenamePlaylist = libraryViewModel::onRenamePlaylistClick,
            onDeletePlaylist = libraryViewModel::onDeletePlaylistClick,
            onRemoveFromPlaylist = libraryViewModel::onRemoveFromPlaylist,
            onReorder = libraryViewModel::onReorder,
            onDismissDialog = libraryViewModel::onDismissDialog,
            onToggleMembership = libraryViewModel::onToggleMembership,
            onCreatePlaylistInline = libraryViewModel::onCreatePlaylistInline,
            onConfirmMemberships = libraryViewModel::onConfirmMemberships,
            onSubmitPlaylistName = libraryViewModel::onSubmitPlaylistName,
            onConfirmDeletePlaylist = libraryViewModel::onConfirmDeletePlaylist,
        )
    }

    // Akagi starting playback pauses YouTube (R11).
    LaunchedEffect(playerState.isPlaying) { if (playerState.isPlaying) controller.pauseMedia() }
    LaunchedEffect(browserState.pendingLoad) {
        browserState.pendingLoad?.let { load ->
            controller.load(load.url)
            browserViewModel.onLoadHandled(load.id)
        }
    }
    LaunchedEffect(browserState.currentVideo) { downloadViewModel.onVideoChanged(browserState.currentVideo?.videoId) }
    LaunchedEffect(downloadState.event) {
        val event = downloadState.event ?: return@LaunchedEffect
        when (event) {
            is DownloadEvent.Toast -> showToast(context, event.message)
        }
        downloadViewModel.onEventHandled(event.id)
    }
    LaunchedEffect(libraryState.event) {
        val event = libraryState.event ?: return@LaunchedEffect
        when (event) {
            is LibraryEvent.Toast -> showToast(context, event.message)

            is LibraryEvent.ConfirmFileDeletion -> deleteConsent.launch(
                IntentSenderRequest.Builder(event.request).build(),
            )
        }
        libraryViewModel.onEventHandled(event.id)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { libraryViewModel.onResume() }

    // Back order (PLAN §7.1): fullscreen → overlay → expanded player → sidebar → WebView history → exit.
    // The handler registered last wins, so the most specific one comes last.
    BackHandler(enabled = browserState.canGoBack) { controller.goBack() }
    BackHandler(enabled = sidePanel.isOpen) { scope.launch { sidePanel.close() } }
    BackHandler(enabled = playerExpanded && playerState.track != null) { playerExpanded = false }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = fullscreenView != null) { controller.exitFullscreen() }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            BrowserTopBar(
                onSubmit = browserViewModel::onQuerySubmit,
                onHomeClick = browserViewModel::onHomeClick,
                onSettingsClick = { overlay = Overlay.SETTINGS },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BrowserHost(
                    controller = controller,
                    startUrl = browserState.startUrl,
                    onPageChange = browserViewModel::onPageChange,
                    onWebAudioStart = playerViewModel::onWebMediaStarted,
                    onWebAudioUnplug = playerViewModel::onWebAudioUnplugged,
                    modifier = Modifier.fillMaxSize(),
                )
                DownloadFab(
                    visible = browserState.currentVideo != null && fullscreenView == null,
                    state = downloadState.fab,
                    onClick = {
                        askForNotifications()
                        downloadViewModel.onFabClick()
                    },
                    onLongClick = {
                        askForNotifications()
                        downloadViewModel.onFabLongClick()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
                SidePanel(state = sidePanel, handleVisible = fullscreenView == null) {
                    LibraryPanel(state = libraryState, actions = libraryActions)
                }
            }
            playerState.track?.let { track ->
                PlayerBar(
                    state = playerState,
                    track = track,
                    actions = playerActions,
                    expanded = playerExpanded,
                    onExpandedChange = { playerExpanded = it },
                )
            }
        }
        val overlayModifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        when (overlay) {
            Overlay.SETTINGS -> SettingsScreen(onBack = { overlay = Overlay.NONE }, modifier = overlayModifier)
            Overlay.NONE -> Unit
        }
        fullscreenView?.let { FullscreenVideo(view = it, modifier = Modifier.fillMaxSize()) }

        downloadState.sheet?.let { FormatSheet(state = it, actions = sheetActions) }

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

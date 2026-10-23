package io.github.z0orb.akagi.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.designsystem.theme.AkagiMotion
import io.github.z0orb.akagi.data.db.TrackEntity
import kotlinx.coroutines.launch

/**
 * Content of the side panel: Downloads | Playlists tabs, or an open playlist as a sub-page (PLAN §7.12). It stays
 * composed while the panel is closed, so [open] keeps back for the rest of the screen then.
 */
@Composable
fun LibraryPanel(
    state: LibraryUiState,
    actions: LibraryActions,
    open: Boolean,
    narrowWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val detail = state.openPlaylist
    val guard = rememberScrollGuard()
    // Composed after MainScreen's handlers, so back leaves the playlist before it closes the panel.
    BackHandler(enabled = open && detail != null, onBack = actions.onClosePlaylist)
    // A playlist slides in over the tabs and back out, like a page pushed on a stack.
    AnimatedContent(
        targetState = detail,
        contentKey = { it?.id },
        transitionSpec = {
            val opening = targetState != null
            val sign = if (opening) 1 else -1
            (fadeIn(AkagiMotion.enter()) + slideInHorizontally(AkagiMotion.enter()) { sign * it / 4 }) togetherWith
                (fadeOut(AkagiMotion.exit()) + slideOutHorizontally(AkagiMotion.exit()) { -sign * it / 4 })
        },
        label = "playlistPage",
        modifier = modifier,
    ) { shown ->
        if (shown != null) {
            PlaylistDetailPage(shown, actions, guard)
        } else {
            TabPager(state, actions, guard, narrowWidth)
        }
    }
    state.dialog?.let { LibraryDialogHost(it, state.playlists, actions) }
}

/** Tabs that also follow a horizontal swipe: left to Playlists, right back to Downloads. */
@Composable
private fun TabPager(
    state: LibraryUiState,
    actions: LibraryActions,
    guard: ScrollGuard,
    narrowWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = state.tab.ordinal) { LibraryTab.entries.size }
    TrackScroll(guard, pager)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { actions.onSelectTab(LibraryTab.entries[it]) }
    }
    Column(modifier = modifier.fillMaxSize()) {
        PanelTabs(pager, narrowWidth, onSelect = { scope.launch { pager.animateScrollToPage(it) } })
        HorizontalPager(
            state = pager,
            key = { LibraryTab.entries[it] },
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (LibraryTab.entries[page]) {
                LibraryTab.DOWNLOADS -> DownloadsTab(state, actions, guard)
                LibraryTab.PLAYLISTS -> PlaylistsTab(state.playlists, actions, guard)
            }
        }
    }
}

/**
 * Downloads | Playlists. Hand-rolled because Material's SecondaryTabRow re-measures through a SubcomposeLayout on
 * every frame of a Half ↔ Full drag (~2.7 ms on the test phone, P7.9). The indicator follows the pager while drawing.
 */
@Composable
private fun PanelTabs(pager: PagerState, narrowWidth: Dp, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tabCount = LibraryTab.entries.size
    // Sized against the Half width, so a drag never changes the labels' constraints and they never re-lay out.
    val labelWidth = narrowWidth / tabCount - TAB_PADDING * 2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TAB_HEIGHT)
            .selectableGroup()
            .drawWithContent {
                drawContent()
                val tabWidth = size.width / tabCount
                val divider = 1.dp.toPx()
                val indicator = 2.dp.toPx()
                drawRect(colors.outlineVariant, Offset(0f, size.height - divider), Size(size.width, divider))
                drawRect(
                    colors.primary,
                    Offset((pager.currentPage + pager.currentPageOffsetFraction) * tabWidth, size.height - indicator),
                    Size(tabWidth, indicator),
                )
            },
    ) {
        LibraryTab.entries.forEach { tab ->
            val selected = pager.currentPage == tab.ordinal
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.ordinal) }),
            ) {
                // Shrinks instead of wrapping when the panel is at Half width or the font scale is large.
                Text(
                    stringResource(tab.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                    modifier = Modifier.widthIn(max = labelWidth),
                )
            }
        }
    }
}

private val TAB_HEIGHT = 48.dp
private val TAB_PADDING = 16.dp

/** Every user intent the panel can raise, bundled so the panel's signatures stay small. */
class LibraryActions(
    val onSelectTab: (LibraryTab) -> Unit,
    val onPlayTrack: (TrackEntity) -> Unit,
    val onPlayPlaylistTrack: (TrackEntity) -> Unit,
    val onPlayPlaylist: (shuffle: Boolean) -> Unit,
    val onCancelTask: (Long) -> Unit,
    val onRetryTask: (Long) -> Unit,
    val onAddToPlaylist: (TrackEntity) -> Unit,
    val onDeleteTrack: (TrackEntity) -> Unit,
    val onConfirmDeleteTrack: () -> Unit,
    val onOpenPlaylist: (Long) -> Unit,
    val onClosePlaylist: () -> Unit,
    val onNewPlaylist: () -> Unit,
    val onRenamePlaylist: (id: Long, name: String) -> Unit,
    val onDeletePlaylist: (id: Long, name: String) -> Unit,
    val onRemoveFromPlaylist: (playlistId: Long, trackId: Long) -> Unit,
    val onReorder: (playlistId: Long, trackIds: List<Long>) -> Unit,
    val onDismissDialog: () -> Unit,
    val onToggleMembership: (Long) -> Unit,
    val onCreatePlaylistInline: (String) -> Unit,
    val onConfirmMemberships: () -> Unit,
    val onSubmitPlaylistName: (String) -> Unit,
    val onConfirmDeletePlaylist: () -> Unit,
)

@Composable
private fun DownloadsTab(state: LibraryUiState, actions: LibraryActions, guard: ScrollGuard) {
    if (state.tasks.isEmpty() && state.tracks.isEmpty()) {
        EmptyState(stringResource(R.string.downloads_empty))
        return
    }
    val list = rememberLazyListState()
    TrackScroll(guard, list)
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (state.tasks.isNotEmpty()) {
            item(key = "active-header", contentType = "header") {
                SectionHeader(stringResource(R.string.downloads_active))
            }
            items(state.tasks, key = { "task-${it.id}" }, contentType = { "task" }) { task ->
                TaskRow(task, state.progress[task.id], state.signIn, actions.onCancelTask, actions.onRetryTask)
            }
            if (state.tracks.isNotEmpty()) {
                item(key = "library-header", contentType = "header") {
                    SectionHeader(stringResource(R.string.downloads_library))
                }
            }
        }
        items(state.tracks, key = { "track-${it.id}" }, contentType = { "track" }) { track ->
            TrackRow(
                track,
                guard = guard,
                onClick = { actions.onPlayTrack(track) },
                trailing = {
                    RowMenu(
                        listOf(
                            MenuEntry(R.string.track_add_to_playlist) { actions.onAddToPlaylist(track) },
                            MenuEntry(R.string.track_delete) { actions.onDeleteTrack(track) },
                        ),
                    )
                },
            )
        }
    }
}

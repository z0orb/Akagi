package io.github.z0orb.akagi.feature.browser

import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.data.db.DownloadState
import io.github.z0orb.akagi.data.db.TrackEntity
import io.github.z0orb.akagi.download.DownloadRequest
import io.github.z0orb.akagi.download.DownloadTask
import io.github.z0orb.akagi.ytdlp.DownloadProgress
import io.github.z0orb.akagi.ytdlp.FormatFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatusTest {

    private val track = TrackEntity(
        id = 7, videoId = "dQw4w9WgXcQ", title = "t", durationMs = 1, sizeBytes = 3_400_000, codec = AudioCodec.OPUS,
        bitrateKbps = 135, contentUri = "content://media/1", fileName = "f", thumbnailPath = null, downloadedAt = 0,
    )

    private fun task(state: DownloadState, error: AppError? = null) =
        DownloadTask(3, DownloadRequest("dQw4w9WgXcQ", "t", "251", AudioCodec.OPUS, 135), state, error)

    private val half = DownloadProgress(0.5f, 10, "1MiB/s")

    @Test
    fun `fab follows the queue, then the library`() {
        assertEquals(FabState.Idle, fabStateOf(null, fetching = false))
        assertEquals(FabState.Fetching, fabStateOf(VideoStatus(null, null, null), fetching = true))
        assertEquals(
            FabState.Downloading(0.5f),
            fabStateOf(VideoStatus(null, task(DownloadState.RUNNING), half), false),
        )
        assertEquals(FabState.Downloading(null), fabStateOf(VideoStatus(null, task(DownloadState.QUEUED), null), false))
        assertEquals(FabState.Downloaded, fabStateOf(VideoStatus(track, null, null), false))
        // A failed re-download leaves the old file in place.
        assertEquals(FabState.Downloaded, fabStateOf(VideoStatus(track, task(DownloadState.FAILED), null), false))
    }

    @Test
    fun `sheet shows the active task, then the duplicate prompt, then formats`() {
        val ready = SheetModel("dQw4w9WgXcQ", load = Load.Ready(FormatFixtures.info("standard")))
        val active = sheetContentOf(ready, VideoStatus(track, task(DownloadState.RUNNING), half))
        assertEquals(SheetContent.Active(3, DownloadState.RUNNING, 0.5f, null), active)

        assertEquals(
            SheetContent.InLibrary(AudioCodec.OPUS, 3_400_000, trackId = 7),
            sheetContentOf(ready, VideoStatus(track, null, null)),
        )

        val choose = sheetContentOf(ready.copy(choosing = true), VideoStatus(track, null, null))
        assertTrue(choose is SheetContent.Choose)
        assertEquals("251", (choose as SheetContent.Choose).selected.formatId)
    }

    @Test
    fun `loading and failures show while nothing else applies`() {
        assertEquals(SheetContent.Loading, sheetContentOf(SheetModel("x"), null))
        assertEquals(
            SheetContent.Failed(AppError.Network),
            sheetContentOf(SheetModel("x", load = Load.Failed(AppError.Network)), VideoStatus(null, null, null)),
        )
    }
}

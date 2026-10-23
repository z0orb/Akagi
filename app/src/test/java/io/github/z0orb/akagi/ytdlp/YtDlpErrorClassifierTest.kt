package io.github.z0orb.akagi.ytdlp

import io.github.z0orb.akagi.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpErrorClassifierTest {

    private fun assertClass(expected: AppError, stderr: String) =
        assertEquals(stderr, expected, YtDlpErrorClassifier.classify(stderr))

    @Test
    fun `real yt-dlp messages map to the catalogue`() {
        assertClass(
            AppError.AgeRestricted,
            "ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users.",
        )
        assertClass(
            AppError.MembersOnly,
            "ERROR: [youtube] abc: Join this channel to get access to members-only content like this video",
        )
        assertClass(
            AppError.BotCheck,
            "ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser or --cookies",
        )
        assertClass(AppError.Private, "ERROR: [youtube] abc: Private video. Sign in if you've been granted access")
        assertClass(AppError.Unavailable, "ERROR: [youtube] abc: Video unavailable")
        assertClass(
            AppError.GeoBlocked,
            "ERROR: [youtube] abc: The uploader has not made this video available in your country",
        )
        assertClass(
            AppError.Upcoming("This live event will begin in 3 hours."),
            "ERROR: [youtube] abc-_DEF123: This live event will begin in 3 hours.",
        )
        assertClass(AppError.Upcoming("Premieres in 2 days"), "ERROR: [youtube] abc: Premieres in 2 days")
        assertClass(AppError.Unavailable, "ERROR: [youtube] abc: This live stream recording is not available.")
        assertClass(
            AppError.Network,
            "ERROR: [youtube] abc: Unable to download API page: <urlopen error [Errno 7] No address associated with hostname>",
        )
        assertClass(AppError.Network, "ERROR: Unable to download webpage: The read operation timed out")
        assertClass(AppError.ExtractorOutdated, "ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertClass(
            AppError.ExtractorOutdated,
            "WARNING: [youtube] abc: nsig extraction failed: Some formats may be missing",
        )
        assertClass(AppError.ExtractorOutdated, "ERROR: [youtube] abc: Requested format is not available.")
    }

    @Test
    fun `anything else is unknown with the ERROR line as detail`() {
        val stderr = "WARNING: something minor\nERROR: [generic] Something completely new happened\n"
        assertClass(AppError.Unknown("[generic] Something completely new happened"), stderr)
    }

    @Test
    fun `without an ERROR line the last line is the detail`() {
        assertEquals("Traceback line 2", YtDlpErrorClassifier.firstErrorLine("Traceback line 1\nTraceback line 2\n\n"))
    }
}

package io.github.z0orb.akagi.core.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorTest {

    @Test
    fun `every error survives a round trip through its key`() {
        val all = listOf(
            AppError.AgeRestricted, AppError.MembersOnly, AppError.BotCheck, AppError.ExtractorOutdated,
            AppError.Private, AppError.Unavailable, AppError.GeoBlocked, AppError.Live,
            AppError.Upcoming("Premieres in 2 hours"), AppError.PostLive, AppError.Network, AppError.Storage,
            AppError.Interrupted, AppError.Unknown("boom"),
        )
        all.forEach { error ->
            assertEquals(error, AppError.fromKey(error.key, error.persistedDetail))
        }
    }

    @Test
    fun `rows saved under the old live key read back as Live`() {
        assertEquals(AppError.Live, AppError.fromKey("LiveOrUpcoming", null))
    }

    @Test
    fun `an unknown key reads back as Unknown`() {
        assertEquals(AppError.Unknown("x"), AppError.fromKey("FromAFutureVersion", "x"))
    }

    @Test
    fun `sizes are SI megabytes with one decimal`() {
        assertEquals("3.4 MB", SizeFormatter.megabytes(3_449_447, Locale.US))
        assertEquals("0.0 MB", SizeFormatter.megabytes(0, Locale.US))
        assertEquals("3,4 MB", SizeFormatter.megabytes(3_449_447, Locale.GERMANY))
    }
}

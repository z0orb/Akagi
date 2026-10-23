package io.github.z0orb.akagi.core.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {

    @Test
    fun `report has header, environment, thread and stack trace with cause`() {
        val error = IllegalStateException("boom", IllegalArgumentException("root cause"))
        val report = CrashReportFormatter.format(
            throwable = error,
            threadName = "main",
            timestamp = "2026-10-23T10:00:00+07:00",
            environment = linkedMapOf("App" to "1.0", "Device" to "Xiaomi 23021RAA2Y"),
        )
        val lines = report.lines()
        assertEquals("Akagi crash report", lines[0])
        assertEquals("Time: 2026-10-23T10:00:00+07:00", lines[1])
        assertEquals("App: 1.0", lines[2])
        assertEquals("Device: Xiaomi 23021RAA2Y", lines[3])
        assertEquals("Thread: main", lines[4])
        assertEquals("java.lang.IllegalStateException: boom", lines[6])
        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: root cause"))
    }

    @Test
    fun `oversized reports are truncated`() {
        val deep = generateSequence(RuntimeException("leaf") as Throwable) { RuntimeException("x".repeat(500), it) }
            .take(400)
            .last()
        val report = CrashReportFormatter.format(deep, "worker", "t", emptyMap())
        assertTrue(report.length <= CrashReportFormatter.MAX_LENGTH + 20)
        assertTrue(report.endsWith("… (truncated)"))
    }
}

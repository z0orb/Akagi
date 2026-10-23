package io.github.z0orb.akagi.core.crash

import java.io.PrintWriter
import java.io.StringWriter

/** Plain-text crash report that the user can share from the next launch (PLAN P2.10). */
object CrashReportFormatter {
    // Keeps the share intent's text extra well under the binder transaction limit.
    const val MAX_LENGTH = 60_000

    fun format(throwable: Throwable, threadName: String, timestamp: String, environment: Map<String, String>): String {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("Akagi crash report")
            appendLine("Time: $timestamp")
            environment.forEach { (key, value) -> appendLine("$key: $value") }
            appendLine("Thread: $threadName")
            appendLine()
            append(stackTrace.trimEnd())
        }
        return if (report.length <= MAX_LENGTH) report else report.take(MAX_LENGTH) + "\n… (truncated)"
    }
}

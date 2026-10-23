package io.github.z0orb.akagi.core.crash

/** Holds the report of the most recent crash until the user has shared or dismissed it. */
interface CrashLogStore {
    fun write(report: String)

    fun read(): String?

    fun clear()
}

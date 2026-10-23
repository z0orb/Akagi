package io.github.z0orb.akagi.core.common

import javax.inject.Inject

/** Wall-clock time, behind an interface so tests control it. */
fun interface Clock {
    fun nowMillis(): Long
}

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

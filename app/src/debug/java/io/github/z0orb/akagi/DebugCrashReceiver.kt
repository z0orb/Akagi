package io.github.z0orb.akagi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only: throws on the main thread so the crash logger can be tested over adb.
 * `adb shell am broadcast -a io.github.z0orb.akagi.DEBUG_CRASH -p io.github.z0orb.akagi.debug`
 */
class DebugCrashReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent): Unit =
        throw IllegalStateException("Test crash requested over adb")
}

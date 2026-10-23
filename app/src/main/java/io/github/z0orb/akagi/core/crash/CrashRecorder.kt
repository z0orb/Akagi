package io.github.z0orb.akagi.core.crash

import android.os.Build
import io.github.z0orb.akagi.BuildConfig
import java.time.OffsetDateTime
import javax.inject.Inject

/** Saves uncaught exceptions so they can be shared without adb, then lets the default handler kill the app. */
class CrashRecorder @Inject constructor(private val store: CrashLogStore) {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Failing to save must never mask the original crash.
            runCatching {
                store.write(
                    CrashReportFormatter.format(throwable, thread.name, OffsetDateTime.now().toString(), environment()),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun environment(): Map<String, String> = linkedMapOf(
        "App" to "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "ABI" to Build.SUPPORTED_ABIS.joinToString(),
    )
}

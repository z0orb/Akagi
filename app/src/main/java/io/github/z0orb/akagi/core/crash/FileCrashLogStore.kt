package io.github.z0orb.akagi.core.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class FileCrashLogStore @Inject constructor(@ApplicationContext context: Context) : CrashLogStore {
    private val file = File(context.filesDir, "crash/last-crash.txt")

    // Called from the dying process, so it stays synchronous and minimal.
    override fun write(report: String) {
        file.parentFile?.mkdirs()
        file.writeText(report)
    }

    override fun read(): String? = file.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    override fun clear() {
        file.delete()
    }
}

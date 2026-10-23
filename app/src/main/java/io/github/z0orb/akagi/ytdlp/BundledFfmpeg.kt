package io.github.z0orb.akagi.ytdlp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Runs the ffmpeg that youtubedl-android ships and extracts (D-034). Only valid once the engine has
 * initialised, which is always true after a download.
 */
class BundledFfmpeg @Inject constructor(@ApplicationContext context: Context) {
    private val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    // youtubedl-android unpacks its shared libraries here (see its YoutubeDL/FFmpeg init).
    private val libraryPath = listOf("python", "ffmpeg").joinToString(":") { name ->
        File(context.noBackupFilesDir, "youtubedl-android/packages/$name/usr/lib").absolutePath
    }

    fun start(args: List<String>): Process = ProcessBuilder(listOf(binary.absolutePath) + args)
        // Unread stderr could fill its pipe and stall ffmpeg; callers rely on the exit code instead.
        .redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
        .apply { environment()["LD_LIBRARY_PATH"] = libraryPath }
        .start()
}

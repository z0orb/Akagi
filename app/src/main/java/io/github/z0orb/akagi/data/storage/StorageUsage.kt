package io.github.z0orb.akagi.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.DispatcherProvider
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Akagi's own storage, split the way Settings explains it (P7.5). The music itself is in `Music/Akagi`. */
data class AppStorage(val runtimeBytes: Long, val cacheBytes: Long, val thumbnailBytes: Long)

class StorageUsage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    /** Walks the app's folders, so it runs off the main thread and only when Settings opens. */
    suspend fun measure(): AppStorage = withContext(dispatchers.io) {
        AppStorage(
            // youtubedl-android unpacks Python, yt-dlp and FFmpeg here on first start.
            runtimeBytes = context.noBackupFilesDir.sizeOnDisk(),
            cacheBytes = context.cacheDir.sizeOnDisk(),
            thumbnailBytes = File(context.filesDir, "thumbs").sizeOnDisk(),
        )
    }

    // Python's lib folder is full of symlinks (libz.so -> libz.so.1 ...); following them counted files twice.
    private fun File.sizeOnDisk(): Long = walkTopDown()
        .onEnter { !Files.isSymbolicLink(it.toPath()) }
        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
        .sumOf { it.length() }
}

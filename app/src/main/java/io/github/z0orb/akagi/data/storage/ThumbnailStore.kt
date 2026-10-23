package io.github.z0orb.akagi.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.DispatcherProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Saves a 640×360 JPEG per video in private storage, so rows show art offline (PLAN §7.6). */
class ThumbnailStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val directory = File(context.filesDir, "thumbs")

    /** The saved file's path, or null when no source worked: a missing thumbnail never fails a download. */
    suspend fun fetch(videoId: String): String? = withContext(dispatchers.io) {
        val bitmap = SOURCES.firstNotNullOfOrNull { name ->
            runCatching { download("https://i.ytimg.com/vi/$videoId/$name.jpg") }.getOrNull()
        } ?: return@withContext null
        val file = File(directory.apply { mkdirs() }, "$videoId.jpg")
        runCatching {
            file.outputStream().use { cropTo16By9(bitmap).compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            file.absolutePath
        }.getOrNull()
    }

    suspend fun delete(path: String) {
        withContext(dispatchers.io) { File(path).delete() }
    }

    private fun download(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            // maxresdefault answers 404 (with a placeholder image) when the video has none.
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }

    private fun cropTo16By9(source: Bitmap): Bitmap {
        val cropHeight = minOf(source.height, source.width * HEIGHT / WIDTH)
        val cropWidth = cropHeight * WIDTH / HEIGHT
        val left = (source.width - cropWidth) / 2
        val top = (source.height - cropHeight) / 2
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            source,
            Rect(left, top, left + cropWidth, top + cropHeight),
            Rect(0, 0, WIDTH, HEIGHT),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }

    private companion object {
        val SOURCES = listOf("maxresdefault", "hq720", "mqdefault")
        const val WIDTH = 640
        const val HEIGHT = 360
        const val QUALITY = 85
        const val TIMEOUT_MS = 15_000
    }
}

package io.github.z0orb.akagi.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.common.DispatcherProvider
import io.github.z0orb.akagi.core.di.ApplicationScope
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/** youtubedl-android 0.18 (bundled Python, ffmpeg and QuickJS) behind [YtDlpEngine]. */
@Singleton
class YoutubeDlAndroidEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope scope: CoroutineScope,
) : YtDlpEngine {
    private val ytdlp = YoutubeDL.getInstance()
    private val cacheDir = File(context.cacheDir, "yt-dlp").absolutePath
    private val pluginDir = File(context.filesDir, "ytdlp-plugins")
    private val infoDir = File(context.cacheDir, "yt-dlp-info")
    private val lock = ReadWriteMutex()
    private val infoIds = AtomicLong()

    // Extracting Python takes seconds on first launch, so it starts with the app, off the main thread (PLAN §7.5).
    private val ready: Deferred<Unit> = scope.async(dispatchers.io) {
        ytdlp.init(context)
        FFmpeg.getInstance().init(context)
        installPlugins()
        prunePlayerCache()
        infoDir.listFiles()?.filter {
            System.currentTimeMillis() - it.lastModified() >= INFO_MAX_AGE_MS
        }?.forEach(File::delete)
        Log.i(TAG, "yt-dlp ${ytdlp.version(context)} ready")
    }

    // Copied on every start so an app update also updates the plugin; it is a few hundred bytes.
    private fun installPlugins() {
        val assets = context.assets
        fun copy(path: String) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                val target = File(pluginDir, path.removePrefix("$PLUGIN_ASSETS/")).apply { parentFile?.mkdirs() }
                assets.open(path).use { input -> target.outputStream().use(input::copyTo) }
            } else {
                children.forEach { copy("$path/$it") }
            }
        }
        copy(PLUGIN_ASSETS)
    }

    /** Keeps only the newest preprocessed player (~4 MB each); YouTube ships a new player every few days. */
    private fun prunePlayerCache() {
        File(cacheDir, "challenge-solver").listFiles { file -> file.name.startsWith("player") }
            ?.sortedByDescending(File::lastModified)
            ?.drop(1)
            ?.forEach(File::delete)
    }

    override suspend fun fetchInfo(videoId: String, cookieFile: File?): VideoInfo = lock.read {
        val request = request(videoId, cookieFile).apply { addCommands(YtDlpOptions.info()) }
        val response = run("info-${infoIds.incrementAndGet()}", request, callback = null)
        val info = try {
            AudioFormatMapper.parse(response.out)
        } catch (e: org.json.JSONException) {
            throw AppException(AppError.Unknown("Unreadable video info"), e.message, e)
        }
        // Kept for the download, which can then skip extracting the page and the JS challenge again. Only a
        // shortcut, so a full disk costs the download a second extraction rather than failing the fetch.
        withContext(dispatchers.io) {
            try {
                infoFile(videoId).apply { parentFile?.mkdirs() }.writeText(response.out)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't keep the info for $videoId", e)
            }
        }
        info
    }

    override suspend fun download(
        processId: String,
        videoId: String,
        formatId: String,
        codec: AudioCodec,
        bitrateKbps: Int,
        outputDir: File,
        cookieFile: File?,
        onProgress: (DownloadProgress) -> Unit,
    ): File = lock.read {
        val args = YtDlpOptions.download(formatId, codec, bitrateKbps, File(outputDir, "%(id)s.%(ext)s").absolutePath)
        val callback = { percent: Float, eta: Long, line: String ->
            onProgress(
                DownloadProgress(
                    (percent / 100f).coerceIn(0f, 1f),
                    eta.takeIf {
                        it >= 0
                    },
                    YtDlpOptions.speedOf(line),
                ),
            )
        }
        val savedInfo = freshInfoFile(videoId)
        val reused = savedInfo != null && try {
            val request = YoutubeDLRequest(emptyList<String>()).apply {
                addCommands(YtDlpOptions.base(cacheDir, pluginDir.absolutePath))
                cookieFile?.let { addCommands(YtDlpOptions.cookies(it.absolutePath)) }
                addCommands(YtDlpOptions.loadInfo(savedInfo.absolutePath) + args)
            }
            run(processId, request, callback)
            true
        } catch (e: AppException) {
            // Most likely expired format URLs: forget the saved info and extract afresh.
            Log.w(TAG, "Saved info for $videoId failed (${e.error}); extracting again")
            savedInfo.delete()
            outputDir.listFiles()?.forEach(File::delete)
            false
        }
        if (!reused) run(processId, request(videoId, cookieFile).apply { addCommands(args) }, callback)
        outputDir.listFiles()?.firstOrNull { it.extension.equals(codec.extension, ignoreCase = true) }
            ?: throw AppException(AppError.Unknown("yt-dlp finished without a .${codec.extension} file"))
    }

    private fun infoFile(videoId: String) = File(infoDir, "$videoId.json")

    private fun freshInfoFile(videoId: String): File? =
        infoFile(videoId).takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < INFO_MAX_AGE_MS }

    override suspend fun version(): String? {
        awaitReady()
        // The library only records a version after an update, so ask the bundled binary the first time.
        return withContext(dispatchers.io) { ytdlp.version(context) } ?: bundledVersion.await()
    }

    private val bundledVersion: Deferred<String?> = scope.async(start = CoroutineStart.LAZY) {
        runCatching {
            run("version", YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }, callback = null)
                .out.trim().ifEmpty { null }
        }.getOrNull()
    }

    override suspend fun update(channel: UpdateChannel): UpdateResult = lock.write {
        awaitReady()
        val libraryChannel = when (channel) {
            UpdateChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
            UpdateChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
        }
        val status = withContext(dispatchers.io) {
            try {
                ytdlp.updateYoutubeDL(context, libraryChannel)
            } catch (e: YoutubeDLException) {
                throw AppException(YtDlpErrorClassifier.classify(e.message.orEmpty()), e.message, e)
            }
        }
        if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
            UpdateResult.AlreadyUpToDate
        } else {
            UpdateResult.Updated(ytdlp.version(context))
        }
    }

    private fun request(videoId: String, cookieFile: File?) = YoutubeDLRequest(YtDlpOptions.watchUrl(videoId)).apply {
        addCommands(YtDlpOptions.base(cacheDir, pluginDir.absolutePath))
        cookieFile?.let { addCommands(YtDlpOptions.cookies(it.absolutePath)) }
    }

    private suspend fun run(
        processId: String,
        request: YoutubeDLRequest,
        callback: ((Float, Long, String) -> Unit)?,
    ): YoutubeDLResponse {
        awaitReady()
        // supervisorScope: a failing call must surface as the classified AppException below, not cancel the scope.
        return supervisorScope {
            val work = async(dispatchers.io) { ytdlp.execute(request, processId, callback) }
            try {
                work.await().also { Log.i(TAG, "yt-dlp $processId took ${it.elapsedTime} ms") }
            } catch (e: CancellationException) {
                // Interrupting the thread would only kill Python; this kills the whole tree, ffmpeg included.
                ytdlp.destroyProcessById(processId)
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                throw CancellationException("yt-dlp process $processId was destroyed").apply { initCause(e) }
            } catch (e: YoutubeDLException) {
                val output = e.message.orEmpty()
                Log.w(TAG, "yt-dlp failed: ${YtDlpErrorClassifier.firstErrorLine(output)}")
                throw AppException(
                    YtDlpErrorClassifier.classify(output),
                    YtDlpErrorClassifier.firstErrorLine(output),
                    e,
                )
            }
        }
    }

    private suspend fun awaitReady() {
        try {
            ready.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not only YoutubeDLException: extracting Python or the plugins can fail with any I/O error.
            throw AppException(AppError.Unknown("yt-dlp failed to start: ${e.message}"), e.message, e)
        }
    }

    private companion object {
        const val TAG = "YtDlpEngine"
        const val PLUGIN_ASSETS = "ytdlp-plugins"

        // Format URLs in the info stay valid for hours; this matches the format sheet's info cache.
        const val INFO_MAX_AGE_MS = 30 * 60 * 1000L
    }
}

package io.github.z0orb.akagi.download

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import io.github.z0orb.akagi.ytdlp.BundledFfmpeg
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class AudioAnalysis(val durationMs: Long, val peaks: ByteArray?)

/**
 * Decodes a downloaded file once to build its waveform (PLAN §7.7). One file at a time, so three
 * parallel downloads don't spike the CPU. The bundled ffmpeg goes first: MediaCodec pays an IPC round
 * trip per ~20 ms buffer and took 16 s for a 4-minute song on the test phone, ffmpeg 1.6 s (D-034).
 */
@Singleton
class WaveformGenerator @Inject constructor(private val ffmpeg: BundledFfmpeg) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.Default.limitedParallelism(1)

    suspend fun analyse(file: File): AudioAnalysis = withContext(decodeDispatcher) {
        val started = System.nanoTime()
        // A broken waveform must never fail the download: in the end, fall back to the duration alone.
        val decoded = attempt("ffmpeg", file, ::decodeWithFfmpeg) ?: attempt("MediaCodec", file, ::decodeWithMediaCodec)
        decoded?.also { Log.i(TAG, "Waveform of ${file.name} in ${(System.nanoTime() - started) / 1_000_000} ms") }
            ?: AudioAnalysis(durationOf(file), null)
    }

    private suspend fun attempt(name: String, file: File, decode: suspend (File) -> AudioAnalysis): AudioAnalysis? =
        try {
            decode(file).takeIf { it.peaks != null }
        } catch (e: IOException) {
            Log.w(TAG, "$name couldn't decode ${file.name}", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "$name couldn't decode ${file.name}", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "$name couldn't decode ${file.name}", e)
            null
        }

    private suspend fun decodeWithFfmpeg(file: File): AudioAnalysis {
        val durationMs = durationOf(file).takeIf { it > 0 } ?: throw IOException("Unknown duration")
        val buckets = WaveformBuckets(durationMs * 1000)
        // Mono at 8 kHz is plenty for loudness and keeps the pipe small (~1 MB per minute).
        val args = listOf("-nostdin", "-v", "error", "-i", file.absolutePath)
        val process = ffmpeg.start(args + listOf("-ac", "1", "-ar", "$PCM_RATE", "-f", "s16le", "-"))
        try {
            val chunk = ByteArray(CHUNK_BYTES)
            var samples = 0L
            process.inputStream.use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.readFully(chunk)
                    if (read < 2) break
                    val count = read / 2
                    val pcm = ByteBuffer.wrap(chunk, 0, count * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    var sum = 0.0
                    while (pcm.hasRemaining()) (pcm.get() / SHORT_SCALE).let { sum += it * it }
                    buckets.add(samples * 1_000_000 / PCM_RATE, sum, count)
                    samples += count
                }
            }
            if (process.waitFor() != 0 || samples == 0L) throw IOException("ffmpeg exited with ${process.exitValue()}")
            return AudioAnalysis(durationMs, buckets.toPeaks())
        } finally {
            process.destroy()
        }
    }

    private suspend fun decodeWithMediaCodec(file: File): AudioAnalysis {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).first { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val buckets = WaveformBuckets(durationUs)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME).orEmpty())
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                pump(extractor, codec, buckets)
            } finally {
                codec.release()
            }
            return AudioAnalysis(durationUs / 1000, buckets.toPeaks())
        } finally {
            extractor.release()
        }
    }

    private suspend fun pump(extractor: MediaExtractor, codec: MediaCodec, buckets: WaveformBuckets) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var floatPcm = false
        while (true) {
            currentCoroutineContext().ensureActive()
            // Fill every free input buffer without waiting; only the output side blocks, while the codec works.
            while (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(0)
                if (inIndex < 0) break
                val buffer = codec.getInputBuffer(inIndex) ?: break
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) floatPcm = isFloat(codec.outputFormat)
            if (outIndex < 0) continue
            codec.getOutputBuffer(outIndex)?.let { buffer ->
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                buffer.order(ByteOrder.nativeOrder())
                // A buffer is ~20 ms and a bucket ~1 s, so the whole buffer counts at its start time.
                buckets.add(info.presentationTimeUs, sumOfSquares(buffer, floatPcm), info.size / if (floatPcm) 4 else 2)
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    private fun sumOfSquares(buffer: ByteBuffer, floatPcm: Boolean): Double {
        var sum = 0.0
        if (floatPcm) {
            val samples = buffer.asFloatBuffer()
            while (samples.hasRemaining()) samples.get().let { sum += it * it }
        } else {
            val samples = buffer.asShortBuffer()
            while (samples.hasRemaining()) (samples.get() / SHORT_SCALE).let { sum += it * it }
        }
        return sum
    }

    private fun isFloat(format: MediaFormat): Boolean = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
        format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT

    // Fills [buffer] unless the stream ends first; returns how many bytes were read.
    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun durationOf(file: File): Long = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                release()
            }
        }
    }.getOrNull() ?: 0L

    private companion object {
        const val TAG = "WaveformGenerator"
        const val TIMEOUT_US = 10_000L
        const val SHORT_SCALE = 32_768f
        const val PCM_RATE = 8_000

        // 0.1 s of PCM: fine enough for 256 buckets even on short clips.
        const val CHUNK_BYTES = PCM_RATE / 10 * 2
    }
}

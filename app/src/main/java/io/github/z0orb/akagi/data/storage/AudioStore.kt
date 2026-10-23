package io.github.z0orb.akagi.data.storage

import io.github.z0orb.akagi.core.common.AudioCodec
import java.io.File

/** The public `Music/Akagi` folder (PLAN §7.6), behind an interface so the pipeline can be tested. */
interface AudioStore {
    /**
     * Copies [source] into the music folder. When [replacing] is the content URI of the previous file
     * for the same video, that file is overwritten or removed, so a re-download never leaves two copies.
     */
    suspend fun save(source: File, fileName: String, title: String, codec: AudioCodec, replacing: String?): SavedAudio

    suspend fun delete(contentUri: String)
}

data class SavedAudio(val contentUri: String, val sizeBytes: Long)

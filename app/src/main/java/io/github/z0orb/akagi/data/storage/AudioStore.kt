package io.github.z0orb.akagi.data.storage

import android.content.IntentSender
import io.github.z0orb.akagi.core.common.AudioCodec
import java.io.File

/** The public `Music/Akagi` folder (PLAN §7.6), behind an interface so the pipeline can be tested. */
interface AudioStore {
    /**
     * Copies [source] into the music folder. When [replacing] is the content URI of the previous file
     * for the same video, that file is overwritten or removed, so a re-download never leaves two copies.
     */
    suspend fun save(source: File, fileName: String, title: String, codec: AudioCodec, replacing: String?): SavedAudio

    /** Deletes the file; a file that is already gone counts as deleted. */
    suspend fun delete(contentUri: String): AudioDeletion

    /** MediaStore IDs of the audio files Akagi can still see, or null when MediaStore can't be queried. */
    suspend fun presentMediaIds(): Set<Long>?
}

data class SavedAudio(val contentUri: String, val sizeBytes: Long)

sealed interface AudioDeletion {
    data object Deleted : AudioDeletion

    /** The file isn't Akagi's (e.g. after a reinstall); the system must ask the user, via [request]. */
    data class NeedsConsent(val request: IntentSender) : AudioDeletion
}

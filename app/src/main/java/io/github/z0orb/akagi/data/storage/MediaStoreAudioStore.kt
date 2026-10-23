package io.github.z0orb.akagi.data.storage

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.AppError
import io.github.z0orb.akagi.core.common.AppException
import io.github.z0orb.akagi.core.common.AudioCodec
import io.github.z0orb.akagi.core.common.DispatcherProvider
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Writes through MediaStore, so other players see the files and no storage permission is needed (D-005). */
class MediaStoreAudioStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) : AudioStore {
    private val resolver: ContentResolver = context.contentResolver
    private val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override suspend fun save(
        source: File,
        fileName: String,
        title: String,
        codec: AudioCodec,
        replacing: String?,
    ): SavedAudio = withContext(dispatchers.io) {
        val previous = replacing?.let(Uri::parse)
        // Same name: overwrite in place. Inserting a new row instead would make MediaStore add " (1)".
        val overwritten = previous?.takeIf { displayNameOf(it) == fileName }?.let { overwrite(it, source) }
        val uri = overwritten ?: insert(source, fileName, title, codec).also { previous?.let(::deleteQuietly) }
        SavedAudio(uri.toString(), source.length())
    }

    override suspend fun delete(contentUri: String): AudioDeletion = withContext(dispatchers.io) {
        val uri = Uri.parse(contentUri)
        try {
            resolver.delete(uri, null, null)
            AudioDeletion.Deleted
        } catch (e: SecurityException) {
            // Files Akagi didn't create (a reinstall drops ownership) need the user's OK through a system dialog.
            // HyperOS also throws here for a row that no longer exists, and a delete request for it would fail.
            when {
                !exists(uri) -> AudioDeletion.Deleted

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    AudioDeletion.NeedsConsent(MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender)

                e is RecoverableSecurityException -> AudioDeletion.NeedsConsent(e.userAction.actionIntent.intentSender)

                else -> throw AppException(AppError.Storage, e.message, e)
            }
        }
    }

    override suspend fun presentMediaIds(): Set<Long>? = withContext(dispatchers.io) {
        runCatching {
            resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
        }.getOrNull()
    }

    private fun insert(source: File, fileName: String, title: String, codec: AudioCodec): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeOf(codec))
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri =
            resolver.insert(collection, values) ?: throw AppException(AppError.Storage, "MediaStore insert failed")
        try {
            copy(source, uri, mode = "w")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            deleteQuietly(uri)
            throw AppException(AppError.Storage, e.message, e)
        }
        return uri
    }

    /** Null when the old file is gone or no longer ours (e.g. after a reinstall); the caller then inserts. */
    private fun overwrite(uri: Uri, source: File): Uri? = try {
        copy(source, uri, mode = "wt")
        uri
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun copy(source: File, uri: Uri, mode: String) {
        val output = resolver.openOutputStream(uri, mode) ?: throw IOException("Can't open $uri")
        output.use { out -> source.inputStream().use { it.copyTo(out) } }
    }

    // On a failed query, assume the file is there: asking the user beats silently dropping a real file.
    private fun exists(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { it.count > 0 }
    }.getOrNull() ?: true

    private fun displayNameOf(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    // A failed cleanup must not fail the download; the worst case is a leftover file the user can delete.
    private fun deleteQuietly(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    // MediaStore renames files whose extension doesn't match the MIME type, so ask the platform's own table.
    private fun mimeTypeOf(codec: AudioCodec): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(codec.extension) ?: "audio/*"

    private companion object {
        const val RELATIVE_PATH = "Music/Akagi"
    }
}

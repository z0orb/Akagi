package io.github.z0orb.akagi.data.library

/** A track's file as reconciliation sees it. */
data class TrackFile(val trackId: Long, val contentUri: String, val missing: Boolean)

/** Works out which tracks lost (or got back) their file since the last check (PLAN P4.7). */
object MissingFiles {
    /** The MediaStore ID at the end of a content URI, or null when the URI doesn't end in one. */
    fun mediaIdOf(file: TrackFile): Long? = file.contentUri.substringAfterLast('/').toLongOrNull()

    /**
     * Track ID → new `missing` flag, for tracks whose flag is wrong. [presentMediaIds] are the MediaStore
     * IDs still visible; a URI that doesn't end in an ID is left alone rather than guessed at.
     */
    fun changes(files: List<TrackFile>, presentMediaIds: Set<Long>): Map<Long, Boolean> = buildMap {
        files.forEach { file ->
            val mediaId = mediaIdOf(file) ?: return@forEach
            val missing = mediaId !in presentMediaIds
            if (missing != file.missing) put(file.trackId, missing)
        }
    }
}

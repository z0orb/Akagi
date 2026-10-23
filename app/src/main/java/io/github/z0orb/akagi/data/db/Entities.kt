package io.github.z0orb.akagi.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.z0orb.akagi.core.common.AudioCodec

/** One row per video (D-009): downloading it again replaces the file but keeps the row and its playlists. */
@Entity(tableName = "tracks", indices = [Index(value = ["videoId"], unique = true), Index("downloadedAt")])
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    val contentUri: String,
    val fileName: String,
    val thumbnailPath: String?,
    val downloadedAt: Long,
    @ColumnInfo(defaultValue = "0") val missing: Boolean = false,
)

/** Kept apart from [TrackEntity] so list queries never load the BLOB (PLAN §6). */
@Entity(
    tableName = "track_waveforms",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackWaveformEntity(
    @PrimaryKey val trackId: Long,
    /** 256 unsigned peak bytes (D-007). */
    val peaks: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is TrackWaveformEntity && other.trackId == trackId && other.peaks.contentEquals(peaks)

    override fun hashCode(): Int = 31 * trackId.hashCode() + peaks.contentHashCode()
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A track is in a playlist at most once; deleting either side removes the membership (D-008). */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId", "position"]), Index("trackId")],
)
data class PlaylistTrackEntity(val playlistId: Long, val trackId: Long, val position: Int)

enum class DownloadState { QUEUED, RUNNING, POST_PROCESSING, FAILED }

/** A queued, running or failed download. Rows are deleted when the download completes or is cancelled. */
@Entity(tableName = "download_tasks", indices = [Index("videoId")])
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val formatId: String,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    val state: DownloadState,
    @ColumnInfo(defaultValue = "0") val progress: Float = 0f,
    val errorType: String? = null,
    val errorMessage: String? = null,
    /** Set when this download replaces an existing track's file (the "Download again" flow). */
    val replaceTrackId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

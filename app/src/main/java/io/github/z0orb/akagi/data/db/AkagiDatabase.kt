package io.github.z0orb.akagi.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        TrackWaveformEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 2,
    // v1 → v2 only adds nullable columns, so no spec is needed (D-071, D-075). Never a destructive fallback.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class AkagiDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun playlistDao(): PlaylistDao
}

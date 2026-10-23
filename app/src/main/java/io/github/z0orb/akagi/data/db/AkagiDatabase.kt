package io.github.z0orb.akagi.data.db

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
    version = 1,
    exportSchema = true,
)
abstract class AkagiDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun downloadTaskDao(): DownloadTaskDao
}

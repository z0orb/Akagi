package io.github.z0orb.akagi.core.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.core.common.SystemClock
import io.github.z0orb.akagi.data.db.AkagiDatabase
import io.github.z0orb.akagi.data.db.DownloadTaskDao
import io.github.z0orb.akagi.data.db.TrackDao
import io.github.z0orb.akagi.data.storage.AudioStore
import io.github.z0orb.akagi.data.storage.MediaStoreAudioStore
import io.github.z0orb.akagi.download.DownloadPipeline
import io.github.z0orb.akagi.download.DownloadQueue
import io.github.z0orb.akagi.download.RoomDownloadTaskStore
import io.github.z0orb.akagi.ytdlp.VideoInfoCache
import io.github.z0orb.akagi.ytdlp.YoutubeDlAndroidEngine
import io.github.z0orb.akagi.ytdlp.YtDlpEngine
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    abstract fun bindAudioStore(impl: MediaStoreAudioStore): AudioStore

    @Binds
    abstract fun bindYtDlpEngine(impl: YoutubeDlAndroidEngine): YtDlpEngine

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AkagiDatabase =
            Room.databaseBuilder(context, AkagiDatabase::class.java, "akagi.db").build()

        @Provides
        fun provideTrackDao(database: AkagiDatabase): TrackDao = database.trackDao()

        @Provides
        fun provideDownloadTaskDao(database: AkagiDatabase): DownloadTaskDao = database.downloadTaskDao()

        @Provides
        @Singleton
        fun provideDownloadQueue(
            store: RoomDownloadTaskStore,
            pipeline: DownloadPipeline,
            @ApplicationScope scope: CoroutineScope,
            clock: Clock,
        ): DownloadQueue = DownloadQueue(store, pipeline, scope, clock)

        @Provides
        @Singleton
        fun provideVideoInfoCache(engine: YtDlpEngine, clock: Clock): VideoInfoCache =
            VideoInfoCache(engine::fetchInfo, clock)
    }
}

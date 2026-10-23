package io.github.z0orb.akagi.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.z0orb.akagi.core.common.DefaultDispatcherProvider
import io.github.z0orb.akagi.core.common.DispatcherProvider
import io.github.z0orb.akagi.core.crash.CrashLogStore
import io.github.z0orb.akagi.core.crash.FileCrashLogStore

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    abstract fun bindCrashLogStore(impl: FileCrashLogStore): CrashLogStore
}

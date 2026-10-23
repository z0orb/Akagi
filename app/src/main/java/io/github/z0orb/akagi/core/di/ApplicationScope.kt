package io.github.z0orb.akagi.core.di

import javax.inject.Qualifier

/** A scope that lives as long as the process: yt-dlp init, the download queue. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

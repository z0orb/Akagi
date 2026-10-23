package io.github.z0orb.akagi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.z0orb.akagi.core.crash.CrashRecorder
import io.github.z0orb.akagi.download.DownloadCoordinator
import javax.inject.Inject

@HiltAndroidApp
class AkagiApp : Application() {

    @Inject
    lateinit var crashRecorder: CrashRecorder

    // Creating it also starts yt-dlp's background init (the engine is one of its dependencies).
    @Inject
    lateinit var downloadCoordinator: DownloadCoordinator

    override fun onCreate() {
        super.onCreate()
        crashRecorder.install()
        downloadCoordinator.start()
    }
}

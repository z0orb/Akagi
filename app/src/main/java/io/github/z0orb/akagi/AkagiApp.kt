package io.github.z0orb.akagi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.z0orb.akagi.core.crash.CrashRecorder
import javax.inject.Inject

@HiltAndroidApp
class AkagiApp : Application() {

    @Inject
    lateinit var crashRecorder: CrashRecorder

    override fun onCreate() {
        super.onCreate()
        crashRecorder.install()
    }
}

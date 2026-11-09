package io.vroomi.app

import android.app.Application
import io.vroomi.app.runtime.VroomiRuntime

class VroomiApplication : Application() {
    lateinit var runtime: VroomiRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = VroomiRuntime(this)
    }
}

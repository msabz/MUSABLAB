package com.musablab.agent

import android.app.Application
import com.musablab.agent.shizuku.ShizukuController

class MusabLabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuController.initialize(this)
    }
}

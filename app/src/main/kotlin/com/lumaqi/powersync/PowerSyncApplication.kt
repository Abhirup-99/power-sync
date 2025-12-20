package com.lumaqi.powersync

import android.app.Application
import com.google.firebase.FirebaseApp

class PowerSyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize debug logger
        DebugLogger.initialize(this)
        DebugLogger.i("PowerSyncApplication", "Application started")
    }
}

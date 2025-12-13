package com.even.chord

import android.app.Application
import com.google.firebase.FirebaseApp

class ChordApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize debug logger
        DebugLogger.initialize(this)
        DebugLogger.i("ChordApplication", "Application started")
    }
}

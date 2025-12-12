package com.even.chord

/**
 * Configuration constants for the WorkManager sync.
 * Matches Flutter's configuration for seamless data sharing.
 */
object NativeSyncConfig {
    // API Configuration
    const val API_BASE_URL = "https://api.even.in"
    const val API_UPLOAD_ENDPOINT = "/app-internal/upload-recording"
    
    // SharedPreferences
    const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
    const val KEY_SYNC_ACTIVE = "flutter.sync_active"
    const val KEY_SYNC_FOLDER_PATH = "flutter.sync_folder_path"
    const val KEY_LAST_SYNC_TIME = "flutter.last_sync_time"
    
    // Database
    const val DATABASE_NAME = "chord_sync.db"
    const val DATABASE_VERSION = 1
    
    // Notification
    const val NOTIFICATION_CHANNEL_ID = "evensync_channel"
    const val NOTIFICATION_CHANNEL_NAME = "EvenSync Background Service"
    const val NOTIFICATION_ID = 256
    
    // Sync Configuration
    const val SYNC_INTERVAL_MINUTES = 5L
    const val UPLOAD_TIMEOUT_SECONDS = 60L
    
    // Firebase Storage
    const val STORAGE_BASE_PATH = "chord"
    const val STORAGE_RECORDINGS_FOLDER = "Recordings"
}

package com.lumaqi.powersync

/** Configuration constants for the app. Updated for pure Kotlin Android (no Flutter prefixes). */
object NativeSyncConfig {
    // SharedPreferences
    const val PREFS_NAME = "powersync_prefs"
    const val KEY_SYNC_ACTIVE = "sync_active"
    const val KEY_SYNC_FOLDER_PATH = "sync_folder_path"
    const val KEY_LAST_SYNC_TIME = "last_sync_time"

    // Legacy Flutter prefs (for migration)
    const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"

    // Database
    const val DATABASE_NAME = "powersync_sync.db"
    const val DATABASE_VERSION = 3

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "powersync_channel"
    const val NOTIFICATION_CHANNEL_NAME = "PowerSync Background Service"
    const val NOTIFICATION_ID = 256

    // Sync Configuration
    const val SYNC_INTERVAL_MINUTES = 15L // Android minimum for WorkManager
    const val UPLOAD_TIMEOUT_SECONDS = 60L

    // Google Drive Configuration
    const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
    const val KEY_DRIVE_FOLDER_NAME = "drive_folder_name"

    // Firebase Storage
    const val STORAGE_BASE_PATH = "powersync"
    const val STORAGE_RECORDINGS_FOLDER = "Recordings"
}

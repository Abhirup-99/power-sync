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

    // Synchronization Settings
    const val KEY_PARALLEL_UPLOADS = "parallel_uploads"
    const val KEY_PARALLEL_DOWNLOADS = "parallel_downloads"
    const val KEY_AUTOSYNC_SCHEDULE = "autosync_schedule"
    const val KEY_ONLY_WHILE_CHARGING = "only_while_charging"
    const val KEY_BATTERY_LEVEL_THRESHOLD = "battery_level_threshold"
    const val KEY_INTERNET_CONNECTION_TYPE = "internet_connection_type"
    const val KEY_ALLOWED_ON_METERED_WIFI = "allowed_on_metered_wifi"
    const val KEY_ALLOWED_WIFI_NETWORKS = "allowed_wifi_networks"
    const val KEY_DISALLOWED_WIFI_NETWORKS = "disallowed_wifi_networks"
    const val KEY_ALLOWED_ON_MOBILE_ROAMING = "allowed_on_mobile_roaming"
    const val KEY_ALLOWED_ON_SLOW_2G = "allowed_on_slow_2g"
    const val KEY_WIFI_UPLOAD_LIMIT = "wifi_upload_limit"
    const val KEY_WIFI_DOWNLOAD_LIMIT = "wifi_download_limit"
    const val KEY_MOBILE_UPLOAD_LIMIT = "mobile_upload_limit"
    const val KEY_MOBILE_DOWNLOAD_LIMIT = "mobile_download_limit"
    const val KEY_WARN_ON_MOBILE = "warn_on_mobile"
    const val KEY_RETRY_AUTOMATICALLY = "retry_automatically"
    const val KEY_RETRY_WAIT_TIME = "retry_wait_time"
    const val KEY_MAX_RETRY_ATTEMPTS = "max_retry_attempts"

    // Firebase Storage
    const val STORAGE_BASE_PATH = "powersync"
    const val STORAGE_RECORDINGS_FOLDER = "Recordings"
}

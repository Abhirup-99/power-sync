package com.lumaqi.powersync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lumaqi.powersync.services.SyncEngine
import com.lumaqi.powersync.services.SyncStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager-based sync worker that handles file sync to Firebase Storage and API. This is the
 * ONLY sync mechanism - runs periodically via WorkManager (minimum 15 minutes).
 */
class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
        CoroutineWorker(appContext, workerParams) {

    private val syncEngine = SyncEngine(appContext)

    override suspend fun doWork(): Result {
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("SyncWorker", "=== SYNC WORKER STARTED (ID: ${id}) ===")

        try {
            // Check if sync is active
            val prefs =
                    applicationContext.getSharedPreferences(
                            NativeSyncConfig.PREFS_NAME,
                            Context.MODE_PRIVATE
                    )
            val syncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
            DebugLogger.i("SyncWorker", "Sync active status: $syncActive")

            if (!syncActive) {
                DebugLogger.i("SyncWorker", "Sync not active, skipping")
                return Result.success()
            }

            // Show foreground notification for long-running work
            DebugLogger.i("SyncWorker", "Setting foreground service info")
            setForeground(createForegroundInfo("Syncing files..."))

            // Perform sync
            DebugLogger.i("SyncWorker", "Calling performSync()")
            val success = performSync()

            if (success) {
                DebugLogger.i("SyncWorker", "=== SYNC WORKER COMPLETED SUCCESSFULLY ===")
                return Result.success()
            } else {
                DebugLogger.w("SyncWorker", "=== SYNC WORKER COMPLETED WITH ISSUES ===")
                return Result.success() // Don't retry on partial failures
            }
        } catch (e: Exception) {
            DebugLogger.e("SyncWorker", "Sync error in doWork", e)
            return Result.retry()
        }
    }

    private suspend fun performSync(): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs =
                    applicationContext.getSharedPreferences(
                            NativeSyncConfig.PREFS_NAME,
                            Context.MODE_PRIVATE
                    )
            val folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
            DebugLogger.i("SyncWorker", "Folder path from prefs: $folderPath")

            if (folderPath == null) {
                DebugLogger.w("SyncWorker", "No sync folder configured")
                return@withContext false
            }

            // Delegate to SyncEngine
            DebugLogger.i("SyncWorker", "Delegating to SyncEngine.performSync($folderPath)")
            SyncStatusManager.notifySyncStarted()
            val result =
                    syncEngine.performSync(folderPath) { uploaded, total ->
                        DebugLogger.i("SyncWorker", "Sync progress: $uploaded/$total")
                        SyncStatusManager.notifySyncProgress(uploaded, total)
                        setForeground(createForegroundInfo("Uploading $uploaded/$total"))
                    }
            DebugLogger.i("SyncWorker", "SyncEngine.performSync result: $result")
            result >= 0
        }
    }

    private fun updateLastSyncTime() {
        applicationContext
                .getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(NativeSyncConfig.KEY_LAST_SYNC_TIME, java.util.Date().toString())
                .apply()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        createNotificationChannel()

        val notification =
                NotificationCompat.Builder(
                                applicationContext,
                                NativeSyncConfig.NOTIFICATION_CHANNEL_ID
                        )
                        .setContentTitle("EvenSync")
                        .setContentText(text)
                        .setSmallIcon(android.R.drawable.ic_popup_sync)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOngoing(true)
                        .build()

        // For Android 14+ (SDK 34+), must specify foreground service type
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                    NativeSyncConfig.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NativeSyncConfig.NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    NativeSyncConfig.NOTIFICATION_CHANNEL_ID,
                                    NativeSyncConfig.NOTIFICATION_CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Notification for background file sync"
                                setShowBadge(false)
                            }

            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

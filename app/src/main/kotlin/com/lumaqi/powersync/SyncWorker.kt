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
import com.lumaqi.powersync.data.SyncSettingsRepository
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

    private val syncEngine = SyncEngine.getInstance(appContext)
    private val repository = SyncSettingsRepository.getInstance(appContext)

    override suspend fun doWork(): Result {
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("SyncWorker", "=== SYNC WORKER STARTED (ID: ${id}) ===")

        try {
            // Check if sync is active
            val syncActive = repository.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
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
                updateLastSyncTime()
                DebugLogger.i("SyncWorker", "=== SYNC WORKER COMPLETED SUCCESSFULLY ===")
                return Result.success()
            } else {
                updateLastSyncTime() // Update time even if partial success, so we don't retry immediately
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
            val folders = repository.getFolders()

            if (folders.isEmpty()) {
                DebugLogger.w("SyncWorker", "No sync folders configured")
                return@withContext false
            }

            SyncStatusManager.notifySyncStarted()

            var allSuccess = true
            var totalUploaded = 0

            // Iterate through all enabled folders
            for (folder in folders) {
                if (!folder.isEnabled) continue

                DebugLogger.i("SyncWorker", "Syncing folder: ${folder.name} (${folder.localPath})")
                val result =
                        syncEngine.performSync(folder.localPath, folder.driveFolderId) { uploaded, total ->
                            DebugLogger.i(
                                    "SyncWorker",
                                    "Sync progress for ${folder.name}: $uploaded/$total"
                            )
                            SyncStatusManager.notifySyncProgress(uploaded, total)
                            setForeground(
                                    createForegroundInfo("Syncing ${folder.name}: $uploaded/$total")
                            )
                        }

                if (result < 0) {
                    allSuccess = false
                } else {
                    totalUploaded += result
                }
            }

            DebugLogger.i("SyncWorker", "Sync completed. Total uploaded: $totalUploaded")
            allSuccess
        }
    }

    private fun updateLastSyncTime() {
        repository.setLong(NativeSyncConfig.KEY_LAST_SYNC_TIME, System.currentTimeMillis())
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        createNotificationChannel()

        val notification =
                NotificationCompat.Builder(
                                applicationContext,
                                NativeSyncConfig.NOTIFICATION_CHANNEL_ID
                        )
                        .setContentTitle("PowerSync")
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

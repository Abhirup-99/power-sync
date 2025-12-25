package com.lumaqi.powersync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
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

            // Check constraints manually for more precision
            if (!checkManualConstraints()) {
                DebugLogger.i("SyncWorker", "Manual constraints not met, skipping")
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
                
                val retrySetting = repository.getString(NativeSyncConfig.KEY_RETRY_AUTOMATICALLY, "for both manual and automatic sync")
                if (retrySetting!!.contains("automatic") || retrySetting.contains("both")) {
                    if (runAttemptCount < getMaxRetries()) {
                        DebugLogger.i("SyncWorker", "Retrying due to issues (Attempt ${runAttemptCount + 1})")
                        return Result.retry()
                    }
                }
                return Result.success()
            }
        } catch (e: Exception) {
            DebugLogger.e("SyncWorker", "Sync error in doWork", e)
            val retrySetting = repository.getString(NativeSyncConfig.KEY_RETRY_AUTOMATICALLY, "for both manual and automatic sync")
            if (retrySetting!!.contains("automatic") || retrySetting.contains("both")) {
                if (runAttemptCount < getMaxRetries()) {
                    return Result.retry()
                }
            }
            return Result.failure()
        }
    }

    private fun checkManualConstraints(): Boolean {
        // Battery Level
        val batteryStatus: Intent? = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()) else 100f
        
        val thresholdStr = repository.getString(NativeSyncConfig.KEY_BATTERY_LEVEL_THRESHOLD, "50%")
        val threshold = thresholdStr?.replace("%", "")?.toIntOrNull() ?: 50
        
        if (batteryPct < threshold) {
            DebugLogger.i("SyncWorker", "Battery level too low: $batteryPct% < $threshold%")
            return false
        }

        // Network constraints
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // Metered Wi-Fi
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val allowedOnMetered = repository.getBoolean(NativeSyncConfig.KEY_ALLOWED_ON_METERED_WIFI, true)
            if (isMetered && !allowedOnMetered) {
                DebugLogger.i("SyncWorker", "On metered Wi-Fi but not allowed")
                return false
            }
        }

        // Roaming
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val isRoaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            val allowedOnRoaming = repository.getBoolean(NativeSyncConfig.KEY_ALLOWED_ON_MOBILE_ROAMING, false)
            if (isRoaming && !allowedOnRoaming) {
                DebugLogger.i("SyncWorker", "On roaming but not allowed")
                return false
            }
        }

        return true
    }

    private fun getMaxRetries(): Int {
        val maxAttemptsStr = repository.getString(NativeSyncConfig.KEY_MAX_RETRY_ATTEMPTS, "1 time")
        return when {
            maxAttemptsStr!!.contains("1 time") -> 1
            maxAttemptsStr.contains("2 times") -> 2
            maxAttemptsStr.contains("3 times") -> 3
            maxAttemptsStr.contains("5 times") -> 5
            maxAttemptsStr.contains("unlimited") -> Int.MAX_VALUE
            else -> 1
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
                        syncEngine.performSync(folder.localPath, folder.driveFolderId) { uploaded, total, progress, fileName ->
                            DebugLogger.i(
                                    "SyncWorker",
                                    "Sync progress for ${folder.name}: $uploaded/$total (${(progress * 100).toInt()}%) $fileName"
                            )
                            SyncStatusManager.notifySyncProgress(uploaded, total, progress, fileName)
                            
                            val progressText = if (fileName != null) {
                                "Syncing ${folder.name}: $uploaded/$total (${(progress * 100).toInt()}%) $fileName"
                            } else {
                                "Syncing ${folder.name}: $uploaded/$total"
                            }
                            
                            setForeground(createForegroundInfo(progressText))
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

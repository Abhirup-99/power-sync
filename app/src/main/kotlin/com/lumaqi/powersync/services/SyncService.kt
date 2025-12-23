package com.lumaqi.powersync.services

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.lumaqi.powersync.DebugLogger
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import com.lumaqi.powersync.SyncWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncService(private val context: Context) {

    private val database = NativeSyncDatabase(context)
    private val workManager = WorkManager.getInstance(context)
    private val syncEngine = SyncEngine(context)

    companion object {
        private const val WORK_NAME = "sync_work"
    }

    fun enableAndStartSync(folderPath: String) {
        DebugLogger.i("SyncService", "enableAndStartSync called with path: $folderPath")

        // Save preferences
        val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
                .putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, folderPath)
                .putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, true)
                .apply()

        // Start services
        startFileMonitorService()
        startWorkManager()

        // Trigger immediate sync via WorkManager (OneTime)
        val oneTimeWork =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()

        workManager.enqueue(oneTimeWork)
        DebugLogger.i("SyncService", "Immediate sync enqueued via WorkManager")
    }

    suspend fun performSync(
            folderPath: String,
            startBackgroundService: Boolean,
            onProgress: (uploaded: Int, total: Int) -> Unit
    ): Int {
        DebugLogger.i(
                "SyncService",
                "performSync called with folderPath: $folderPath, startBackgroundService: $startBackgroundService"
        )
        return withContext(Dispatchers.IO) {
            // Save folder path
            val prefs =
                    context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, folderPath).apply()
            DebugLogger.i("SyncService", "Saved folder path to prefs")

            // Delegate to SyncEngine
            DebugLogger.i("SyncService", "Delegating to SyncEngine.performSync")
            val result = syncEngine.performSync(folderPath, onProgress)
            DebugLogger.i("SyncService", "SyncEngine.performSync result: $result")

            if (startBackgroundService) {
                DebugLogger.i("SyncService", "Starting background services")
                startWorkManager()
                startFileMonitorService()
                prefs.edit().putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, true).apply()
            }
            result
        }
    }

    private fun startWorkManager() {
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =
                PeriodicWorkRequestBuilder<SyncWorker>(
                                NativeSyncConfig.SYNC_INTERVAL_MINUTES,
                                TimeUnit.MINUTES
                        )
                        .setConstraints(constraints)
                        .build()

        workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        )

        DebugLogger.i("SyncService", "WorkManager sync scheduled")
    }

    private fun startFileMonitorService() {
        val intent = Intent(context, FileMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        DebugLogger.i("SyncService", "FileMonitorService started from SyncService")
    }

    private fun stopFileMonitorService() {
        val intent = Intent(context, FileMonitorService::class.java)
        context.stopService(intent)
        DebugLogger.i("SyncService", "FileMonitorService stopped from SyncService")
    }

    fun stopSync() {
        workManager.cancelUniqueWork(WORK_NAME)
        stopFileMonitorService()

        context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
                .apply()

        DebugLogger.i("SyncService", "WorkManager and FileMonitorService sync stopped")
    }

    fun getUnsyncedFilesCount(folderPath: String): Int {
        return database.getUnsyncedFiles(folderPath).size
    }

    suspend fun clearSyncCache() {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete("synced_files", null, null)
            database.writableDatabase.delete("sync_metadata", null, null)
            DebugLogger.i("SyncService", "Sync cache cleared")
        }
    }
}

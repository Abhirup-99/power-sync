package com.lumaqi.powersync.services

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.lumaqi.powersync.DebugLogger
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import com.lumaqi.powersync.SyncWorker
import com.lumaqi.powersync.data.SyncSettingsRepository
import com.lumaqi.powersync.models.SyncFolder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncService private constructor(private val context: Context) {

    private val database = NativeSyncDatabase.getInstance(context)
    private val repository = SyncSettingsRepository.getInstance(context)
    private val workManager = WorkManager.getInstance(context)
    private val syncEngine = SyncEngine.getInstance(context)

    companion object {
        @Volatile
        private var INSTANCE: SyncService? = null

        fun getInstance(context: Context): SyncService {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private const val WORK_NAME = "sync_work"
    }

    fun enableAndStartSync(folderPath: String, driveFolderId: String? = null, driveFolderName: String? = null) {
        DebugLogger.i("SyncService", "enableAndStartSync called with path: $folderPath, driveId: $driveFolderId")

        // Save folder if not exists or update if driveId changed
        val folders = repository.getFolders()
        val existingFolder = folders.find { it.localPath == folderPath }

        if (existingFolder == null) {
            val folderName = folderPath.substringAfterLast("/")
            repository.addFolder(
                    SyncFolder(
                            localPath = folderPath,
                            name = if (folderName.isNotEmpty()) folderName else "Sync Folder",
                            driveFolderId = driveFolderId,
                            driveFolderName = driveFolderName ?: "PowerSync"
                    )
            )
        } else if (driveFolderId != null && existingFolder.driveFolderId != driveFolderId) {
            repository.addFolder(
                    existingFolder.copy(
                            driveFolderId = driveFolderId,
                            driveFolderName = driveFolderName ?: existingFolder.driveFolderName
                    )
            )
        }
        repository.setBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, true)

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
            driveFolderId: String? = null,
            startBackgroundService: Boolean,
            onProgress: (uploaded: Int, total: Int) -> Unit
    ): Int {
        DebugLogger.i(
                "SyncService",
                "performSync called with folderPath: $folderPath, driveFolderId: $driveFolderId, startBackgroundService: $startBackgroundService"
        )
        return withContext(Dispatchers.IO) {
            // Delegate to SyncEngine
            DebugLogger.i("SyncService", "Delegating to SyncEngine.performSync")
            val result = syncEngine.performSync(folderPath, driveFolderId, onProgress = onProgress)
            DebugLogger.i("SyncService", "SyncEngine.performSync result: $result")

            if (startBackgroundService) {
                DebugLogger.i("SyncService", "Starting background services")
                startWorkManager()
                startFileMonitorService()
                repository.setBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, true)
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

        repository.setBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)

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

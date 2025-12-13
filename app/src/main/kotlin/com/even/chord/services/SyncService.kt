package com.even.chord.services

import android.content.Context
import androidx.work.*
import com.even.chord.DebugLogger
import com.even.chord.NativeSyncConfig
import com.even.chord.NativeSyncDatabase
import com.even.chord.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class SyncService(private val context: Context) {
    
    private val database = NativeSyncDatabase(context)
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val workManager = WorkManager.getInstance(context)
    
    companion object {
        private const val WORK_NAME = "sync_work"
    }
    
    suspend fun performSync(
        folderPath: String,
        startBackgroundService: Boolean,
        onProgress: (uploaded: Int, total: Int) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val user = auth.currentUser ?: throw Exception("Not authenticated")
            val userEmail = user.email ?: throw Exception("No email")
            
            // Save folder path
            val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, folderPath).apply()
            
            // Get unsynced files
            val unsyncedFiles = database.getUnsyncedFiles(folderPath)
            
            if (unsyncedFiles.isEmpty()) {
                updateLastSyncTime()
                return@withContext
            }
            
            DebugLogger.i("SyncService", "Found ${unsyncedFiles.size} files to sync")
            
            var successCount = 0
            onProgress(0, unsyncedFiles.size)
            
            unsyncedFiles.forEachIndexed { _, file ->
                val storagePath = uploadToFirebase(file, userEmail)
                if (storagePath != null) {
                    database.markAsSynced(
                        file.absolutePath,
                        NativeSyncConfig.STORAGE_RECORDINGS_FOLDER,
                        storagePath
                    )
                    successCount++
                }
                onProgress(successCount, unsyncedFiles.size)
            }
            
            updateLastSyncTime()
            
            if (startBackgroundService) {
                startWorkManager()
                prefs.edit().putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, true).apply()
            }
            
            DebugLogger.i("SyncService", "Sync completed: $successCount/${unsyncedFiles.size} files")
        }
    }
    
    private suspend fun uploadToFirebase(file: File, userEmail: String): String? {
        return try {
            val storagePath = "${NativeSyncConfig.STORAGE_BASE_PATH}/$userEmail/${NativeSyncConfig.STORAGE_RECORDINGS_FOLDER}/${file.name}"
            val storageRef = storage.reference.child(storagePath)
            
            // Check if file already exists with same size
            try {
                val metadata = storageRef.metadata.await()
                if (metadata.sizeBytes == file.length()) {
                    DebugLogger.i("SyncService", "File already exists: ${file.name}")
                    return storagePath
                }
            } catch (e: Exception) {
                // File doesn't exist, continue with upload
            }
            
            // Upload file
            val uri = android.net.Uri.fromFile(file)
            storageRef.putFile(uri).await()
            
            storagePath
        } catch (e: Exception) {
            DebugLogger.e("SyncService", "Upload failed: ${file.name}", e)
            null
        }
    }
    
    private fun updateLastSyncTime() {
        context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(NativeSyncConfig.KEY_LAST_SYNC_TIME, java.util.Date().toString())
            .apply()
    }
    
    private fun startWorkManager() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            NativeSyncConfig.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
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
    
    fun stopSync() {
        workManager.cancelUniqueWork(WORK_NAME)
        
        context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
            .apply()
        
        DebugLogger.i("SyncService", "WorkManager sync stopped")
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

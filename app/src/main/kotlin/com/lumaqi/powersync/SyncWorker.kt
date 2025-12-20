package com.lumaqi.powersync

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based sync worker that handles file sync to Firebase Storage and API.
 * This is the ONLY sync mechanism - runs periodically via WorkManager (minimum 15 minutes).
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val database = NativeSyncDatabase(appContext)
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(NativeSyncConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NativeSyncConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NativeSyncConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("SyncWorker", "=== SYNC WORKER STARTED ===")

        try {
            // Check if sync is active
            val prefs = applicationContext.getSharedPreferences(
                NativeSyncConfig.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val syncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)

            if (!syncActive) {
                DebugLogger.i("SyncWorker", "Sync not active, skipping")
                return Result.success()
            }

            // Show foreground notification for long-running work
            setForeground(createForegroundInfo("Syncing files..."))

            // Perform sync
            val success = performSync()

            if (success) {
                DebugLogger.i("SyncWorker", "=== SYNC WORKER COMPLETED SUCCESSFULLY ===")
                return Result.success()
            } else {
                DebugLogger.w("SyncWorker", "=== SYNC WORKER COMPLETED WITH ISSUES ===")
                return Result.success() // Don't retry on partial failures
            }
        } catch (e: Exception) {
            DebugLogger.e("SyncWorker", "Sync error", e)
            return Result.retry()
        }
    }

    private suspend fun performSync(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val user = auth.currentUser
                if (user == null) {
                    DebugLogger.w("SyncWorker", "No authenticated user")
                    return@withContext false
                }

                val prefs = applicationContext.getSharedPreferences(
                    NativeSyncConfig.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                val folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)

                if (folderPath == null) {
                    DebugLogger.w("SyncWorker", "No sync folder configured")
                    return@withContext false
                }

                // Get unsynced files
                val unsyncedFiles = database.getUnsyncedFiles(folderPath)

                if (unsyncedFiles.isEmpty()) {
                    DebugLogger.i("SyncWorker", "No files to sync")
                    updateLastSyncTime()
                    return@withContext true
                }

                DebugLogger.i("SyncWorker", "Found ${unsyncedFiles.size} files to sync")

                val userEmail = user.email ?: return@withContext false
                val uploadedPaths = mutableListOf<String>()

                // Upload each file
                unsyncedFiles.forEachIndexed { index, file ->
                    setForeground(createForegroundInfo("Uploading ${index + 1}/${unsyncedFiles.size}"))

                    val storagePath = uploadToFirebase(file, userEmail)
                    if (storagePath != null) {
                        database.markAsSynced(
                            file.absolutePath,
                            NativeSyncConfig.STORAGE_RECORDINGS_FOLDER,
                            storagePath
                        )
                        uploadedPaths.add(storagePath)
                        DebugLogger.i("SyncWorker", "Uploaded: ${file.name}")
                    } else {
                        DebugLogger.e("SyncWorker", "Failed to upload: ${file.name}")
                    }
                }

                // Call API with uploaded paths
                if (uploadedPaths.isNotEmpty()) {
                    val apiSuccess = callUploadApi(user, uploadedPaths)
                    if (apiSuccess) {
                        DebugLogger.i("SyncWorker", "API call successful for ${uploadedPaths.size} files")
                    } else {
                        DebugLogger.w("SyncWorker", "API call failed")
                    }
                }

                updateLastSyncTime()
                DebugLogger.i("SyncWorker", "Sync completed: ${uploadedPaths.size} files uploaded")
                true
            } catch (e: Exception) {
                DebugLogger.e("SyncWorker", "performSync error", e)
                false
            }
        }
    }

    private suspend fun uploadToFirebase(file: File, userEmail: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val storagePath = "${NativeSyncConfig.STORAGE_BASE_PATH}/$userEmail/${NativeSyncConfig.STORAGE_RECORDINGS_FOLDER}/${file.name}"
                val storageRef = storage.reference.child(storagePath)

                // Check if file already exists with same size
                try {
                    val metadata = storageRef.metadata.await()
                    if (metadata.sizeBytes == file.length()) {
                        DebugLogger.i("SyncWorker", "File already exists: ${file.name}")
                        return@withContext storagePath
                    }
                } catch (e: Exception) {
                    // File doesn't exist, continue with upload
                }

                // Upload file
                val uri = android.net.Uri.fromFile(file)
                storageRef.putFile(uri).await()

                storagePath
            } catch (e: Exception) {
                DebugLogger.e("SyncWorker", "Firebase upload error: ${e.message}")
                null
            }
        }
    }

    private suspend fun callUploadApi(
        user: com.google.firebase.auth.FirebaseUser,
        storagePaths: List<String>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get fresh ID token
                val tokenResult = user.getIdToken(true).await()
                val idToken = tokenResult.token ?: return@withContext false

                // Build request body
                val recordings = JSONArray()
                storagePaths.forEach { path ->
                    recordings.put(JSONObject().put("file_path", path))
                }
                val body = JSONObject().put("recordings", recordings)

                // Make API call
                val request = Request.Builder()
                    .url("${NativeSyncConfig.API_BASE_URL}${NativeSyncConfig.API_UPLOAD_ENDPOINT}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                response.use {
                    it.isSuccessful
                }
            } catch (e: Exception) {
                DebugLogger.e("SyncWorker", "API call error: ${e.message}")
                false
            }
        }
    }

    private fun updateLastSyncTime() {
        applicationContext.getSharedPreferences(
            NativeSyncConfig.PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(NativeSyncConfig.KEY_LAST_SYNC_TIME, java.util.Date().toString())
            .apply()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, NativeSyncConfig.NOTIFICATION_CHANNEL_ID)
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
            val channel = NotificationChannel(
                NativeSyncConfig.NOTIFICATION_CHANNEL_ID,
                NativeSyncConfig.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for background file sync"
                setShowBadge(false)
            }

            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

package com.lumaqi.powersync.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.MimeTypeMap
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.lumaqi.powersync.DebugLogger
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import com.lumaqi.powersync.data.SyncSettingsRepository
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

class SyncEngine private constructor(private val context: Context) {

    private val database = NativeSyncDatabase.getInstance(context)
    private val repository = SyncSettingsRepository.getInstance(context)
    private val auth = FirebaseAuth.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: SyncEngine? = null

        fun getInstance(context: Context): SyncEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private val folderMutex = Mutex()
        private val syncMutex = Mutex()
        private val uploadSemaphore = Semaphore(3) // Limit concurrent uploads to handle large files better
    }

    suspend fun performSync(
            folderPath: String,
            driveFolderId: String? = null,
            onProgress: (suspend (uploaded: Int, total: Int, currentProgress: Float, currentFileName: String?) -> Unit)? = null
    ): Int {
        return syncMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val user = auth.currentUser
                    if (user == null) {
                        DebugLogger.w("SyncEngine", "No authenticated user in Firebase Auth")
                        return@withContext -1
                    }
                    DebugLogger.i("SyncEngine", "Firebase user authenticated: ${user.uid}")

                    // Initialize Drive Service
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        DebugLogger.w(
                                "SyncEngine",
                                "No Google account found with GoogleSignIn.getLastSignedInAccount"
                        )
                        return@withContext -1
                    }
                    DebugLogger.i("SyncEngine", "Google account found: ${googleAccount.email}")

                    val credential =
                            GoogleAccountCredential.usingOAuth2(
                                    context,
                                    Collections.singleton(DriveScopes.DRIVE)
                            )
                    credential.selectedAccount = googleAccount.account
                    DebugLogger.i(
                            "SyncEngine",
                            "Credential created for account: ${googleAccount.account?.name}"
                    )

                    val driveService =
                            Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
                                    .setApplicationName("PowerSync")
                                    .build()

                    // Get unsynced files
                    DebugLogger.i(
                            "SyncEngine",
                            "Querying database for unsynced files in: $folderPath"
                    )
                    val unsyncedFiles = database.getUnsyncedFiles(folderPath)

                    if (unsyncedFiles.isEmpty()) {
                        DebugLogger.i("SyncEngine", "No files to sync according to database")
                        updateLastSyncTime()
                        SyncStatusManager.notifySyncFinished()
                        return@withContext 0
                    }

                    DebugLogger.i("SyncEngine", "Found ${unsyncedFiles.size} files to sync")

                    val successCount = AtomicInteger(0)
                    val successfulUploads = Collections.synchronizedList(mutableListOf<Pair<File, String>>())
                    onProgress?.invoke(0, unsyncedFiles.size, 0f, null)

                    // Get target Drive folder
                    DebugLogger.i("SyncEngine", "Determining target Drive folder")
                    val folderFromRepo = repository.getFolders().find { it.localPath == folderPath }
                    val configuredFolderId =
                            driveFolderId
                                    ?: folderFromRepo?.driveFolderId
                                    ?: repository.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_ID)

                    val parentFolderId =
                            if (!configuredFolderId.isNullOrEmpty()) {
                                DebugLogger.i(
                                        "SyncEngine",
                                        "Using folder ID: $configuredFolderId"
                                )
                                configuredFolderId
                            } else {
                                DebugLogger.i(
                                        "SyncEngine",
                                        "No folder configured, using default 'PowerSync' folder"
                                )
                                folderMutex.withLock {
                                    getOrCreateFolder(driveService, "PowerSync")
                                }
                            }
                    DebugLogger.i("SyncEngine", "Parent folder ID: $parentFolderId")

                    val parallelUploads = repository.getBoolean(NativeSyncConfig.KEY_PARALLEL_UPLOADS, true)
                    
                    if (parallelUploads) {
                        coroutineScope {
                            unsyncedFiles.map { file ->
                                launch {
                                    if (!isFileSizeAllowed(file)) {
                                        DebugLogger.i("SyncEngine", "File size not allowed for current network: ${file.name}")
                                        return@launch
                                    }
                                    
                                    // Use semaphore to limit concurrency
                                    uploadSemaphore.acquire()
                                    try {
                                        val driveFileId = uploadToDrive(driveService, file, parentFolderId) { progress ->
                                            runBlocking {
                                                onProgress?.invoke(successCount.get(), unsyncedFiles.size, progress, file.name)
                                            }
                                        }
                                        
                                        if (driveFileId != null) {
                                            database.markAsSynced(
                                                file.absolutePath,
                                                NativeSyncConfig.STORAGE_RECORDINGS_FOLDER,
                                                driveFileId
                                            )
                                            DebugLogger.i("SyncEngine", "Successfully uploaded: ${file.name} (ID: $driveFileId)")
                                            successfulUploads.add(file to driveFileId)
                                            val currentSuccess = successCount.incrementAndGet()
                                            onProgress?.invoke(currentSuccess, unsyncedFiles.size, 1f, file.name)
                                        } else {
                                            DebugLogger.e("SyncEngine", "Failed to upload: ${file.name}")
                                        }
                                    } finally {
                                        uploadSemaphore.release()
                                    }
                                }
                            }.joinAll()
                        }
                    } else {
                        unsyncedFiles.forEachIndexed { index, file ->
                            if (!isFileSizeAllowed(file)) {
                                DebugLogger.i("SyncEngine", "File size not allowed for current network: ${file.name}")
                                onProgress?.invoke(successCount.get(), unsyncedFiles.size, 0f, null)
                                return@forEachIndexed
                            }

                            DebugLogger.i(
                                "SyncEngine",
                                "Attempting to upload file ${index + 1}/${unsyncedFiles.size}: ${file.name}"
                            )
                            val driveFileId = uploadToDrive(driveService, file, parentFolderId) { progress ->
                                runBlocking {
                                    onProgress?.invoke(successCount.get(), unsyncedFiles.size, progress, file.name)
                                }
                            }
                            
                            if (driveFileId != null) {
                                database.markAsSynced(
                                    file.absolutePath,
                                    NativeSyncConfig.STORAGE_RECORDINGS_FOLDER,
                                    driveFileId
                                )
                                successfulUploads.add(file to driveFileId)
                                val currentSuccess = successCount.incrementAndGet()
                                DebugLogger.i(
                                    "SyncEngine",
                                    "Successfully uploaded: ${file.name} (ID: $driveFileId)"
                                )
                                onProgress?.invoke(currentSuccess, unsyncedFiles.size, 1f, file.name)
                            } else {
                                DebugLogger.e("SyncEngine", "Failed to upload: ${file.name}")
                                onProgress?.invoke(successCount.get(), unsyncedFiles.size, 0f, null)
                            }
                        }
                    }

                    updateLastSyncTime()
                    DebugLogger.i(
                            "SyncEngine",
                            "Sync completed: ${successfulUploads.size} files uploaded"
                    )
                    val count = successfulUploads.size
                    SyncStatusManager.notifySyncFinished()
                    count
                } catch (e: Exception) {
                    DebugLogger.e("SyncEngine", "performSync error", e)
                    SyncStatusManager.notifySyncFinished()
                    -1
                }
            }
        }
    }

    private fun escapeQueryString(value: String): String {
        return value.replace("'", "\\'")
    }

    private fun getOrCreateFolder(driveService: Drive, folderName: String): String {
        try {
            // Check if folder exists
            val escapedName = escapeQueryString(folderName)
            val query =
                    "mimeType = 'application/vnd.google-apps.folder' and name = '$escapedName' and trashed = false"
            val result =
                    driveService
                            .files()
                            .list()
                            .setQ(query)
                            .setSpaces("drive")
                            .setFields("files(id, name)")
                            .execute()

            if (result.files.isNotEmpty()) {
                return result.files[0].id
            }

            // Create folder
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = folderName
            fileMetadata.mimeType = "application/vnd.google-apps.folder"

            val file = driveService.files().create(fileMetadata).setFields("id").execute()

            return file.id
        } catch (e: Exception) {
            DebugLogger.e("SyncEngine", "Error getting/creating folder", e)
            throw e
        }
    }

    private fun uploadToDrive(
        driveService: Drive,
        file: File,
        parentFolderId: String,
        onByteProgress: ((progress: Float) -> Unit)? = null
    ): String? {
        return try {
            // Check if file already exists in the folder
            val escapedName = escapeQueryString(file.name)
            val query = "'$parentFolderId' in parents and name = '$escapedName' and trashed = false"
            val result =
                    driveService
                            .files()
                            .list()
                            .setQ(query)
                            .setSpaces("drive")
                            .setFields("files(id, size)")
                            .execute()

            if (result.files.isNotEmpty()) {
                val existingFile = result.files[0]
                // Simple check: if size matches, assume it's the same file
                if (existingFile.getSize() == file.length()) {
                    DebugLogger.i("SyncEngine", "File already exists in Drive: ${file.name}")
                    onByteProgress?.invoke(1f)
                    return existingFile.id
                }
            }

            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = file.name
            fileMetadata.parents = Collections.singletonList(parentFolderId)

            val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
            val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                            ?: "application/octet-stream"
            val mediaContent = FileContent(mimeType, file)

            val createRequest = driveService.files().create(fileMetadata, mediaContent)
            
            // Configure resumable upload for large files
            val uploader = createRequest.mediaHttpUploader
            uploader.isDirectUploadEnabled = false // Force resumable upload
            uploader.setProgressListener { progressUploader ->
                when (progressUploader.uploadState) {
                    MediaHttpUploader.UploadState.INITIATION_STARTED -> {
                        DebugLogger.i("SyncEngine", "Upload initiation started: ${file.name}")
                    }
                    MediaHttpUploader.UploadState.INITIATION_COMPLETE -> {
                        DebugLogger.i("SyncEngine", "Upload initiation complete: ${file.name}")
                    }
                    MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                        onByteProgress?.invoke(progressUploader.progress.toFloat())
                    }
                    MediaHttpUploader.UploadState.MEDIA_COMPLETE -> {
                        DebugLogger.i("SyncEngine", "Upload complete: ${file.name}")
                        onByteProgress?.invoke(1f)
                    }
                    else -> {}
                }
            }

            val uploadedFile = createRequest.setFields("id").execute()
            uploadedFile.id
        } catch (e: Exception) {
            DebugLogger.e("SyncEngine", "Drive upload error for ${file.name}: ${e.message}")
            null
        }
    }

    private fun updateLastSyncTime() {
        repository.setLong(NativeSyncConfig.KEY_LAST_SYNC_TIME, System.currentTimeMillis())
    }

    private fun isFileSizeAllowed(file: File): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val limitStr = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            repository.getString(NativeSyncConfig.KEY_WIFI_UPLOAD_LIMIT, "no limit")
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            repository.getString(NativeSyncConfig.KEY_MOBILE_UPLOAD_LIMIT, "< 20 MB")
        } else {
            "no limit"
        }

        if (limitStr == "no limit") return true

        val limitBytes = parseSizeLimit(limitStr!!)
        return file.length() <= limitBytes
    }

    private fun parseSizeLimit(limitStr: String): Long {
        return when {
            limitStr.contains("< 10 MB") -> 10 * 1024 * 1024L
            limitStr.contains("< 20 MB") -> 20 * 1024 * 1024L
            limitStr.contains("< 50 MB") -> 50 * 1024 * 1024L
            limitStr.contains("< 100 MB") -> 100 * 1024 * 1024L
            else -> Long.MAX_VALUE
        }
    }
}

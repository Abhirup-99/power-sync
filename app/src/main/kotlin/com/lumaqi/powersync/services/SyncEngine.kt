package com.lumaqi.powersync.services

import android.content.Context
import android.webkit.MimeTypeMap
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.lumaqi.powersync.DebugLogger
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncEngine(private val context: Context) {

    private val database = NativeSyncDatabase(context)
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private val folderMutex = Mutex()
        private val syncMutex = Mutex()
    }

    suspend fun performSync(
            folderPath: String,
            onProgress: (suspend (uploaded: Int, total: Int) -> Unit)? = null
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

                    var successCount = 0
                    val successfulUploads = mutableListOf<Pair<File, String>>()
                    onProgress?.invoke(0, unsyncedFiles.size)

                    // Get target Drive folder
                    DebugLogger.i("SyncEngine", "Determining target Drive folder")
                    val prefs =
                            context.getSharedPreferences(
                                    NativeSyncConfig.PREFS_NAME,
                                    Context.MODE_PRIVATE
                            )
                    val configuredFolderId =
                            prefs.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_ID, null)

                    val parentFolderId =
                            if (!configuredFolderId.isNullOrEmpty()) {
                                DebugLogger.i(
                                        "SyncEngine",
                                        "Using configured folder ID: $configuredFolderId"
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

                    unsyncedFiles.forEachIndexed { index, file ->
                        DebugLogger.i(
                                "SyncEngine",
                                "Attempting to upload file ${index + 1}/${unsyncedFiles.size}: ${file.name}"
                        )
                        val driveFileId = uploadToDrive(driveService, file, parentFolderId)
                        if (driveFileId != null) {
                            database.markAsSynced(
                                    file.absolutePath,
                                    NativeSyncConfig.STORAGE_RECORDINGS_FOLDER,
                                    driveFileId
                            )
                            successfulUploads.add(file to driveFileId)
                            successCount++
                            DebugLogger.i(
                                    "SyncEngine",
                                    "Successfully uploaded: ${file.name} (ID: $driveFileId)"
                            )
                        } else {
                            DebugLogger.e("SyncEngine", "Failed to upload: ${file.name}")
                        }
                        if (successCount % 5 == 0 || successCount == unsyncedFiles.size) {
                            onProgress?.invoke(successCount, unsyncedFiles.size)
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

    private fun uploadToDrive(driveService: Drive, file: File, parentFolderId: String): String? {
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
                // Note: Drive API returns size as Long (String in JSON), File.length() is Long
                if (existingFile.getSize() == file.length()) {
                    DebugLogger.i("SyncEngine", "File already exists in Drive: ${file.name}")
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

            val uploadedFile =
                    driveService
                            .files()
                            .create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute()

            uploadedFile.id
        } catch (e: Exception) {
            DebugLogger.e("SyncEngine", "Drive upload error: ${e.message}")
            null
        }
    }

    private fun updateLastSyncTime() {
        context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(NativeSyncConfig.KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .apply()
    }
}

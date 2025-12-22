package com.lumaqi.powersync.services

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.lumaqi.powersync.DebugLogger
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DriveFolder(val id: String, val name: String)

enum class DriveCategory {
    MY_DRIVE,
    SHARED_WITH_ME,
    STARRED
}

class GoogleDriveService(private val context: Context) {

    private val driveService: Drive? by lazy { getDriveService(context) }

    private fun getDriveService(context: Context): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential =
                GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(DriveScopes.DRIVE_FILE)
                )
        credential.selectedAccount = googleAccount.account

        return Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
                .setApplicationName("PowerSync")
                .build()
    }

    suspend fun listFolders(
            parentId: String? = null,
            category: DriveCategory = DriveCategory.MY_DRIVE
    ): List<DriveFolder> =
            withContext(Dispatchers.IO) {
                try {
                    val service = driveService ?: return@withContext emptyList()

                    val query =
                            if (parentId != null) {
                                // Navigating inside a folder - same for all categories
                                "'${parentId}' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                            } else {
                                // Root level queries based on category
                                when (category) {
                                    DriveCategory.MY_DRIVE ->
                                            "'root' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                                    DriveCategory.SHARED_WITH_ME ->
                                            "sharedWithMe = true and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                                    DriveCategory.STARRED ->
                                            "starred = true and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                                }
                            }

                    val result =
                            service.files()
                                    .list()
                                    .setQ(query)
                                    .setSpaces("drive")
                                    .setFields("files(id, name)")
                                    .setOrderBy("name")
                                    .execute()

                    result.files.map { DriveFolder(it.id, it.name) }
                } catch (e: Exception) {
                    DebugLogger.e("GoogleDriveService", "Error listing folders", e)
                    emptyList()
                }
            }

    suspend fun createFolder(name: String, parentId: String? = null): String? =
            withContext(Dispatchers.IO) {
                try {
                    val service = driveService ?: return@withContext null
                    val fileMetadata = com.google.api.services.drive.model.File()
                    fileMetadata.name = name
                    fileMetadata.mimeType = "application/vnd.google-apps.folder"

                    if (parentId != null) {
                        fileMetadata.parents = Collections.singletonList(parentId)
                    }

                    val file = service.files().create(fileMetadata).setFields("id").execute()
                    file.id
                } catch (e: Exception) {
                    DebugLogger.e("GoogleDriveService", "Error creating folder", e)
                    null
                }
            }
}

package com.lumaqi.powersync.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.data.SyncSettingsRepository
import com.lumaqi.powersync.models.SyncFolder
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.services.GoogleDriveService
import com.lumaqi.powersync.ui.components.DriveFolderPickerDialog
import com.lumaqi.powersync.ui.theme.*
import com.lumaqi.powersync.utils.FileUtils
import kotlinx.coroutines.launch

@Composable
fun ConnectDriveScreen(onFolderSelected: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val syncService = remember { SyncService.getInstance(context) }
        val repository = remember { SyncSettingsRepository.getInstance(context) }
        var folderPath by remember { mutableStateOf<String?>(null) }
        var driveFolderId by remember { mutableStateOf<String?>(null) }
        var driveFolderName by remember { mutableStateOf<String?>(null) }
        var showDrivePicker by remember { mutableStateOf(false) }
        var isSyncing by remember { mutableStateOf(false) }

        val folderPickerLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                        uri?.let {
                                val path = FileUtils.getPathFromUri(context, it)
                                if (path != null) {
                                        folderPath = path
                                } else {
                                        Toast.makeText(
                                                        context,
                                                        "Could not access folder",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                }
                        }
                }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer,
                                                                MaterialTheme.colorScheme.background
                                                        )
                                        )
                                )
        ) {
                Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "Connect Drive",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = "Choose a local folder to sync with your Google Drive.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        // Selected Path Display
                        if (folderPath != null) {
                                Text(
                                        text = "Local Folder:",
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                        alpha = 0.6f
                                                ),
                                        modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                        )
                                                        .padding(16.dp)
                                ) {
                                        Text(
                                                text = folderPath ?: "",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (driveFolderName != null) {
                                Text(
                                        text = "Drive Destination:",
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                        alpha = 0.6f
                                                ),
                                        modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                        )
                                                        .padding(16.dp)
                                ) {
                                        Text(
                                                text = driveFolderName ?: "",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Select Local Folder Button
                        Button(
                                onClick = {
                                        if (Build.VERSION.SDK_INT >=
                                                        Build.VERSION_CODES.R &&
                                                        !Environment
                                                                .isExternalStorageManager()
                                        ) {
                                                // ... permission logic ...
                                                try {
                                                        val intent =
                                                                Intent(
                                                                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                                        )
                                                                        .apply {
                                                                                data =
                                                                                        Uri.parse(
                                                                                                "package:${context.packageName}"
                                                                                        )
                                                                        }
                                                        context.startActivity(intent)
                                                } catch (e: Exception) {
                                                        val intent =
                                                                Intent(
                                                                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                                                )
                                                        context.startActivity(intent)
                                                }
                                        } else {
                                                folderPickerLauncher.launch(null)
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = if (folderPath != null) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                        ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = if (folderPath == null) "Select Local Folder" else "Change Local Folder",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Select Drive Folder Button
                        Button(
                                onClick = { showDrivePicker = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = if (driveFolderName != null) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                        ) {
                                Icon(Icons.Rounded.Cloud, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = if (driveFolderName == null) "Select Drive Folder" else "Change Drive Folder",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Add Folder Button
                        Button(
                                onClick = {
                                        if (folderPath != null && driveFolderId != null) {
                                                isSyncing = true
                                                scope.launch {
                                                        try {
                                                                val folderName = folderPath!!.substringAfterLast("/")
                                                                val existingFolder = repository.getFolders().find { it.localPath == folderPath }
                                                                val newFolder = if (existingFolder != null) {
                                                                    existingFolder.copy(
                                                                        driveFolderId = driveFolderId,
                                                                        driveFolderName = driveFolderName ?: "PowerSync"
                                                                    )
                                                                } else {
                                                                    SyncFolder(
                                                                        localPath = folderPath!!,
                                                                        name = if (folderName.isNotEmpty()) folderName else "Sync Folder",
                                                                        driveFolderId = driveFolderId,
                                                                        driveFolderName = driveFolderName ?: "PowerSync"
                                                                    )
                                                                }
                                                                repository.addFolder(newFolder)

                                                                syncService.performSync(
                                                                        folderPath!!,
                                                                        driveFolderId,
                                                                        true
                                                                ) { _, _ -> }
                                                                onFolderSelected()
                                                        } catch (e: Exception) {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Sync failed: ${e.message}",
                                                                                Toast.LENGTH_LONG
                                                                        )
                                                                        .show()
                                                        } finally {
                                                                isSyncing = false
                                                        }
                                                }
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = folderPath != null && driveFolderId != null && !isSyncing,
                                shape = RoundedCornerShape(28.dp)
                        ) {
                                if (isSyncing) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                        )
                                } else {
                                        Text(
                                                text = "Add Folder & Sync",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                                Icons.AutoMirrored.Rounded.NavigateNext,
                                                contentDescription = null
                                        )
                                }
                        }
                }
        }

        if (showDrivePicker) {
            val driveService = remember { GoogleDriveService(context) }
            DriveFolderPickerDialog(
                driveService = driveService,
                onDismissRequest = { showDrivePicker = false },
                onFolderSelected = { id, name ->
                    driveFolderId = id
                    driveFolderName = name
                    showDrivePicker = false
                }
            )
        }
}

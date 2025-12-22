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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.services.GoogleAuthService
import com.lumaqi.powersync.services.GoogleDriveService
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.ui.components.DriveFolderPickerDialog
import com.lumaqi.powersync.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFolderSelected: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { GoogleAuthService(context) }
    val syncService = remember { SyncService(context) }

    var folderPath by remember { mutableStateOf<String?>(null) }
    var driveFolderName by remember { mutableStateOf<String?>(null) }
    var showDriveFolderPicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var uploadedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var autoSyncActive by remember { mutableStateOf(false) }
    var lastSyncTime by remember { mutableStateOf<Date?>(null) }
    var pendingFilesCount by remember { mutableStateOf(0) }

    val user = FirebaseAuth.getInstance().currentUser
    val userName = user?.displayName ?: user?.email ?: "User"

    // Load initial state
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
        folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
        driveFolderName = prefs.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_NAME, null)
        autoSyncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)

        val lastSyncString = prefs.getString(NativeSyncConfig.KEY_LAST_SYNC_TIME, null)
        lastSyncTime =
                lastSyncString?.let {
                    try {
                        SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US).parse(it)
                    } catch (e: Exception) {
                        null
                    }
                }

        folderPath?.let { pendingFilesCount = syncService.getUnsyncedFilesCount(it) }

        isLoading = false
    }

    // Periodic status update
    LaunchedEffect(autoSyncActive) {
        while (autoSyncActive) {
            delay(10000)
            folderPath?.let { pendingFilesCount = syncService.getUnsyncedFilesCount(it) }
        }
    }

    val folderPickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    // Convert content URI to file path
                    val path = getPathFromUri(context, it)
                    if (path != null) {
                        folderPath = path
                        val prefs =
                                context.getSharedPreferences(
                                        NativeSyncConfig.PREFS_NAME,
                                        Context.MODE_PRIVATE
                                )
                        prefs.edit().putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, path).apply()

                        scope.launch {
                            pendingFilesCount = syncService.getUnsyncedFilesCount(path)
                            onFolderSelected()
                        }
                    } else {
                        Toast.makeText(context, "Could not access folder", Toast.LENGTH_SHORT)
                                .show()
                    }
                }
            }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking signin status...", color = Grey600)
                }
            }
        } else {
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp)
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // User profile card
                UserProfileCard(
                        userName = userName,
                        onSignOut = {
                            scope.launch {
                                authService.signOut()
                                syncService.stopSync()
                                onSignOut()
                            }
                        }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Drive Folder selection card
                DriveFolderSelectionCard(
                        driveFolderName = driveFolderName,
                        onPickDriveFolder = { showDriveFolderPicker = true }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Folder selection card
                FolderSelectionCard(
                        folderPath = folderPath,
                        isSyncing = isSyncing,
                        autoSyncActive = autoSyncActive,
                        lastSyncTime = lastSyncTime,
                        uploadedCount = uploadedCount,
                        totalCount = totalCount,
                        pendingFilesCount = pendingFilesCount,
                        onPickFolder = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                            !Environment.isExternalStorageManager()
                            ) {
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
                                    Toast.makeText(
                                                    context,
                                                    "Please grant All Files Access to sync folders",
                                                    Toast.LENGTH_LONG
                                            )
                                            .show()
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
                        onSyncNow = {
                            folderPath?.let { path ->
                                scope.launch {
                                    isSyncing = true
                                    try {
                                        syncService.performSync(
                                                folderPath = path,
                                                startBackgroundService = true,
                                                onProgress = { uploaded, total ->
                                                    uploadedCount = uploaded
                                                    totalCount = total
                                                }
                                        )
                                        autoSyncActive = true
                                        lastSyncTime = Date()
                                        pendingFilesCount = syncService.getUnsyncedFilesCount(path)
                                        Toast.makeText(
                                                        context,
                                                        "Sync completed!",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        "Sync error: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            }
                        },
                        onForceSync = {
                            folderPath?.let { path ->
                                scope.launch {
                                    isSyncing = true
                                    try {
                                        syncService.clearSyncCache()
                                        syncService.performSync(
                                                folderPath = path,
                                                startBackgroundService = false,
                                                onProgress = { uploaded, total ->
                                                    uploadedCount = uploaded
                                                    totalCount = total
                                                }
                                        )
                                        lastSyncTime = Date()
                                        pendingFilesCount = syncService.getUnsyncedFilesCount(path)
                                        Toast.makeText(
                                                        context,
                                                        "Force sync completed!",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        "Sync error: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            }
                        },
                        onRefreshStatus = {
                            scope.launch {
                                folderPath?.let {
                                    pendingFilesCount = syncService.getUnsyncedFilesCount(it)
                                }
                            }
                        }
                )
            }
        }
    }

    if (showDriveFolderPicker) {
        val driveService = remember { GoogleDriveService(context) }
        DriveFolderPickerDialog(
                driveService = driveService,
                onDismissRequest = { showDriveFolderPicker = false },
                onFolderSelected = { id, name ->
                    driveFolderName = name
                    val prefs =
                            context.getSharedPreferences(
                                    NativeSyncConfig.PREFS_NAME,
                                    Context.MODE_PRIVATE
                            )
                    prefs.edit()
                            .putString(NativeSyncConfig.KEY_DRIVE_FOLDER_ID, id)
                            .putString(NativeSyncConfig.KEY_DRIVE_FOLDER_NAME, name)
                            .apply()
                    showDriveFolderPicker = false
                }
        )
    }
}

@Composable
private fun DriveFolderSelectionCard(driveFolderName: String?, onPickDriveFolder: () -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                    text = "Drive Destination",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Grey900
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Select folder button
            OutlinedButton(
                    onClick = onPickDriveFolder,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(driveFolderName ?: "Select Drive Folder (Default: PowerSync)")
            }
        }
    }
}

@Composable
private fun UserProfileCard(userName: String, onSignOut: () -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = ButtonDefaults.outlinedButtonBorder
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                    modifier =
                            Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Primary)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = userName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text = "Signed in", fontSize = 12.sp, color = Grey600)
            }

            TextButton(onClick = onSignOut) { Text("Sign Out", color = Error) }
        }
    }
}

@Composable
private fun FolderSelectionCard(
        folderPath: String?,
        isSyncing: Boolean,
        autoSyncActive: Boolean,
        lastSyncTime: Date?,
        uploadedCount: Int,
        totalCount: Int,
        pendingFilesCount: Int,
        onPickFolder: () -> Unit,
        onSyncNow: () -> Unit,
        onForceSync: () -> Unit,
        onRefreshStatus: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                    text = "Folder Selection",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Grey900
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Select folder button
            Button(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Folder")
            }

            // Folder status
            if (folderPath != null) {
                Spacer(modifier = Modifier.height(20.dp))

                FolderStatusDisplay(
                        folderPath = folderPath,
                        autoSyncActive = autoSyncActive,
                        lastSyncTime = lastSyncTime,
                        pendingCount = pendingFilesCount,
                        onRefresh = onRefreshStatus
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sync now button
            Button(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = folderPath != null && !isSyncing,
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = Green,
                                    disabledContainerColor = Grey200
                            ),
                    shape = RoundedCornerShape(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            if (totalCount > 0) "Syncing $uploadedCount/$totalCount..."
                            else "Syncing...",
                            fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Force sync button
            OutlinedButton(
                    onClick = onForceSync,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = folderPath != null && !isSyncing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                    border =
                            ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Orange)
                            ),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.SyncProblem, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Force Sync (Reset & Sync All)")
            }
        }
    }
}

@Composable
private fun FolderStatusDisplay(
        folderPath: String,
        autoSyncActive: Boolean,
        lastSyncTime: Date?,
        pendingCount: Int,
        onRefresh: () -> Unit
) {
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Grey200.copy(alpha = 0.3f))
                            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = folderPath.split("/").lastOrNull() ?: folderPath,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = Grey600,
                        modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            StatusChip(
                    text = if (autoSyncActive) "Auto-sync: ON" else "Auto-sync: OFF",
                    isActive = autoSyncActive
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (lastSyncTime != null) {
                val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                StatusChip(text = "Last: ${format.format(lastSyncTime)}", isActive = false)
                Spacer(modifier = Modifier.width(8.dp))
            }

            StatusChip(
                    text = "Pending: $pendingCount",
                    isActive = pendingCount > 0,
                    color = if (pendingCount > 0) Orange else Grey600
            )
        }
    }
}

@Composable
private fun StatusChip(
        text: String,
        isActive: Boolean,
        color: Color = if (isActive) Green else Grey600
) {
    Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            modifier =
                    Modifier.clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Suppress("UNUSED_PARAMETER")
private fun getPathFromUri(context: Context, uri: Uri): String? {
    // For document tree URIs, we need to use the actual path
    // Try to extract from the URI
    val docId = uri.lastPathSegment ?: return null

    // Common pattern: "primary:folder/subfolder"
    if (docId.startsWith("primary:")) {
        val relativePath = docId.substringAfter("primary:")
        return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
    }

    // If not primary, try to use the path directly
    return uri.path?.let { path ->
        // Remove /tree/ prefix if present
        path.replace("/tree/", "").let { cleanPath ->
            if (cleanPath.startsWith("primary:")) {
                "${Environment.getExternalStorageDirectory().absolutePath}/${cleanPath.substringAfter("primary:")}"
            } else {
                // Might be an external SD card or other storage
                cleanPath
            }
        }
    }
}

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
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ConnectDriveScreen(onFolderSelected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncService = remember { SyncService(context) }
    var folderPath by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    // Load initial state
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
        folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
    }

    val folderPickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    val path = getPathFromUri(context, it)
                    if (path != null) {
                        folderPath = path
                        val prefs =
                                context.getSharedPreferences(
                                        NativeSyncConfig.PREFS_NAME,
                                        Context.MODE_PRIVATE
                                )
                        prefs.edit().putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, path).apply()
                    } else {
                        Toast.makeText(context, "Could not access folder", Toast.LENGTH_SHORT)
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
                        text = "Selected Folder:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
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
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Select Folder Button
            if (folderPath == null) {
                Button(
                        onClick = {
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
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Select Folder", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                        onClick = {
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
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Change Folder", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue Button
            Button(
                    onClick = {
                        if (folderPath != null) {
                            isSyncing = true
                            scope.launch {
                                try {
                                    syncService.performSync(folderPath!!, true) { _, _ -> }
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
                    enabled = folderPath != null && !isSyncing,
                    shape = RoundedCornerShape(28.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
                }
            }
        }
    }
}

// Helper function duplicated here to ensure independence
// In a real app, this should be in a utils file
@Suppress("UNUSED_PARAMETER")
private fun getPathFromUri(context: Context, uri: Uri): String? {
    val docId = uri.lastPathSegment ?: return null
    if (docId.startsWith("primary:")) {
        val relativePath = docId.substringAfter("primary:")
        return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
    }
    return uri.path?.let { path ->
        path.replace("/tree/", "").let { cleanPath ->
            if (cleanPath.startsWith("primary:")) {
                "${Environment.getExternalStorageDirectory().absolutePath}/${cleanPath.substringAfter("primary:")}"
            } else {
                cleanPath
            }
        }
    }
}

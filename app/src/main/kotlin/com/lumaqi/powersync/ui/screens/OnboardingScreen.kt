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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.services.GoogleAuthService
import com.lumaqi.powersync.services.GoogleDriveService
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.ui.components.DriveFolderPickerDialog
import com.lumaqi.powersync.ui.theme.Error
import com.lumaqi.powersync.ui.theme.Grey600
import com.lumaqi.powersync.ui.theme.Grey900
import com.lumaqi.powersync.ui.theme.Primary
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

    val user = FirebaseAuth.getInstance().currentUser
    val userName = user?.displayName ?: user?.email ?: "User"

    // Load initial state
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
        folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
        driveFolderName = prefs.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_NAME, null)
        isLoading = false
    }

    // Periodic status update

    val folderPickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    // Convert content URI to file path
                    val path = getPathFromUri(it)
                    if (path != null) {
                        folderPath = path
                        syncService.enableAndStartSync(path)
                        onFolderSelected()
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
private fun FolderSelectionCard(folderPath: String?, onPickFolder: () -> Unit) {
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
                Text(if (folderPath != null) "Change Folder" else "Select Folder")
            }

            if (folderPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "Selected: $folderPath",
                        fontSize = 12.sp,
                        color = Grey600,
                        modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

private fun getPathFromUri(uri: Uri): String? {
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

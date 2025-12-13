package com.even.chord.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.chord.NativeSyncConfig
import com.even.chord.ui.theme.*

@Composable
fun ConnectDriveScreen(
    onFolderSelected: () -> Unit
) {
    val context = LocalContext.current
    var folderPath by remember { mutableStateOf<String?>(null) }
    
    // Load initial state
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
        folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(context, it)
            if (path != null) {
                folderPath = path
                val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, path).apply()
            } else {
                Toast.makeText(context, "Could not access folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DriveSyncDarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Connect Drive",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DriveSyncGreenAccent
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Choose a local folder to sync with your Google Drive.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Selected Path Display
            if (folderPath != null) {
                Text(
                    text = "Selected Folder:",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = folderPath ?: "",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Select Folder Button
            Button(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (folderPath == null) DriveSyncGreenAccent else DriveSyncButtonGrey,
                    contentColor = if (folderPath == null) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (folderPath == null) "Select Folder" else "Change Folder",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Continue Button
            Button(
                onClick = onFolderSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = folderPath != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DriveSyncGreenAccent,
                    contentColor = Color.Black,
                    disabledContainerColor = DriveSyncButtonGrey.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Rounded.NavigateNext, contentDescription = null)
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

package com.even.chord.ui.screens

import android.Manifest
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.even.chord.ui.theme.*

data class PermissionItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val permissions: List<String>,
    val isSpecial: Boolean = false
)

@Composable
fun PermissionsScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    
    var isRequesting by remember { mutableStateOf(false) }
    var deniedPermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val permissionItems = remember {
        listOf(

            PermissionItem(
                icon = Icons.Default.Folder,
                title = "Storage & Files",
                description = "To access and backup your recordings",
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                } else {
                    listOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                },
                isSpecial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ),
            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "To notify you about sync status",
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
            ),
            PermissionItem(
                icon = Icons.Default.BatteryChargingFull,
                title = "Battery Optimization",
                description = "To keep background sync running",
                permissions = emptyList(),
                isSpecial = true
            )
        )
    }
    
    // Check permissions on load
    fun checkAllPermissions(): Boolean {

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PermissionChecker.PERMISSION_GRANTED
        }
        
        // Return only storage status for now (though storage perms also removed in manifest, logic still here for legacy/scope?)
        // The user asked to drop READ_PHONE_STATE.
        return hasStorage
    }
    
    fun getDenied(): List<String> {
        val denied = mutableListOf<String>()
        

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PermissionChecker.PERMISSION_GRANTED
        }
        if (!hasStorage) {
            denied.add("Storage & Files")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                denied.add("Notifications")
            }
        }
        
        return denied
    }
    
    LaunchedEffect(Unit) {
        if (checkAllPermissions()) {
            onAllPermissionsGranted()
        }
        deniedPermissions = getDenied()
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        isRequesting = false
        deniedPermissions = getDenied()
        
        if (checkAllPermissions()) {
            onAllPermissionsGranted()
        } else {
            Toast.makeText(
                context,
                "Some permissions were denied. The app may not work properly.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        deniedPermissions = getDenied()
        if (checkAllPermissions()) {
            onAllPermissionsGranted()
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterHorizontally),
                tint = Primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Permissions Required",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Chord needs the following permissions to work properly:",
                fontSize = 16.sp,
                color = Grey600,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Permission items
            permissionItems.forEach { item ->
                PermissionItemCard(item)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Denied permissions warning
            if (deniedPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Orange50)
                        .border(1.dp, Orange200, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = Orange700,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Missing: ${deniedPermissions.joinToString(", ")}",
                        fontSize = 13.sp,
                        color = Orange700
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = {
                    isRequesting = true
                    
                    // Request MANAGE_EXTERNAL_STORAGE for Android 11+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                        !Environment.isExternalStorageManager()) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        manageStorageLauncher.launch(intent)
                    }
                    
                    // Phone permission removed
                    // val permissionsToRequest = mutableListOf<String>()
                    val permissionsToRequest = mutableListOf<String>()
                    // permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
                    // permissionsToRequest.add(Manifest.permission.READ_CALL_LOG)
                    
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isRequesting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRequesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Grant Permissions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionItemCard(item: PermissionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Grey200, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.description,
                fontSize = 12.sp,
                color = Grey600
            )
        }
    }
}

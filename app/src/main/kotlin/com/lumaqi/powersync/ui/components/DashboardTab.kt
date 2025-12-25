package com.lumaqi.powersync.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashboardTabContent(
    lastSyncTime: Long,
    isSyncing: Boolean,
    totalSyncedFiles: Int,
    totalSyncedSize: Long,
    totalPendingFiles: Int,
    autoSyncActive: Boolean,
    isBatteryOptimized: Boolean,
    hasStoragePermission: Boolean,
    hasNotificationPermission: Boolean,
    driveStorageTotal: Long,
    driveStorageUsed: Long,
    currentFileName: String? = null,
    currentFileProgress: Float = 0f,
    syncProgressCount: Int = 0,
    totalFilesToSync: Int = 0
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Status Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val statusIcon = when {
                    isSyncing -> Icons.Rounded.Sync
                    totalPendingFiles > 0 -> Icons.Rounded.Pending
                    else -> Icons.Rounded.CheckCircle
                }
                val statusColor = when {
                    isSyncing -> MaterialTheme.colorScheme.primary
                    totalPendingFiles > 0 -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                }
                
                Icon(
                    statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = statusColor
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when {
                        isSyncing -> {
                            if (currentFileName != null) "Syncing: $currentFileName"
                            else "Syncing..."
                        }
                        totalPendingFiles > 0 -> "$totalPendingFiles files pending"
                        else -> "All synced"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (isSyncing && totalFilesToSync > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { currentFileProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "File ${syncProgressCount + 1} of $totalFilesToSync (${(currentFileProgress * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Text(
                    text = if (lastSyncTime > 0) "Last sync: ${DateUtils.getRelativeTimeSpanString(lastSyncTime)}" else "No syncs yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1.5 Sync Details
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Sync Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (isSyncing) "Syncing..." else if (totalPendingFiles > 0) "Pending" else "Idle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Last Synced", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (lastSyncTime > 0) DateUtils.getRelativeTimeSpanString(lastSyncTime).toString() else "Never",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (lastSyncTime > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Last Sync Ended", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val dateFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                            Text(
                                dateFormat.format(java.util.Date(lastSyncTime)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Schedule", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (autoSyncActive) "Every 15 mins" else "Manual only",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Warnings
        if (isBatteryOptimized) {
            item {
                WarningCard(
                    title = "Battery Optimization Active",
                    message = "Background sync may be unreliable.",
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (!hasStoragePermission) {
            item {
                WarningCard(
                    title = "Storage Permission Missing",
                    message = "App needs access to files to sync.",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            item {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> }

                WarningCard(
                    title = "Notification Permission Missing",
                    message = "Android requires notification when an app works in the background.",
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 3. Stats Grid
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.CloudUpload,
                    label = "Uploaded",
                    value = Formatter.formatShortFileSize(context, totalSyncedSize),
                    subValue = "$totalSyncedFiles files"
                )
                
                val driveUsagePercent = if (driveStorageTotal > 0) (driveStorageUsed.toFloat() / driveStorageTotal.toFloat()) else 0f
                StatsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Cloud,
                    label = "Drive Storage",
                    value = if (driveStorageTotal > 0) "${(driveUsagePercent * 100).toInt()}%" else "--",
                    subValue = if (driveStorageTotal > 0) "${Formatter.formatShortFileSize(context, driveStorageTotal - driveStorageUsed)} free" else "Not connected",
                    progress = driveUsagePercent
                )
            }
        }
    }
}

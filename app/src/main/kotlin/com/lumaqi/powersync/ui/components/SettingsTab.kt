package com.lumaqi.powersync.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumaqi.powersync.models.FolderStats
import com.lumaqi.powersync.models.SyncFolder

@Composable
fun SettingsTabContent(
    folders: List<SyncFolder>,
    folderStatsMap: Map<String, FolderStats>,
    onDeleteFolder: (SyncFolder) -> Unit,
    onAddFolder: () -> Unit,
    isBatteryOptimized: Boolean,
    hasStoragePermission: Boolean,
    accountEmail: String?,
    driveFolderName: String?,
    driveStorageTotal: Long,
    driveStorageUsed: Long
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Folders Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Synced Folders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddFolder) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Folder")
                }
            }
        }

        if (folders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No folders configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(folders) { folder ->
                val stats = folderStatsMap[folder.id] ?: FolderStats()
                SyncFolderCard(
                    folder = folder,
                    stats = stats,
                    onDelete = { onDeleteFolder(folder) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Google Drive Account
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        "Google Drive account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (accountEmail != null) {
                        StatusRow("Account", accountEmail)
                        StatusRow("Status", "Connected", Color(0xFF4CAF50))
                        StatusRow("Sync Folder", driveFolderName ?: "PowerSync")

                        if (driveStorageTotal > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))

                            StatusRow("Total Storage", Formatter.formatShortFileSize(context, driveStorageTotal))
                            StatusRow("Used Space", Formatter.formatShortFileSize(context, driveStorageUsed))
                            StatusRow("Free Space", Formatter.formatShortFileSize(context, driveStorageTotal - driveStorageUsed))
                        }
                    } else {
                        Text("Not connected", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Battery Optimization
        if (isBatteryOptimized) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Battery optimization",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "App is battery optimized. This may affect background sync.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) { Text("Turn off", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Storage Permission
        if (!hasStoragePermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Storage permission required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        ) { Text("Grant permission", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

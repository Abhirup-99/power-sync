package com.lumaqi.powersync.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import com.lumaqi.powersync.data.SyncSettingsRepository
import com.lumaqi.powersync.models.SyncFolder
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.services.SyncStatusManager
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class to hold transient UI stats for a folder
data class FolderStats(
    val pendingCount: Int = 0,
    val localFileCount: Int = 0,
    val localSizeBytes: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(onConfigureFolders: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "History", "Settings")
    var isSyncing by remember { mutableStateOf(false) }

    // Repository
    val repository = remember { SyncSettingsRepository(context) }
    val folders by repository.folders.collectAsState()

    // Hoisted States
    val prefs = remember {
        context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val database = remember { NativeSyncDatabase(context) }

    var lastSyncTime by remember { mutableLongStateOf(0L) }
    var isBatteryOptimized by remember { mutableStateOf(true) }
    var hasStoragePermission by remember { mutableStateOf(true) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var autoSyncActive by remember { mutableStateOf(false) }

    var driveStorageTotal by remember { mutableLongStateOf(0L) }
    var driveStorageUsed by remember { mutableLongStateOf(0L) }

    // Aggregate stats
    var totalSyncedFiles by remember { mutableIntStateOf(0) }
    var totalSyncedSize by remember { mutableLongStateOf(0L) }
    var totalPendingFiles by remember { mutableIntStateOf(0) }
    
    // Per-folder stats map
    var folderStatsMap by remember { mutableStateOf<Map<String, FolderStats>>(emptyMap()) }

    // Progress States
    var syncProgressCount by remember { mutableIntStateOf(0) }
    var totalFilesToSync by remember { mutableIntStateOf(0) }

    // Function to refresh data
    fun refreshData() {
        autoSyncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)

        lastSyncTime =
                try {
                    prefs.getLong(NativeSyncConfig.KEY_LAST_SYNC_TIME, 0L)
                } catch (_: ClassCastException) {
                    prefs.edit().remove(NativeSyncConfig.KEY_LAST_SYNC_TIME).apply()
                    0L
                }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)

        hasStoragePermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }

        val account = GoogleSignIn.getLastSignedInAccount(context)
        accountEmail = account?.email

        scope.launch {
            // Calculate aggregate and per-folder stats
            val (syncedCount, syncedSize, pendingCount, newFolderStats) = withContext(Dispatchers.IO) {
                var synced = 0
                var size = 0L
                var pending = 0
                val statsMap = mutableMapOf<String, FolderStats>()

                try {
                    val db = database.readableDatabase
                    val cursor = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(file_size), 0) FROM synced_files", null)
                    cursor.use {
                        if (it.moveToFirst()) {
                            synced = it.getInt(0)
                            size = it.getLong(1)
                        }
                    }
                    
                    // Calculate stats for all folders
                    folders.forEach { folder ->
                        val unsynced = database.getUnsyncedFiles(folder.localPath)
                        val folderPending = unsynced.size
                        pending += folderPending
                        
                        // Local file stats
                        var localCount = 0
                        var localSize = 0L
                        val localFolder = File(folder.localPath)
                        if (localFolder.exists() && localFolder.isDirectory) {
                            val allFiles = localFolder.listFiles()
                            val files = allFiles?.filter { file ->
                                file.isFile && !file.name.startsWith(".trashed-")
                            } ?: emptyList()
                            localCount = files.size
                            localSize = files.sumOf { file -> file.length() }
                        }
                        
                        statsMap[folder.id] = FolderStats(folderPending, localCount, localSize)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Quadruple(synced, size, pending, statsMap)
            }
            
            totalSyncedFiles = syncedCount
            totalSyncedSize = syncedSize
            totalPendingFiles = pendingCount
            folderStatsMap = newFolderStats

            // precise drive storage fetch
            if (account != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val credential =
                                GoogleAccountCredential.usingOAuth2(
                                        context,
                                        Collections.singleton(DriveScopes.DRIVE)
                                )
                        credential.selectedAccount = account.account
                        val driveService =
                                Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
                                        .setApplicationName("PowerSync")
                                        .build()

                        val about = driveService.about().get().setFields("storageQuota").execute()
                        val quota = about.storageQuota
                        if (quota != null) {
                            driveStorageTotal = quota.limit ?: 0L
                            driveStorageUsed = quota.usage ?: 0L
                        } else {
                            driveStorageTotal = 0L
                            driveStorageUsed = 0L
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SyncStatusScreen", "Error fetching storage quota", e)
                    }
                }
            }
        }
    }

    // Load data and refresh on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Refresh when folders list changes
    LaunchedEffect(folders) {
        refreshData()
    }

    // Initial load
    LaunchedEffect(Unit) {
        refreshData()

        // Listen for background sync events
        SyncStatusManager.events.collect { event ->
            when (event) {
                is SyncStatusManager.SyncEvent.FileChanged -> {
                    refreshData()
                }
                is SyncStatusManager.SyncEvent.SyncStarted -> {
                    isSyncing = true
                }
                is SyncStatusManager.SyncEvent.SyncProgress -> {
                    syncProgressCount = event.uploaded
                    totalFilesToSync = event.total
                    refreshData()
                }
                is SyncStatusManager.SyncEvent.SyncFinished -> {
                    isSyncing = false
                    refreshData()
                }
            }
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("PowerSync", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { /* Menu action */}) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                        }
                )
            },
            floatingActionButton = {
                if (selectedTabIndex == 0) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = onConfigureFolders,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Folder")
                        }

                        ExtendedFloatingActionButton(
                            onClick = {
                                if (isSyncing) return@ExtendedFloatingActionButton
                                if (folders.isEmpty()) {
                                    Toast.makeText(context, "No folders configured", Toast.LENGTH_SHORT).show()
                                    return@ExtendedFloatingActionButton
                                }

                                scope.launch {
                                    isSyncing = true
                                    syncProgressCount = 0
                                    totalFilesToSync = 0
                                    Toast.makeText(context, "Sync started...", Toast.LENGTH_SHORT).show()
                                    
                                    try {
                                        val syncService = SyncService(context)
                                        SyncStatusManager.notifySyncStarted()
                                        
                                        // Parallel Sync
                                        val jobs = folders.filter { it.isEnabled }.map { folder ->
                                            async(Dispatchers.IO) {
                                                syncService.performSync(folder.localPath, true) { uploaded, total ->
                                                    // Note: This progress is per-folder and might jump around in the UI
                                                    // Ideally we'd aggregate progress, but for now we just show activity
                                                    SyncStatusManager.notifySyncProgress(uploaded, total)
                                                }
                                            }
                                        }
                                        
                                        val results = jobs.awaitAll()
                                        val totalUploaded = results.sum().coerceAtLeast(0)
                                        
                                        Toast.makeText(context, "Sync completed: $totalUploaded files uploaded", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSyncing = false
                                        refreshData()
                                        SyncStatusManager.notifySyncFinished()
                                    }
                                }
                            },
                            icon = {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null)
                                }
                            },
                            text = {
                                Text(if (isSyncing) "Syncing..." else "Sync All")
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
            }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
                        )
                    }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> DashboardTabContent(
                    folders = folders,
                    folderStatsMap = folderStatsMap,
                    onDeleteFolder = { repository.removeFolder(it.id) },
                    lastSyncTime = lastSyncTime,
                    isSyncing = isSyncing,
                    totalSyncedFiles = totalSyncedFiles,
                    totalSyncedSize = totalSyncedSize,
                    totalPendingFiles = totalPendingFiles,
                    autoSyncActive = autoSyncActive,
                    isBatteryOptimized = isBatteryOptimized,
                    hasStoragePermission = hasStoragePermission,
                    accountEmail = accountEmail,
                    driveStorageTotal = driveStorageTotal,
                    driveStorageUsed = driveStorageUsed
                )
                1 -> SyncHistoryTabContent(database)
                2 -> SettingsTabContent(
                    isBatteryOptimized = isBatteryOptimized,
                    hasStoragePermission = hasStoragePermission,
                    accountEmail = accountEmail,
                    driveStorageTotal = driveStorageTotal,
                    driveStorageUsed = driveStorageUsed
                )
            }
        }
    }
}

@Composable
fun DashboardTabContent(
    folders: List<SyncFolder>,
    folderStatsMap: Map<String, FolderStats>,
    onDeleteFolder: (SyncFolder) -> Unit,
    lastSyncTime: Long,
    isSyncing: Boolean,
    totalSyncedFiles: Int,
    totalSyncedSize: Long,
    totalPendingFiles: Int,
    autoSyncActive: Boolean,
    isBatteryOptimized: Boolean,
    hasStoragePermission: Boolean,
    accountEmail: String?,
    driveStorageTotal: Long,
    driveStorageUsed: Long
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
                        isSyncing -> "Syncing..."
                        totalPendingFiles > 0 -> "$totalPendingFiles files pending"
                        else -> "All synced"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
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

        // 4. Folders Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Synced Folders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (autoSyncActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Auto-sync On",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 5. Folders List
        if (folders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                    onDelete = { onDeleteFolder(folder) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun WarningCard(title: String, message: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun StatsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    progress: Float? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun SyncFolderCard(folder: SyncFolder, stats: FolderStats, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        folder.localPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FolderStatBadge(
                            label = "Pending",
                            value = stats.pendingCount.toString(),
                            color = if (stats.pendingCount > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FolderStatBadge(
                            label = "Local Files",
                            value = stats.localFileCount.toString()
                        )
                        FolderStatBadge(
                            label = "Size",
                            value = Formatter.formatShortFileSize(context, stats.localSizeBytes)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun FolderStatBadge(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SettingsTabContent(
    isBatteryOptimized: Boolean,
    hasStoragePermission: Boolean,
    accountEmail: String?,
    driveStorageTotal: Long,
    driveStorageUsed: Long
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

@Composable
fun SyncHistoryTabContent(database: NativeSyncDatabase) {
    val context = LocalContext.current
    val syncHistory = remember { mutableStateListOf<Map<String, Any>>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    val limit = 50

    // Function to load history
    suspend fun loadHistoryItems(isInitial: Boolean) {
        if (!hasMore && !isInitial) return

        if (isInitial) {
            isLoading = true
            offset = 0
            syncHistory.clear()
        } else {
            isLoadingMore = true
        }

        withContext(Dispatchers.IO) {
            val newItems = database.getSyncHistory(limit, offset)
            withContext(Dispatchers.Main) {
                if (newItems.size < limit) {
                    hasMore = false
                }
                syncHistory.addAll(newItems)
                offset += newItems.size
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHistoryItems(true)
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (syncHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                    "No sync history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(syncHistory.size) { index ->
                val item = syncHistory[index]
                val fileName = item["file_name"] as? String ?: "Unknown"
                val syncedAtRaw = item["synced_at"] as? String ?: ""
                val fileSize = item["file_size"] as? Long ?: 0L

                val syncedAtFormatted =
                        remember(syncedAtRaw) {
                            try {
                                val sdf =
                                        java.text.SimpleDateFormat(
                                                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                                                java.util.Locale.US
                                        )
                                val date = sdf.parse(syncedAtRaw)
                                if (date != null) {
                                    DateUtils.getRelativeTimeSpanString(date.time)
                                } else {
                                    syncedAtRaw
                                }
                            } catch (e: Exception) {
                                syncedAtRaw
                            }
                        }

                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                    Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    "${Formatter.formatShortFileSize(context, fileSize)} • $syncedAtFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Load more when reaching the end
                if (index == syncHistory.lastIndex && hasMore && !isLoadingMore) {
                    LaunchedEffect(Unit) {
                        loadHistoryItems(false)
                    }
                }
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(
        label: String,
        value: String,
        valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.End
    ) {
        Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
                text = value,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                color = valueColor
        )
    }
}

// Helper class for 4 values
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.services.SyncStatusManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(onConfigureFolders: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Status", "Sync history", "Synced folders")
    var isSyncing by remember { mutableStateOf(false) }

    // Hoisted States from StatusTabContent
    val prefs = remember {
        context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val database = remember { NativeSyncDatabase(context) }

    var lastSyncTime by remember { mutableLongStateOf(0L) }
    var isBatteryOptimized by remember { mutableStateOf(true) }
    var hasStoragePermission by remember { mutableStateOf(true) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var folderPath by remember { mutableStateOf<String?>(null) }
    var autoSyncActive by remember { mutableStateOf(false) }

    var syncedFilesCount by remember { mutableIntStateOf(0) }
    var pendingFilesCount by remember { mutableIntStateOf(0) }
    var totalSyncedSize by remember { mutableLongStateOf(0L) }
    var localFilesCount by remember { mutableIntStateOf(0) }
    var localFilesSize by remember { mutableLongStateOf(0L) }

    // Progress States
    var syncProgressCount by remember { mutableIntStateOf(0) }
    var totalFilesToSync by remember { mutableIntStateOf(0) }

    // Function to refresh data
    fun refreshData() {
        val currentFolderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
        folderPath = currentFolderPath
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
            val stats =
                    withContext(Dispatchers.IO) {
                        var syncedCount = 0
                        var syncedSize = 0L
                        var pendingCount = 0
                        var localCount = 0
                        var localSize = 0L

                        try {
                            val db = database.readableDatabase
                            val cursor =
                                    db.rawQuery(
                                            "SELECT COUNT(*), COALESCE(SUM(file_size), 0) FROM synced_files",
                                            null
                                    )
                            cursor.use {
                                if (it.moveToFirst()) {
                                    syncedCount = it.getInt(0)
                                    syncedSize = it.getLong(1)
                                }
                            }

                            currentFolderPath?.let { path ->
                                val localFolder = File(path)
                                if (localFolder.exists() && localFolder.isDirectory) {
                                    val allFiles = localFolder.listFiles()
                                    val files =
                                            allFiles?.filter { file -> file.isFile } ?: emptyList()
                                    localCount = files.size
                                    localSize = files.sumOf { file -> file.length() }

                                    val unsyncedFiles = database.getUnsyncedFiles(path)
                                    pendingCount = unsyncedFiles.size
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SyncStatusScreen", "Error loading stats", e)
                        }

                        arrayOf(syncedCount, syncedSize, pendingCount, localCount, localSize)
                    }

            syncedFilesCount = stats[0] as Int
            totalSyncedSize = stats[1] as Long
            pendingFilesCount = stats[2] as Int
            localFilesCount = stats[3] as Int
            localFilesSize = stats[4] as Long
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
                            IconButton(onClick = { refreshData() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(onClick = { /* Menu action */}) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                        }
                )
            },
            floatingActionButton = {
                if (selectedTabIndex == 0) {
                    ExtendedFloatingActionButton(
                            onClick = {
                                if (isSyncing) return@ExtendedFloatingActionButton

                                scope.launch {
                                    val currentFolderPath =
                                            prefs.getString(
                                                    NativeSyncConfig.KEY_SYNC_FOLDER_PATH,
                                                    null
                                            )

                                    if (currentFolderPath != null) {
                                        isSyncing = true
                                        syncProgressCount = 0
                                        totalFilesToSync = 0
                                        Toast.makeText(
                                                        context,
                                                        "Sync started...",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        try {
                                            val syncService = SyncService(context)
                                            SyncStatusManager.notifySyncStarted()
                                            val count =
                                                    syncService.performSync(
                                                            currentFolderPath,
                                                            true
                                                    ) { uploaded, total ->
                                                        SyncStatusManager.notifySyncProgress(
                                                                uploaded,
                                                                total
                                                        )
                                                    }
                                            if (count >= 0) {
                                                Toast.makeText(
                                                                context,
                                                                "Sync completed: $count files uploaded",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            } else {
                                                Toast.makeText(
                                                                context,
                                                                "Sync failed",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                            context,
                                                            "Sync failed: ${e.message}",
                                                            Toast.LENGTH_LONG
                                                    )
                                                    .show()
                                        } finally {
                                            // The collector in LaunchedEffect will handle isSyncing
                                            // = false
                                            // and refreshData() via
                                            // SyncStatusManager.notifySyncFinished()
                                        }
                                    } else {
                                        Toast.makeText(
                                                        context,
                                                        "Please configure a folder first",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
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
                                Text(
                                        if (isSyncing) {
                                            if (totalFilesToSync > 0)
                                                    "Syncing ($syncProgressCount/$totalFilesToSync)..."
                                            else "Syncing..."
                                        } else "Sync"
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
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
                0 ->
                        StatusTabContent(
                                onConfigureFolders = onConfigureFolders,
                                lastSyncTime = lastSyncTime,
                                isBatteryOptimized = isBatteryOptimized,
                                hasStoragePermission = hasStoragePermission,
                                accountEmail = accountEmail,
                                folderPath = folderPath,
                                autoSyncActive = autoSyncActive,
                                isSyncing = isSyncing,
                                syncedFilesCount = syncedFilesCount,
                                pendingFilesCount = pendingFilesCount,
                                totalSyncedSize = totalSyncedSize,
                                localFilesCount = localFilesCount,
                                localFilesSize = localFilesSize
                        )
                1 -> SyncHistoryTabContent(database)
                2 -> SyncedFoldersTabContent(folderPath, onConfigureFolders)
            }
        }
    }
}

@Composable
fun StatusTabContent(
        onConfigureFolders: () -> Unit,
        lastSyncTime: Long,
        isBatteryOptimized: Boolean,
        hasStoragePermission: Boolean,
        accountEmail: String?,
        folderPath: String?,
        autoSyncActive: Boolean,
        isSyncing: Boolean,
        syncedFilesCount: Int,
        pendingFilesCount: Int,
        totalSyncedSize: Long,
        localFilesCount: Int,
        localFilesSize: Long
) {
    val context = LocalContext.current

    LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sync Status Card
        item {
            Card(
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            "Sync status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatusRow(
                            "Last sync",
                            if (lastSyncTime > 0)
                                    DateUtils.getRelativeTimeSpanString(lastSyncTime).toString()
                            else "Never"
                    )
                    StatusRow(
                            "Status",
                            when {
                                isSyncing -> "Syncing..."
                                pendingFilesCount > 0 -> "Pending"
                                else -> "Up to date"
                            },
                            when {
                                isSyncing -> MaterialTheme.colorScheme.primary
                                pendingFilesCount > 0 -> Color(0xFFFF9800)
                                else -> Color(0xFF4CAF50)
                            }
                    )
                    StatusRow(
                            "Auto-sync",
                            if (autoSyncActive) "Active" else "Disabled",
                            if (autoSyncActive) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sync Statistics Card
        item {
            Card(
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            "Sync statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatusRow("Files synced", "$syncedFilesCount files")
                    StatusRow(
                            "Total uploaded",
                            Formatter.formatShortFileSize(context, totalSyncedSize)
                    )
                    StatusRow(
                            "Pending files",
                            "$pendingFilesCount files",
                            if (pendingFilesCount > 0) Color(0xFFFF9800)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Synced Folder Card (when configured)
        if (folderPath != null) {
            item {
                Card(
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(
                                "Synced folder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                folderPath,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusRow("Local files", "$localFilesCount files")
                        StatusRow(
                                "Local size",
                                Formatter.formatShortFileSize(context, localFilesSize)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onConfigureFolders) { Text("Change folder") }
                    }
                }
            }
        }

        // Battery Optimization Card
        if (isBatteryOptimized) {
            item {
                Card(
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                )
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
                                "This app is being battery optimized by Android. If you want it to sync files reliably, you need to exclude the app from optimization and allow it to run unrestricted in the background.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                                onClick = {
                                    val intent =
                                            Intent(
                                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                                    )
                                                    .apply {
                                                        data =
                                                                Uri.parse(
                                                                        "package:${context.packageName}"
                                                                )
                                                    }
                                    context.startActivity(intent)
                                }
                        ) { Text("Turn off battery optimization", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { /* Show info */}) {
                            Text("More info", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Storage Permission Card (Android 11+)
        if (!hasStoragePermission) {
            item {
                Card(
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                "Storage permission required",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                "PowerSync needs access to all files to sync your folders. Please grant the 'All files access' permission in settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                                    }
                                }
                        ) { Text("Grant permission", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // No folder pair configured
        if (folderPath == null) {
            item {
                Card(
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(
                                "No folder pair configured and enabled. Nothing to sync.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onConfigureFolders) { Text("Configure") }
                    }
                }
            }
        }

        // Google Drive Account
        item {
            Card(
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
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
                    } else {
                        Text("Not connected", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Spacer for FAB
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun SyncHistoryTabContent(database: NativeSyncDatabase) {
    val context = LocalContext.current
    var syncHistory by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            syncHistory = database.getSyncHistory()
            isLoading = false
        }
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
            }
        }
    }
}

@Composable
fun SyncedFoldersTabContent(folderPath: String?, onConfigureFolders: () -> Unit) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
                "Configured Folders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
        )

        if (folderPath != null) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                                "Local Sync Folder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            folderPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onConfigureFolders, modifier = Modifier.align(Alignment.End)) {
                        Text("Change Folder")
                    }
                }
            }
        } else {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            "No folder configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onConfigureFolders) { Text("Configure Now") }
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

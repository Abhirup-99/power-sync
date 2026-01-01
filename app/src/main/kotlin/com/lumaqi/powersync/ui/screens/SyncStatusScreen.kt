package com.lumaqi.powersync.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.lumaqi.powersync.models.FolderStats
import com.lumaqi.powersync.models.SyncFolder
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.services.SyncStatusManager
import com.lumaqi.powersync.ui.components.DashboardTabContent
import com.lumaqi.powersync.ui.components.SettingsTabContent
import com.lumaqi.powersync.ui.components.SyncHistoryTabContent
import java.io.File
import java.util.Collections
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    onConfigureFolders: () -> Unit,
    onNavigateToSynchronizationSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "History", "Settings")
    var isSyncing by remember { mutableStateOf(false) }

    // Repository
    val repository = remember { SyncSettingsRepository.getInstance(context) }
    val folders by repository.folders.collectAsState()

    // Hoisted States
    val database = remember { NativeSyncDatabase.getInstance(context) }

    var lastSyncTime by remember { mutableLongStateOf(0L) }
    var isBatteryOptimized by remember { mutableStateOf(true) }
    var hasStoragePermission by remember { mutableStateOf(true) }
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var driveFolderName by remember { mutableStateOf<String?>(null) }
    var autoSyncActive by remember { mutableStateOf(false) }

    var driveStorageTotal by remember { mutableLongStateOf(0L) }
    var driveStorageUsed by remember { mutableLongStateOf(0L) }

    var showMobileWarning by remember { mutableStateOf(false) }

    // Aggregate stats
    var totalSyncedFiles by remember { mutableIntStateOf(0) }
    var totalSyncedSize by remember { mutableLongStateOf(0L) }
    var totalPendingFiles by remember { mutableIntStateOf(0) }
    
    // Per-folder stats map
    var folderStatsMap by remember { mutableStateOf<Map<String, FolderStats>>(emptyMap()) }

    // Progress States
    var syncProgressCount by remember { mutableIntStateOf(0) }
    var totalFilesToSync by remember { mutableIntStateOf(0) }
    var currentFileProgress by remember { mutableFloatStateOf(0f) }
    var currentFileName by remember { mutableStateOf<String?>(null) }

    // Function to refresh data
    fun refreshData() {
        autoSyncActive = repository.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
        driveFolderName = repository.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_NAME)

        lastSyncTime = repository.getLong(NativeSyncConfig.KEY_LAST_SYNC_TIME, 0L)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)

        hasStoragePermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }

        hasNotificationPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
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
                    val (syncedCount, syncedSize) = database.getSyncedStats()
                    synced = syncedCount
                    size = syncedSize
                    
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            refreshData()
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
                    syncProgressCount = event.uploadedFiles
                    totalFilesToSync = event.totalFiles
                    currentFileProgress = event.currentFileProgress
                    currentFileName = event.currentFileName
                    refreshData()
                }
                is SyncStatusManager.SyncEvent.SyncFinished -> {
                    isSyncing = false
                    refreshData()
                }
            }
        }
    }

    if (showMobileWarning) {
        AlertDialog(
            onDismissRequest = { showMobileWarning = false },
            title = { Text("Sync on Mobile Network?") },
            text = { Text("You are currently on a mobile network. Syncing may consume significant data.") },
            confirmButton = {
                TextButton(onClick = {
                    showMobileWarning = false
                    startSync(scope, folders, context, refreshData = { refreshData() }, setSyncing = { isSyncing = it })
                }) {
                    Text("Sync Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMobileWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("PowerSync", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:hello@lumaqi.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "PowerSync Chat Request")
                                }
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Chat")
                            }

                            var showMenu by remember { mutableStateOf(false) }
                            
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Synchronization") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToSynchronizationSettings()
                                    }
                                )
                            }
                        }
                )
            },
            floatingActionButton = {
                if (selectedTabIndex == 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (isSyncing) return@ExtendedFloatingActionButton
                            if (folders.isEmpty()) {
                                Toast.makeText(context, "No folders configured", Toast.LENGTH_SHORT).show()
                                return@ExtendedFloatingActionButton
                            }

                            if (!hasStoragePermission) {
                                Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
                                return@ExtendedFloatingActionButton
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@ExtendedFloatingActionButton
                            }

                            // Check for mobile network warning
                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val network = connectivityManager.activeNetwork
                            val capabilities = connectivityManager.getNetworkCapabilities(network)
                            val isMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                            val warnOnMobile = repository.getBoolean(NativeSyncConfig.KEY_WARN_ON_MOBILE, true)

                            if (isMobile && warnOnMobile) {
                                showMobileWarning = true
                            } else {
                                startSync(scope, folders, context, refreshData = { refreshData() }, setSyncing = { isSyncing = it })
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
                    lastSyncTime = lastSyncTime,
                    isSyncing = isSyncing,
                    totalSyncedFiles = totalSyncedFiles,
                    totalSyncedSize = totalSyncedSize,
                    totalPendingFiles = totalPendingFiles,
                    autoSyncActive = autoSyncActive,
                    isBatteryOptimized = isBatteryOptimized,
                    hasStoragePermission = hasStoragePermission,
                    hasNotificationPermission = hasNotificationPermission,
                    driveStorageTotal = driveStorageTotal,
                    driveStorageUsed = driveStorageUsed,
                    currentFileName = currentFileName,
                    currentFileProgress = currentFileProgress,
                    syncProgressCount = syncProgressCount,
                    totalFilesToSync = totalFilesToSync
                )
                1 -> SyncHistoryTabContent(database)
                2 -> SettingsTabContent(
                    folders = folders,
                    folderStatsMap = folderStatsMap,
                    onDeleteFolder = { repository.removeFolder(it.id) },
                    onAddFolder = onConfigureFolders,
                    isBatteryOptimized = isBatteryOptimized,
                    hasStoragePermission = hasStoragePermission,
                    hasNotificationPermission = hasNotificationPermission,
                    accountEmail = accountEmail,
                    driveFolderName = driveFolderName,
                    driveStorageTotal = driveStorageTotal,
                    driveStorageUsed = driveStorageUsed
                )
            }
        }
    }
}

// Helper class for 4 values
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun startSync(
    scope: CoroutineScope,
    folders: List<SyncFolder>,
    context: Context,
    refreshData: () -> Unit,
    setSyncing: (Boolean) -> Unit
) {
    scope.launch {
        setSyncing(true)
        Toast.makeText(context, "Sync started...", Toast.LENGTH_SHORT).show()
        
        try {
            val syncService = SyncService.getInstance(context)
            SyncStatusManager.notifySyncStarted()
            // Parallel Sync
            val jobs = folders.filter { it.isEnabled }.map { folder ->
                scope.async(Dispatchers.IO) {
                    syncService.performSync(folder.localPath, folder.driveFolderId, true) { uploaded, total, progress, fileName ->
                        SyncStatusManager.notifySyncProgress(uploaded, total, progress, fileName)
                    }
                }
            }
            
            val results = jobs.awaitAll()
            val totalUploaded = results.sum().coerceAtLeast(0)
            
            Toast.makeText(context, "Sync completed: $totalUploaded files uploaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            setSyncing(false)
            refreshData()
            SyncStatusManager.notifySyncFinished()
        }
    }
}

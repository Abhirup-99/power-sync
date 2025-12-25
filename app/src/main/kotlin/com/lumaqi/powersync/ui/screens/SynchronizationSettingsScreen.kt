package com.lumaqi.powersync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.data.SyncSettingsRepository
import com.lumaqi.powersync.services.SyncService
import com.lumaqi.powersync.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynchronizationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SyncSettingsRepository.getInstance(context) }
    val syncService = remember { SyncService.getInstance(context) }

    // State for all settings
    var parallelUploads by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_PARALLEL_UPLOADS, true)) }
    var parallelDownloads by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_PARALLEL_DOWNLOADS, true)) }
    
    var enableAutosync by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)) }
    var autosyncSchedule by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_AUTOSYNC_SCHEDULE, "Every 2 hours") ?: "Every 2 hours") }
    var onlyWhileCharging by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_ONLY_WHILE_CHARGING, false)) }
    var batteryLevelThreshold by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_BATTERY_LEVEL_THRESHOLD, "50%") ?: "50%") }
    var internetConnection by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_INTERNET_CONNECTION_TYPE, "Using Wi-Fi only") ?: "Using Wi-Fi only") }
    var allowedOnMeteredWifi by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_ALLOWED_ON_METERED_WIFI, true)) }
    var allowedWifiNetworks by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_ALLOWED_WIFI_NETWORKS, "All Wi-Fi networks") ?: "All Wi-Fi networks") }
    var disallowedWifiNetworks by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_DISALLOWED_WIFI_NETWORKS, "None") ?: "None") }
    var allowedOnMobileRoaming by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_ALLOWED_ON_MOBILE_ROAMING, false)) }
    var allowedOnSlow2G by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_ALLOWED_ON_SLOW_2G, true)) }

    var wifiUploadLimit by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_WIFI_UPLOAD_LIMIT, "< 20 MB") ?: "< 20 MB") }
    var wifiDownloadLimit by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_WIFI_DOWNLOAD_LIMIT, "no limit") ?: "no limit") }
    
    var mobileUploadLimit by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_MOBILE_UPLOAD_LIMIT, "< 20 MB") ?: "< 20 MB") }
    var mobileDownloadLimit by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_MOBILE_DOWNLOAD_LIMIT, "no limit") ?: "no limit") }
    var warnOnMobile by remember { mutableStateOf(repository.getBoolean(NativeSyncConfig.KEY_WARN_ON_MOBILE, true)) }

    var retryAutomatically by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_RETRY_AUTOMATICALLY, "for both manual and automatic sync") ?: "for both manual and automatic sync") }
    var retryWaitTime by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_RETRY_WAIT_TIME, "5 minutes") ?: "5 minutes") }
    var maxRetryAttempts by remember { mutableStateOf(repository.getString(NativeSyncConfig.KEY_MAX_RETRY_ATTEMPTS, "1 time") ?: "1 time") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronization", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item { PreferenceCategory("Parallel transfers") }
            item {
                CheckboxPreference(
                    title = "Allow parallel uploads",
                    checked = parallelUploads,
                    onCheckedChange = {
                        parallelUploads = it
                        repository.setBoolean(NativeSyncConfig.KEY_PARALLEL_UPLOADS, it)
                    }
                )
            }
            item {
                CheckboxPreference(
                    title = "Allow parallel downloads",
                    checked = parallelDownloads,
                    onCheckedChange = {
                        parallelDownloads = it
                        repository.setBoolean(NativeSyncConfig.KEY_PARALLEL_DOWNLOADS, it)
                    }
                )
            }

            item { PreferenceCategory("Automatic background sync") }
            item {
                SwitchPreference(
                    title = "Enable autosync",
                    summary = "Monitor folders and sync in background",
                    checked = enableAutosync,
                    onCheckedChange = {
                        enableAutosync = it
                        repository.setBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, it)
                        if (it) {
                            syncService.startWorkManager()
                        } else {
                            syncService.stopSync()
                        }
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Autosync schedule",
                    summary = autosyncSchedule,
                    enabled = enableAutosync,
                    options = listOf("Every 15 minutes", "Every 30 minutes", "Every hour", "Every 2 hours", "Every 6 hours", "Daily"),
                    onOptionSelected = {
                        autosyncSchedule = it
                        repository.setString(NativeSyncConfig.KEY_AUTOSYNC_SCHEDULE, it)
                        if (enableAutosync) syncService.startWorkManager()
                    }
                )
            }
            item {
                CheckboxPreference(
                    title = "Only while device is charging",
                    checked = onlyWhileCharging,
                    enabled = enableAutosync,
                    onCheckedChange = {
                        onlyWhileCharging = it
                        repository.setBoolean(NativeSyncConfig.KEY_ONLY_WHILE_CHARGING, it)
                        if (enableAutosync) syncService.startWorkManager()
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Battery level must be at least",
                    summary = batteryLevelThreshold,
                    enabled = enableAutosync,
                    options = listOf("10%", "20%", "30%", "50%", "80%"),
                    onOptionSelected = {
                        batteryLevelThreshold = it
                        repository.setString(NativeSyncConfig.KEY_BATTERY_LEVEL_THRESHOLD, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Internet connection",
                    summary = internetConnection,
                    enabled = enableAutosync,
                    options = listOf("Using Wi-Fi only", "Using any connection", "Using Wi-Fi or Ethernet"),
                    onOptionSelected = {
                        internetConnection = it
                        repository.setString(NativeSyncConfig.KEY_INTERNET_CONNECTION_TYPE, it)
                        if (enableAutosync) syncService.startWorkManager()
                    }
                )
            }
            item {
                CheckboxPreference(
                    title = "Allowed on metered Wi-Fi",
                    checked = allowedOnMeteredWifi,
                    enabled = enableAutosync,
                    onCheckedChange = {
                        allowedOnMeteredWifi = it
                        repository.setBoolean(NativeSyncConfig.KEY_ALLOWED_ON_METERED_WIFI, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Allowed Wi-Fi networks",
                    summary = allowedWifiNetworks,
                    enabled = enableAutosync,
                    options = listOf("All Wi-Fi networks", "Selected networks only"),
                    onOptionSelected = {
                        allowedWifiNetworks = it
                        repository.setString(NativeSyncConfig.KEY_ALLOWED_WIFI_NETWORKS, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Disallowed Wi-Fi networks",
                    summary = disallowedWifiNetworks,
                    enabled = enableAutosync,
                    options = listOf("None", "Selected networks only"),
                    onOptionSelected = {
                        disallowedWifiNetworks = it
                        repository.setString(NativeSyncConfig.KEY_DISALLOWED_WIFI_NETWORKS, it)
                    }
                )
            }
            item {
                CheckboxPreference(
                    title = "Allowed on mobile roaming",
                    checked = allowedOnMobileRoaming,
                    enabled = enableAutosync,
                    onCheckedChange = {
                        allowedOnMobileRoaming = it
                        repository.setBoolean(NativeSyncConfig.KEY_ALLOWED_ON_MOBILE_ROAMING, it)
                    }
                )
            }
            item {
                CheckboxPreference(
                    title = "Allowed on slow 2G networks",
                    checked = allowedOnSlow2G,
                    enabled = enableAutosync,
                    onCheckedChange = {
                        allowedOnSlow2G = it
                        repository.setBoolean(NativeSyncConfig.KEY_ALLOWED_ON_SLOW_2G, it)
                    }
                )
            }

            item { PreferenceCategory("While connected to Wi-Fi") }
            item {
                DropdownPreference(
                    title = "Upload file size limit",
                    summary = wifiUploadLimit,
                    options = listOf("no limit", "< 10 MB", "< 20 MB", "< 50 MB", "< 100 MB"),
                    onOptionSelected = {
                        wifiUploadLimit = it
                        repository.setString(NativeSyncConfig.KEY_WIFI_UPLOAD_LIMIT, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Download file size limit",
                    summary = wifiDownloadLimit,
                    options = listOf("no limit", "< 10 MB", "< 20 MB", "< 50 MB", "< 100 MB"),
                    onOptionSelected = {
                        wifiDownloadLimit = it
                        repository.setString(NativeSyncConfig.KEY_WIFI_DOWNLOAD_LIMIT, it)
                    }
                )
            }

            item { PreferenceCategory("While connected to mobile network") }
            item {
                DropdownPreference(
                    title = "Upload file size limit",
                    summary = mobileUploadLimit,
                    options = listOf("no limit", "< 10 MB", "< 20 MB", "< 50 MB", "< 100 MB"),
                    onOptionSelected = {
                        mobileUploadLimit = it
                        repository.setString(NativeSyncConfig.KEY_MOBILE_UPLOAD_LIMIT, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Download file size limit",
                    summary = mobileDownloadLimit,
                    options = listOf("no limit", "< 10 MB", "< 20 MB", "< 50 MB", "< 100 MB"),
                    onOptionSelected = {
                        mobileDownloadLimit = it
                        repository.setString(NativeSyncConfig.KEY_MOBILE_DOWNLOAD_LIMIT, it)
                    }
                )
            }
            item {
                CheckboxPreference(
                    title = "Warn if sync on mobile network",
                    summary = "Ask user to confirm before manual sync on mobile network",
                    checked = warnOnMobile,
                    onCheckedChange = {
                        warnOnMobile = it
                        repository.setBoolean(NativeSyncConfig.KEY_WARN_ON_MOBILE, it)
                    }
                )
            }

            item { PreferenceCategory("After sync errors") }
            item {
                DropdownPreference(
                    title = "Try again automatically",
                    summary = retryAutomatically,
                    options = listOf("never", "for manual sync only", "for automatic sync only", "for both manual and automatic sync"),
                    onOptionSelected = {
                        retryAutomatically = it
                        repository.setString(NativeSyncConfig.KEY_RETRY_AUTOMATICALLY, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Wait before each attempt",
                    summary = retryWaitTime,
                    options = listOf("1 minute", "2 minutes", "5 minutes", "10 minutes", "30 minutes"),
                    onOptionSelected = {
                        retryWaitTime = it
                        repository.setString(NativeSyncConfig.KEY_RETRY_WAIT_TIME, it)
                    }
                )
            }
            item {
                DropdownPreference(
                    title = "Max number of attempts",
                    summary = maxRetryAttempts,
                    options = listOf("1 time", "2 times", "3 times", "5 times", "unlimited"),
                    onOptionSelected = {
                        maxRetryAttempts = it
                        repository.setString(NativeSyncConfig.KEY_MAX_RETRY_ATTEMPTS, it)
                    }
                )
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

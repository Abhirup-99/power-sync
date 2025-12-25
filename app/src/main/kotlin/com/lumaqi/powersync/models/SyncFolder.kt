package com.lumaqi.powersync.models

import java.util.UUID

data class SyncFolder(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val name: String, // User friendly name
    val isEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val status: SyncStatus = SyncStatus.Idle,
    val driveFolderId: String? = null,
    val driveFolderName: String? = null
)

enum class SyncStatus {
    Idle,
    Syncing,
    Pending,
    Error
}

// Data class to hold transient UI stats for a folder
data class FolderStats(
    val pendingCount: Int = 0,
    val localFileCount: Int = 0,
    val localSizeBytes: Long = 0L
)

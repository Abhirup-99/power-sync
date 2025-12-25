package com.lumaqi.powersync.models

import java.util.UUID

data class SyncFolder(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val name: String, // User friendly name
    val isEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val status: SyncStatus = SyncStatus.Idle
)

enum class SyncStatus {
    Idle,
    Syncing,
    Pending,
    Error
}

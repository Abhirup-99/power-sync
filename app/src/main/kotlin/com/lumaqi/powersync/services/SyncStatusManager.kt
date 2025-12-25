package com.lumaqi.powersync.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Global manager for sync-related events to allow UI to react to background changes. */
object SyncStatusManager {
    sealed class SyncEvent {
        object FileChanged : SyncEvent()
        object SyncStarted : SyncEvent()
        data class SyncProgress(
            val uploadedFiles: Int,
            val totalFiles: Int,
            val currentFileProgress: Float = 0f,
            val currentFileName: String? = null
        ) : SyncEvent()
        object SyncFinished : SyncEvent()
    }

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun notifyFileChanged() {
        _events.tryEmit(SyncEvent.FileChanged)
    }

    fun notifySyncStarted() {
        _events.tryEmit(SyncEvent.SyncStarted)
    }

    fun notifySyncProgress(uploadedFiles: Int, totalFiles: Int, currentFileProgress: Float = 0f, currentFileName: String? = null) {
        _events.tryEmit(SyncEvent.SyncProgress(uploadedFiles, totalFiles, currentFileProgress, currentFileName))
    }

    fun notifySyncFinished() {
        _events.tryEmit(SyncEvent.SyncFinished)
    }
}

package com.lumaqi.powersync.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Global manager for sync-related events to allow UI to react to background changes. */
object SyncStatusManager {
    sealed class SyncEvent {
        object FileChanged : SyncEvent()
        object SyncStarted : SyncEvent()
        data class SyncProgress(val uploaded: Int, val total: Int) : SyncEvent()
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

    fun notifySyncProgress(uploaded: Int, total: Int) {
        _events.tryEmit(SyncEvent.SyncProgress(uploaded, total))
    }

    fun notifySyncFinished() {
        _events.tryEmit(SyncEvent.SyncFinished)
    }
}

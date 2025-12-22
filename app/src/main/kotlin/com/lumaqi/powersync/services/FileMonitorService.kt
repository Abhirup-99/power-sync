package com.lumaqi.powersync.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lumaqi.powersync.DebugLogger
import com.lumaqi.powersync.NativeSyncConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground Service that uses FileObserver to monitor changes in the sync folder and trigger syncs
 * immediately.
 */
class FileMonitorService : Service() {

    private var fileObserver: FileObserver? = null
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Debounce duration to avoid triggering sync for every single byte write
    private val SYNC_DEBOUNCE_MS = 5000L

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("FileMonitorService", "Service starting")

        createNotificationChannel()
        startForeground(
                NativeSyncConfig.NOTIFICATION_ID + 1, // Use different ID than SyncWorker
                createNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
        )

        startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        val prefs = getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val folderPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)

        if (folderPath == null) {
            DebugLogger.w("FileMonitorService", "No folder path configured")
            stopSelf()
            return
        }

        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            DebugLogger.w("FileMonitorService", "Folder does not exist: $folderPath")
            stopSelf()
            return
        }

        DebugLogger.i("FileMonitorService", "Starting FileObserver on: $folderPath")

        // Stop existing observer if any
        fileObserver?.stopWatching()

        // Create new observer
        fileObserver =
                object : FileObserver(folderPath, CREATE or MODIFY or MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == null) return

                        // Ignore dot files or temp files if needed
                        if (path.startsWith(".")) return

                        DebugLogger.i("FileMonitorService", "File event ($event) on: $path")
                        SyncStatusManager.notifyFileChanged()
                        scheduleSync()
                    }
                }

        fileObserver?.startWatching()
    }

    private fun scheduleSync() {
        // Cancel pending sync
        syncJob?.cancel()

        // Schedule new sync with debounce
        syncJob =
                scope.launch {
                    try {
                        DebugLogger.i(
                                "FileMonitorService",
                                "Sync scheduled in ${SYNC_DEBOUNCE_MS}ms"
                        )
                        delay(SYNC_DEBOUNCE_MS)

                        DebugLogger.i("FileMonitorService", "Triggering sync now")
                        SyncStatusManager.notifySyncStarted()

                        val prefs =
                                getSharedPreferences(
                                        NativeSyncConfig.PREFS_NAME,
                                        Context.MODE_PRIVATE
                                )
                        val folderPath =
                                prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)

                        if (folderPath != null) {
                            val syncService = SyncService(applicationContext)
                            syncService.performSync(
                                    folderPath = folderPath,
                                    startBackgroundService = false, // We ARE the background service
                                    onProgress = { uploaded, total ->
                                        SyncStatusManager.notifySyncProgress(uploaded, total)
                                    }
                            )
                        }
                        SyncStatusManager.notifySyncFinished()
                    } catch (e: Exception) {
                        DebugLogger.e("FileMonitorService", "Error during sync trigger", e)
                        SyncStatusManager.notifySyncFinished() // Ensure UI resets
                    }
                }
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.i("FileMonitorService", "Service destroying")
        fileObserver?.stopWatching()
        syncJob?.cancel()
    }

    private fun createNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, NativeSyncConfig.NOTIFICATION_CHANNEL_ID)
                .setContentTitle("EvenSync Monitor")
                .setContentText("Watching for new files...")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    NativeSyncConfig.NOTIFICATION_CHANNEL_ID,
                                    NativeSyncConfig.NOTIFICATION_CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Notification for file monitoring"
                                setShowBadge(false)
                            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

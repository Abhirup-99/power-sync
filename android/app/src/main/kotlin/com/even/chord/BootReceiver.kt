package com.even.chord

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver that restarts WorkManager sync after boot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val WORK_NAME = "sync_work"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        DebugLogger.initialize(context)
        
        val action = intent.action
        DebugLogger.i("BootReceiver", "Received action: $action")
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DebugLogger.i("BootReceiver", "System event detected: $action - checking sync status")
                restartWorkManagerIfNeeded(context)
            }
        }
    }
    
    private fun restartWorkManagerIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences(NativeSyncConfig.FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
            val syncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
            
            if (syncActive) {
                DebugLogger.i("BootReceiver", "sync_active=true, starting WorkManager")
                
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                
                val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                    NativeSyncConfig.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                
                DebugLogger.i("BootReceiver", "WorkManager sync scheduled")
            } else {
                DebugLogger.i("BootReceiver", "sync_active=false, not starting WorkManager")
            }
        } catch (e: Exception) {
            DebugLogger.e("BootReceiver", "Error in restartWorkManagerIfNeeded", e)
        }
    }
}

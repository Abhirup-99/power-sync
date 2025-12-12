package com.even.chord

import android.content.Context
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

class MainActivity: FlutterActivity() {
    private val DEBUG_CHANNEL = "com.even.chord/debug_log"
    private val SYNC_CHANNEL = "com.even.chord/sync"
    private val WORK_NAME = "sync_work"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize debug logger
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("MainActivity", "onCreate called")
    }
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Set up debug logging channel
        val debugChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DEBUG_CHANNEL)
        
        debugChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "enableLogging" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    DebugLogger.setEnabled(enabled, applicationContext)
                    result.success(true)
                }
                "getKotlinLogs" -> {
                    result.success(DebugLogger.getLogContents())
                }
                "clearKotlinLogs" -> {
                    DebugLogger.clearLogs()
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
        
        // Sync control channel - WorkManager only
        val syncChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SYNC_CHANNEL)
        
        syncChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startSync" -> {
                    try {
                        // Set sync_active flag
                        val prefs = getSharedPreferences(NativeSyncConfig.FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, true).apply()
                        
                        // Start WorkManager periodic sync
                        startWorkManager()
                        
                        DebugLogger.i("MainActivity", "WorkManager sync started")
                        result.success(true)
                    } catch (e: Exception) {
                        DebugLogger.e("MainActivity", "Failed to start sync", e)
                        result.error("START_FAILED", e.message, null)
                    }
                }
                "stopSync" -> {
                    try {
                        // Clear sync_active flag
                        val prefs = getSharedPreferences(NativeSyncConfig.FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false).apply()
                        
                        // Stop WorkManager
                        stopWorkManager()
                        
                        DebugLogger.i("MainActivity", "WorkManager sync stopped")
                        result.success(true)
                    } catch (e: Exception) {
                        DebugLogger.e("MainActivity", "Failed to stop sync", e)
                        result.error("STOP_FAILED", e.message, null)
                    }
                }
                "isSyncActive" -> {
                    val prefs = getSharedPreferences(NativeSyncConfig.FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
                    val syncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
                    result.success(syncActive)
                }
                "getSyncStatus" -> {
                    val prefs = getSharedPreferences(NativeSyncConfig.FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
                    val status = mapOf(
                        "syncActive" to prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false),
                        "lastSyncTime" to prefs.getString(NativeSyncConfig.KEY_LAST_SYNC_TIME, null)
                    )
                    result.success(status)
                }
                "triggerImmediateSync" -> {
                    try {
                        // Enqueue a one-time work request for immediate sync
                        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()
                            )
                            .build()
                        
                        WorkManager.getInstance(applicationContext).enqueue(oneTimeRequest)
                        DebugLogger.i("MainActivity", "Immediate sync triggered")
                        result.success(true)
                    } catch (e: Exception) {
                        DebugLogger.e("MainActivity", "Failed to trigger immediate sync", e)
                        result.error("SYNC_FAILED", e.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
    
    private fun startWorkManager() {
        try {
            // Constraints for the sync work
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            // Use configured interval for syncs
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                NativeSyncConfig.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            DebugLogger.i("MainActivity", "WorkManager sync scheduled (every ${NativeSyncConfig.SYNC_INTERVAL_MINUTES} min)")
        } catch (e: Exception) {
            DebugLogger.e("MainActivity", "Failed to start WorkManager", e)
        }
    }
    
    private fun stopWorkManager() {
        try {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME)
            DebugLogger.i("MainActivity", "WorkManager sync cancelled")
        } catch (e: Exception) {
            DebugLogger.e("MainActivity", "Failed to stop WorkManager", e)
        }
    }
    
    override fun onDestroy() {
        DebugLogger.i("MainActivity", "onDestroy called")
        super.onDestroy()
    }
}

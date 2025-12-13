package com.even.chord

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType

/**
 * BroadcastReceiver that triggers a sync after a phone call ends.
 * This ensures recordings are synced immediately after they're created,
 * even if WorkManager periodic jobs are deferred by Doze mode.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val SYNC_DELAY_MS = 5000L // 5 second delay to let recording file be written
        private const val PREFS_NAME = "PhoneStateReceiverPrefs"
        private const val KEY_LAST_STATE = "last_phone_state"
        
        @Volatile
        private var lastStateCache = TelephonyManager.CALL_STATE_IDLE
        private val lock = Any()
        
        private fun getLastState(context: Context): Int {
            synchronized(lock) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                lastStateCache = prefs.getInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_IDLE)
                return lastStateCache
            }
        }
        
        private fun setLastState(context: Context, state: Int) {
            synchronized(lock) {
                lastStateCache = state
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_LAST_STATE, state)
                    .apply()
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        
        DebugLogger.initialize(context)
        
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> return
        }
        
        val previousState = getLastState(context)
        DebugLogger.i("PhoneStateReceiver", "Phone state changed: $stateStr (was: $previousState)")
        
        // Detect call ended: was in call (OFFHOOK) -> now idle
        if (previousState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE) {
            DebugLogger.i("PhoneStateReceiver", "Call ended - triggering sync")
            triggerSync(context)
        }
        
        setLastState(context, state)
    }
    
    private fun triggerSync(context: Context) {
        try {
            // Check if sync is active
            val prefs = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val syncActive = prefs.getBoolean(NativeSyncConfig.KEY_SYNC_ACTIVE, false)
            
            if (!syncActive) {
                DebugLogger.i("PhoneStateReceiver", "Sync not active, skipping")
                return
            }
            
            // Enqueue a one-time sync with a small delay to let the recording file be written
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(SYNC_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("post_call_sync")
                .build()
            
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            DebugLogger.i("PhoneStateReceiver", "Post-call sync scheduled (5s delay)")
        } catch (e: Exception) {
            DebugLogger.e("PhoneStateReceiver", "Failed to trigger sync", e)
        }
    }
}

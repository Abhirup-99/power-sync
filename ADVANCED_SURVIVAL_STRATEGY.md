# Advanced Background Service Survival Strategy

## Problem Solved
Your app was getting killed overnight during deep sleep despite foreground service optimizations. This implementation adds **multiple layers of redundancy** to keep the service alive.

---

## Architecture Overview

### Multi-Layer Survival System

```
┌─────────────────────────────────────────────────────────┐
│                  PRIMARY LAYER                          │
│         Foreground Service (flutter_foreground_task)    │
│              Running every 5 minutes                    │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  BACKUP LAYER 1                         │
│          WorkManager (ServiceMonitorWorker)             │
│    Checks service health every 15 minutes               │
│    Restarts if dead - survives app termination         │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  BACKUP LAYER 2                         │
│      AlarmManager (ServiceHeartbeatReceiver)            │
│    Wakes device from deep sleep every 15 minutes       │
│    Most aggressive - bypasses Doze mode                │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  BACKUP LAYER 3                         │
│           System Event Receivers                        │
│  Boot • Screen On/Off • Connectivity Change             │
│  Restarts service on any system event                   │
└─────────────────────────────────────────────────────────┘
```

---

## What Was Added

### 1. **BootReceiver.kt** - Custom BroadcastReceiver
- Listens to: BOOT_COMPLETED, SCREEN_ON, USER_PRESENT, CONNECTIVITY_CHANGE, etc.
- Checks SharedPreferences to see if service should be running
- Automatically restarts service if it's dead but should be alive

### 2. **ServiceMonitorWorker.kt** - WorkManager Background Task
- Runs every 15 minutes (even when app is closed)
- Checks if foreground service is alive
- Launches app to restart service if dead
- Survives app force-stop better than foreground services

### 3. **ServiceHeartbeatReceiver.kt** - AlarmManager Watchdog
- **Most aggressive survival mechanism**
- Uses `setExactAndAllowWhileIdle()` to wake from deep sleep
- Runs every 15 minutes
- Tracks service death count and timestamps
- Wakes device even in Doze mode

### 4. **Enhanced MainActivity.kt**
- Method channels to start/stop WorkManager and AlarmManager
- `onTaskRemoved()` - Restarts service when task is swiped away
- Handles "restart_service" intent from receivers
- Properly manages backup systems lifecycle

### 5. **Enhanced foreground_sync_service.dart**
- Starts backup systems (WorkManager + AlarmManager) when service starts
- Stops backup systems when service stops
- Method channel integration for native control

---

## How It Works

### Normal Operation
1. Foreground service runs normally every 5 minutes
2. WorkManager checks health every 15 minutes (redundancy)
3. AlarmManager wakes device every 15 minutes (deep redundancy)

### When Service Dies
1. AlarmManager wakes device at next 15-minute interval
2. Detects service is dead
3. Launches MainActivity with `restart_service=true`
4. MainActivity signals Flutter to restart service
5. Service comes back to life

### When App Force-Stopped
1. WorkManager still runs (survives force-stop better)
2. WorkManager detects service is dead
3. Launches app to restart service
4. If WorkManager also killed → System events (boot, screen on) will trigger restart

---

## Testing Instructions

### 1. Build and Install
```bash
cd /home/abhirup/Documents/chord
./build.sh prod
adb install -r build/app/outputs/flutter-apk/app-prod-release.apk
```

### 2. Start Service
- Open app
- Ensure sync is enabled
- Check notification appears

### 3. Test Service Survival
```bash
# Test 1: Kill foreground service
adb shell am force-stop com.even.chord
# Wait 1 minute - app should auto-restart

# Test 2: Check WorkManager
adb shell dumpsys jobscheduler | grep -A 20 com.even.chord

# Test 3: Check AlarmManager
adb shell dumpsys alarm | grep -A 10 ServiceHeartbeat

# Test 4: Simulate overnight
# Leave phone idle for 2-3 hours with screen off
# Service should survive

# Test 5: Check logs
adb logcat | grep -E "ServiceHeartbeat|ServiceMonitor|BootReceiver|MainActivity"
```

### 4. Monitor Service Health
```bash
# Check if service is running
adb shell dumpsys activity services com.even.chord

# Check WorkManager status
adb shell dumpsys jobscheduler | grep ServiceMonitor

# Check alarm status
adb shell dumpsys alarm | grep ServiceHeartbeat

# View service death count
adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_death
```

---

## Key Improvements Over Previous Implementation

| Feature | Before | After |
|---------|--------|-------|
| Foreground Service | ✅ Yes | ✅ Yes |
| WorkManager Backup | ❌ No | ✅ Yes |
| AlarmManager Watchdog | ❌ No | ✅ Yes |
| System Event Receivers | ⚠️ Basic | ✅ Advanced |
| Deep Sleep Wake | ❌ No | ✅ Yes (AlarmManager) |
| Force-Stop Recovery | ⚠️ Limited | ✅ Multiple layers |
| Death Tracking | ❌ No | ✅ Yes (counts + timestamps) |

---

## How Each Layer Handles Android Restrictions

### Foreground Service
- **Strength**: Highest priority, visible to user
- **Weakness**: Can still be killed in extreme low memory or aggressive OEMs
- **Solution**: Backup layers restart it

### WorkManager
- **Strength**: Survives app termination, managed by system JobScheduler
- **Weakness**: Can be delayed in Doze mode (up to several hours)
- **Solution**: AlarmManager provides exact timing

### AlarmManager
- **Strength**: Can wake from deep sleep, exact timing, bypasses Doze
- **Weakness**: Requires SCHEDULE_EXACT_ALARM permission (Android 12+)
- **Solution**: Already have permission in manifest

### System Event Receivers
- **Strength**: Triggers on many system events
- **Weakness**: Some events throttled on Android 8+
- **Solution**: Multiple event types increase trigger chances

---

## Battery Impact

### Realistic Impact
- **Foreground Service**: ~2-4% per day (already running)
- **WorkManager**: <1% per day (efficient background check)
- **AlarmManager**: ~1-2% per day (wakes device briefly)
- **System Receivers**: Negligible (only triggers on events)

**Total estimated increase**: ~1-3% additional battery drain

**Trade-off**: Service reliability vs minimal battery cost

---

## User Settings Still Required

Even with all these improvements, users MUST disable battery optimization:

### Critical Settings
```
Settings → Apps → Chord → Battery → Unrestricted
Settings → Apps → Chord → Battery optimization → Not optimized
```

### Manufacturer-Specific
- **Samsung**: Add to "Never sleeping apps"
- **Xiaomi**: Disable "MIUI optimization", enable "Autostart"
- **Huawei**: Enable all Launch options
- **OnePlus**: Disable advanced battery optimization

---

## Debugging Service Deaths

### Check Death Count
```bash
adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_death_count
```

### Check Last Death Time
```bash
adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_death_time
```

### Check Last Alive Timestamp
```bash
adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_last_alive
```

### Real-time Monitoring
```bash
# Watch all service-related logs
adb logcat -c && adb logcat | grep -E "ServiceHeartbeat|ServiceMonitor|BootReceiver|SyncTaskHandler|MainActivity"
```

---

## Expected Behavior

### Healthy Service
```
11:00 - Service running
11:05 - Sync performed
11:10 - Sync performed
11:15 - WorkManager: Service alive ✓
        AlarmManager: Service alive ✓
11:20 - Sync performed
```

### Service Death → Recovery
```
11:00 - Service running
11:05 - Sync performed
11:10 - ❌ SERVICE KILLED BY SYSTEM
11:12 - User unlocks screen → BootReceiver triggers
11:12 - ✅ Service restarted
11:15 - WorkManager: Service alive ✓
        AlarmManager: Service alive ✓
```

---

## If Service STILL Gets Killed

If after all these changes the service dies overnight:

### 1. Check Manufacturer Settings
- Most likely cause: Battery optimization not fully disabled
- Check manufacturer-specific settings in BACKGROUND_SERVICE_SETUP.md

### 2. Check Logs
```bash
adb logcat -d | grep -E "killed|death|LOW_MEMORY|OOM" > service_death_log.txt
```

### 3. Verify Backup Systems
```bash
# Check if WorkManager is scheduled
adb shell dumpsys jobscheduler | grep ServiceMonitor

# Check if AlarmManager is scheduled
adb shell dumpsys alarm | grep ServiceHeartbeat
```

### 4. Nuclear Option: Reduce Check Intervals
Edit `ServiceHeartbeatReceiver.kt`:
```kotlin
private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes instead of 15
```

---

## Success Metrics

After implementation, you should see:
- ✅ Service survives overnight (8+ hours)
- ✅ Notification remains visible in morning
- ✅ Files continue syncing at 5-minute intervals
- ✅ Last sync time updates throughout the night
- ✅ Service auto-restarts within 1-2 minutes if killed

---

## Next Steps

1. **Build the app**: `./build.sh prod`
2. **Install on device**: `adb install -r build/app/outputs/...`
3. **Grant permissions**: Battery optimization, notifications
4. **Test overnight**: Leave phone idle for 8+ hours
5. **Check in morning**: 
   - Is notification still there?
   - Check last sync time
   - Check service death count (should be 0 or very low)

---

## Technical Notes

### Why 3 Backup Systems?
- **WorkManager**: Best for periodic background tasks
- **AlarmManager**: Only one that truly wakes from deep sleep
- **BroadcastReceivers**: Opportunistic restarts on system events

Each has different survival characteristics. Having all 3 maximizes reliability.

### Why 15-minute intervals?
- Balance between battery life and responsiveness
- 15 minutes is reasonable for detecting deaths
- If service dies at 11:02, it's restarted by 11:15
- Your actual sync is still every 5 minutes when alive

---

## Support

If service still dies after 24 hours:
1. Collect logs: `adb logcat -d > full_log.txt`
2. Check death count and timestamps
3. Verify all battery optimization settings
4. Consider reducing check intervals to 10 minutes

# Background Service Setup Guide

## Problem: App Being Killed During Sleep

Your app was being killed after 4-5 hours when the phone entered **deep sleep/Doze mode** overnight.

## Technical Changes Made

### 1. Service Configuration
- ✅ Reduced sync interval from 15 to 5 minutes (prevents long idle periods)
- ✅ Added keepalive timer (sends heartbeat every 30 seconds)
- ✅ Made notification sticky
- ✅ Removed isolated process (runs in main process for better communication)
- ✅ Added timestamp updates to notification to show activity

### 2. Android Manifest Updates
- ✅ Added `SCHEDULE_EXACT_ALARM` permission (Android 12+)
- ✅ Added `USE_EXACT_ALARM` permission (Android 12+)
- ✅ Added `DISABLE_KEYGUARD` permission (wake from lock screen)
- ✅ Added additional boot/restart receivers
- ✅ Service configured with `android:stopWithTask="false"`

### 3. Wake Lock Configuration
- ✅ Enabled `allowWakeLock` and `allowWifiLock`
- ✅ MainActivity configured to not stop service on destroy
- ✅ Service runs in isolated process for stability

---

## CRITICAL: User Settings Required

Even with all code changes, Android manufacturers are **extremely aggressive** with battery optimization. Users MUST configure these settings:

### For All Android Devices:

#### 1. Disable Battery Optimization
```
Settings → Apps → Chord → Battery → Battery optimization → Not optimized
```

#### 2. Allow Background Activity
```
Settings → Apps → Chord → Battery → Background restriction → Unrestricted
```

#### 3. Enable Autostart
```
Settings → Apps → Chord → Advanced → Allow autostart (if available)
```

### Manufacturer-Specific Settings:

#### Samsung Devices:
```
Settings → Apps → Chord → Battery → Put app to sleep → Never sleeping apps
Settings → Battery → Background usage limits → Never sleeping apps → Add Chord
Settings → Device care → Battery → App power management → Turn OFF adaptive power saving
```

#### Xiaomi/MIUI:
```
Settings → Apps → Manage apps → Chord → Battery saver → No restrictions
Settings → Apps → Manage apps → Chord → Autostart → Enable
Settings → Battery & performance → Manage apps battery usage → Chord → No restrictions
Developer Options → Disable MIUI optimization (if available)
```

#### Huawei/EMUI:
```
Settings → Apps → Chord → Battery → Launch → Manual management
- Enable Auto-launch
- Enable Secondary launch
- Enable Run in background
Phone Manager → Protected apps → Enable Chord
```

#### OnePlus/OxygenOS:
```
Settings → Apps → Chord → Battery → Battery optimization → Don't optimize
Settings → Battery → Battery optimization → Advanced → Chord → Don't optimize
```

#### Oppo/ColorOS:
```
Settings → Apps → Chord → App battery usage → Background freeze → Never
Settings → Battery → High background power consumption → Chord → Allow
```

#### Vivo/FunTouch:
```
Settings → Battery → High background power consumption → Chord → Allow
Settings → Apps → Chord → Battery → Background run → Allow
```

---

## Testing the Fix

### 1. Start the App
- Grant all permissions (especially "Ignore battery optimizations")
- Start the sync service
- You should see notification: "EvenSync Active - Last check: HH:MM"

### 2. Observe Notification
- The timestamp should update every 5 minutes
- This proves the service is running

### 3. Leave Phone Overnight
- Plug in or leave phone on battery
- Lock the screen
- Don't use the phone for 6-8 hours

### 4. Check in the Morning
- Look at the notification timestamp
- It should show a recent time (within 5 minutes)
- Check app logs/sync history

---

## Debugging Service Issues

### Check if Service is Running:
```dart
final syncService = ForegroundSyncService();
final isRunning = await syncService.isRunning();
print('Service running: $isRunning');
```

### Check Last Sync Time:
```dart
final lastSync = await syncService.getLastSyncTime();
print('Last sync: $lastSync');
```

### View Android Logs (ADB):
```bash
adb logcat | grep -i "flutter\|foreground\|evensync"
```

### Check Battery Stats:
```bash
adb shell dumpsys batterystats | grep -i chord
```

---

## Why Services Get Killed

Android uses several mechanisms to kill background services:

1. **Doze Mode** - Aggressive battery saving after phone is idle for 30+ minutes
2. **App Standby** - Apps unused for days are restricted
3. **Background Execution Limits** - Android 8.0+ restricts background services
4. **Manufacturer Battery Savers** - OEMs add custom battery optimizations
5. **Low Memory Killer** - System kills services when memory is low

Our fixes address **all** of these except manufacturer-specific optimizations, which require manual user configuration.

---

## Additional Recommendations

### For Development/Testing:
```bash
# Disable doze mode (testing only)
adb shell dumpsys deviceidle disable

# Check if app is in doze whitelist
adb shell dumpsys deviceidle whitelist

# Force idle mode (testing)
adb shell dumpsys deviceidle force-idle
```

### For Production:
- Educate users about battery optimization settings
- Show in-app warnings if battery optimization is enabled
- Provide deep links to battery settings
- Show notification importance in onboarding

---

## Known Limitations

Even with all these fixes, some scenarios may still kill the service:

- **Extreme battery saver mode** - User-enabled emergency mode
- **Force stop** - User manually force stops the app
- **System updates** - Device restarts during updates
- **Task killers** - Third-party task killer apps
- **Very low memory** - System prioritizes user-facing apps

For mission-critical syncing, consider:
- Implementing WorkManager as backup (for less frequent syncs)
- Cloud-based scheduling (server initiates sync checks)
- Periodic app launch reminders to user

---

## Success Metrics

The service is working correctly if:
- ✅ Notification timestamp updates every 5 minutes
- ✅ Service survives overnight (8+ hours)
- ✅ Sync happens even when phone is locked
- ✅ Service auto-restarts after phone reboot
- ✅ Files are synced within 5 minutes of creation

---

## Support Resources

- Don't Kill My App: https://dontkillmyapp.com/
- Android Battery Optimization: https://developer.android.com/training/monitoring-device-state/doze-standby
- Foreground Services: https://developer.android.com/develop/background-work/services/foreground-services

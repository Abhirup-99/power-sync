# Quick Test Guide

## Install & Setup (5 minutes)

```bash
# 1. Install the app
adb install -r build/app/outputs/flutter-apk/app-prod-release.apk

# 2. Open app and complete onboarding

# 3. Disable battery optimization (CRITICAL!)
# Settings → Apps → Chord → Battery → Unrestricted
# Settings → Apps → Chord → Battery optimization → Not optimized
```

---

## Verify Backup Systems Are Running (2 minutes)

```bash
# Check WorkManager is scheduled
adb shell dumpsys jobscheduler | grep -B 2 -A 15 "com.even.chord"

# Check AlarmManager is scheduled  
adb shell dumpsys alarm | grep -B 2 -A 10 "ServiceHeartbeat"

# Both should show active schedules
```

---

## Test Service Survival (10 minutes)

```bash
# Test 1: Force stop app
adb shell am force-stop com.even.chord
# Wait 1-2 minutes, check if notification reappears

# Test 2: Watch logs for auto-restart
adb logcat -c && adb logcat | grep -E "BootReceiver|ServiceMonitor|ServiceHeartbeat|MainActivity"
# Should see restart activity within 1-2 minutes

# Test 3: Simulate screen unlock
adb shell input keyevent 82  # Unlock
# Should trigger BootReceiver if service was dead
```

---

## Overnight Test (8+ hours)

```bash
# Before bed:
1. Verify sync service is running (notification visible)
2. Note current time and last sync time
3. Ensure battery optimization is disabled
4. Put phone down, don't use it

# In morning:
1. Check if notification is still visible ✓
2. Check last sync time (should be recent) ✓
3. Check service death count:
   adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_death
```

---

## Expected Results

### ✅ Success:
- Notification still visible after 8+ hours
- Last sync time is within last 5-10 minutes
- Service death count = 0 or 1-2 max
- Files synced throughout the night

### ❌ Failure:
- Notification gone
- Service death count > 5
- No syncs after midnight

### 🔧 If Failed:
1. Double-check battery optimization is FULLY disabled
2. Check manufacturer-specific settings (BACKGROUND_SERVICE_SETUP.md)
3. Reduce check interval to 10 minutes
4. Collect logs: `adb logcat -d > overnight_logs.txt`

---

## Quick Debugging Commands

```bash
# Is service currently running?
adb shell dumpsys activity services com.even.chord | grep -A 10 ForegroundService

# How many times has service died?
adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_death_count

# When was last death?
adb shell run-as com.even.chord cat /data/data/com.even.chord/shared_prefs/FlutterSharedPreferences.xml | grep service_death_time

# View recent restarts
adb logcat -d | grep -E "restart|Health check|Heartbeat check" | tail -20

# Full service logs
adb logcat -d | grep -E "SyncTaskHandler|ServiceMonitor|ServiceHeartbeat|BootReceiver" > service_logs.txt
```

---

## One-Line Test Command

```bash
# Complete test suite
adb shell am force-stop com.even.chord && sleep 120 && adb shell dumpsys activity services com.even.chord | grep -A 5 "ForegroundService"
# Service should be running again after 2 minutes
```

---

## Battery Settings - Critical Checklist

- [ ] Settings → Apps → Chord → Battery → **Unrestricted**
- [ ] Settings → Apps → Chord → Battery optimization → **Not optimized**
- [ ] Settings → Apps → Chord → Permissions → **All granted**
- [ ] (Samsung) Never sleeping apps → **Chord added**
- [ ] (Xiaomi) Autostart → **Enabled for Chord**
- [ ] (Huawei) Launch → **Manual, all options enabled**

---

## Success = Service Never Dies

The goal is simple: Leave phone idle overnight, service keeps running.

If it survives 8 hours → ✅ Success!
If it dies → Check battery optimization and manufacturer settings

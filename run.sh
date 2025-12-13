#!/usr/bin/env bash
set -euo pipefail

# Usage: ./run.sh [staging|prod] [debug|release]
# Optional: ANDROID_SERIAL=<serial> ./run.sh staging debug

ENVIRONMENT=${1:-staging}
BUILD_MODE=${2:-debug}

cd "$(dirname "$0")/android"

# Validate environment
case "$ENVIRONMENT" in
  staging|prod) FLAVOR="$ENVIRONMENT" ;;
  *) echo "❌ Error: Invalid environment. Use: staging or prod"; exit 1 ;;
esac

# App id per flavor
BASE_APP_ID="com.even.chord"
APP_ID="$BASE_APP_ID"
if [[ "$FLAVOR" == "staging" ]]; then
  APP_ID="${BASE_APP_ID}.staging"
fi

# Gradle task per build mode
case "$BUILD_MODE" in
  debug)   GRADLE_TASK="install${FLAVOR^}Debug" ;;
  release) GRADLE_TASK="install${FLAVOR^}Release" ;;
  *) echo "❌ Error: Invalid build mode. Use: debug or release"; exit 1 ;;
esac

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    🚀 Chord Run                            ║"
echo "╠════════════════════════════════════════════════════════════╣"
echo "║  Environment:  $ENVIRONMENT"
echo "║  Build Mode:   $BUILD_MODE"
echo "║  App ID:       $APP_ID"
echo "║  Gradle Task:  $GRADLE_TASK"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

ADB="adb"
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB="adb -s ${ANDROID_SERIAL}"
fi

# Find a usable device if ANDROID_SERIAL not set
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  # Pick first device in "device" state
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [[ -z "$SERIAL" ]]; then
    echo "❌ Error: No online devices found."
    echo "   Tip: run 'adb devices' and ensure state is 'device' (not unauthorized/offline)."
    exit 1
  fi
  ANDROID_SERIAL="$SERIAL"
  ADB="adb -s ${ANDROID_SERIAL}"
fi

echo "📱 Using device: $ANDROID_SERIAL"
echo ""

echo "⏳ Waiting for device..."
$ADB wait-for-device

# Quick sanity check for authorization/offline
STATE="$($ADB get-state || true)"
if [[ "$STATE" != "device" ]]; then
  echo "❌ Error: Device state is '$STATE' (expected 'device')."
  echo "   Fix: unlock phone, accept USB debugging prompt, or reconnect cable."
  exit 1
fi

echo "🔨 Building and installing..."
./gradlew "$GRADLE_TASK"

echo ""
echo "🚀 Launching app..."

# Robust launch (avoids hardcoding MainActivity)
# Monkey sends a single launch intent to the main activity.
$ADB shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || {
  echo "⚠️  Monkey launch failed, trying explicit activity..."
  $ADB shell am start -n "${APP_ID}/.ui.MainActivity" >/dev/null
}

echo "✅ App launched!"
echo "📋 Preparing logs..."

# Clear logcat so we only see fresh logs for this run
$ADB logcat -c

# Resolve PID (best-effort)
PID=""
for i in {1..50}; do
  PID="$($ADB shell pidof -s "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  [[ -n "$PID" ]] && break
  sleep 0.1
done

if [[ -n "$PID" ]]; then
  echo "📋 Tailing logs for PID: $PID ($APP_ID)"
  # Some environments don't support --pid; fallback if it errors.
  if ! $ADB logcat -v color --pid="$PID"; then
    echo "⚠️  --pid not supported. Falling back to grep for $APP_ID..."
    $ADB logcat -v color | grep --line-buffered "$APP_ID"
  fi
else
  echo "⚠️  Could not resolve PID. Grepping logs for $APP_ID..."
  $ADB logcat -v color | grep --line-buffered "$APP_ID"
fi

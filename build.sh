#!/bin/bash

# Build script for Chord Android app with different environments
# Usage: ./build.sh [staging|prod] [debug|release|bundle|lint]

set -e

ENVIRONMENT=${1:-staging}
BUILD_MODE=${2:-debug}



# Validate environment
case $ENVIRONMENT in
  staging|prod)
    FLAVOR="$ENVIRONMENT"
    ;;
  *)
    echo "❌ Error: Invalid environment. Use: staging or prod"
    exit 1
    ;;
esac

# Validate build mode and determine Gradle task
case $BUILD_MODE in
  debug)
    BUILD_VARIANT="${FLAVOR}Debug"
    GRADLE_TASK="assemble${FLAVOR^}Debug"
    APK_PATH="app/build/outputs/apk/${FLAVOR}/debug/app-${FLAVOR}-debug.apk"
    ;;
  release)
    BUILD_VARIANT="${FLAVOR}Release"
    GRADLE_TASK="assemble${FLAVOR^}Release"
    APK_PATH="app/build/outputs/apk/${FLAVOR}/release/app-${FLAVOR}-release.apk"
    ;;
  bundle)
    BUILD_VARIANT="${FLAVOR}Release"
    GRADLE_TASK=":app:bundle${FLAVOR^}Release"
    AAB_PATH="app/build/outputs/bundle/${FLAVOR}Release/app-${FLAVOR}-release.aab"
    ;;
  lint)
    BUILD_VARIANT="${FLAVOR}Debug"
    GRADLE_TASK="lint${FLAVOR^}Debug"
    APK_PATH=""
    ;;
  *)
    echo "❌ Error: Invalid build mode. Use: debug, release, bundle or lint"
    exit 1
    ;;
esac

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    🔨 PowerSync Build                      ║"
echo "╠════════════════════════════════════════════════════════════╣"
echo "║  Environment:  $ENVIRONMENT"
echo "║  Build Mode:   $BUILD_MODE"
echo "║  Gradle Task:  $GRADLE_TASK"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Clean if requested
if [ "$3" == "clean" ]; then
  echo "🧹 Cleaning previous build..."
  ./gradlew clean
  echo ""
fi

# Build the app
echo "🔨 Building..."
./gradlew $GRADLE_TASK

echo ""
echo "✅ Build completed successfully!"
if [ -n "$APK_PATH" ]; then
  echo "📦 APK Location: ${APK_PATH}"
fi
if [ -n "$AAB_PATH" ]; then
  echo "📦 AAB Location: ${AAB_PATH}"
fi
echo ""

#!/bin/bash

# Build script for Chord app with different environments
# Usage: ./build.sh [dev|staging|prod] [debug|release]

set -e

ENVIRONMENT=${1:-dev}
BUILD_MODE=${2:-debug}

case $ENVIRONMENT in
  dev)
    FLAVOR="dev"
    ENTRY_POINT="lib/main_dev.dart"
    ;;
  prod)
    FLAVOR="prod"
    ENTRY_POINT="lib/main_prod.dart"
    ;;
  *)
    echo "Error: Invalid environment. Use: dev, staging, or prod"
    exit 1
    ;;
esac

echo "🔨 Building Chord for $ENVIRONMENT environment in $BUILD_MODE mode..."
echo "📱 Flavor: $FLAVOR"
echo "🎯 Entry point: $ENTRY_POINT"
echo ""

if [ "$BUILD_MODE" == "release" ]; then
  flutter build apk --release --flavor $FLAVOR -t $ENTRY_POINT
  echo ""
  echo "✅ Release APK built successfully!"
  echo "📦 Location: build/app/outputs/flutter-apk/app-${FLAVOR}-release.apk"
elif [ "$BUILD_MODE" == "debug" ]; then
  flutter build apk --debug --flavor $FLAVOR -t $ENTRY_POINT
  echo ""
  echo "✅ Debug APK built successfully!"
  echo "📦 Location: build/app/outputs/flutter-apk/app-${FLAVOR}-debug.apk"
else
  echo "Error: Invalid build mode. Use: debug or release"
  exit 1
fi

#!/bin/bash

# Run script for Chord app with different environments
# Usage: ./run.sh [dev|staging|prod]

set -e

ENVIRONMENT=${1:-dev}

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

echo "🚀 Running Chord in $ENVIRONMENT environment..."
echo "📱 Flavor: $FLAVOR"
echo "🎯 Entry point: $ENTRY_POINT"
echo ""

flutter run --flavor $FLAVOR -t $ENTRY_POINT

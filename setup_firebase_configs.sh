#!/bin/bash

# Script to organize existing Firebase config files
# This moves your current google-services.json to the dev folder

echo "🔧 Organizing Firebase configuration files..."
echo ""

# Check if the original google-services.json exists
if [ -f "android/app/google-services.json" ]; then
  echo "📁 Found existing google-services.json"
  
  # Create dev directory if it doesn't exist
  mkdir -p android/app/src/dev
  
  # Copy to dev folder
  cp android/app/google-services.json android/app/src/dev/google-services.json
  
  echo "✅ Copied to android/app/src/dev/google-services.json"
  echo ""
  echo "⚠️  Note: The original file at android/app/google-services.json will be ignored by git"
  echo "   You can keep it there for backward compatibility, or delete it."
  echo ""
else
  echo "❌ No google-services.json found at android/app/google-services.json"
  echo ""
fi

echo "📋 Current status:"
echo ""

if [ -f "android/app/src/dev/google-services.json" ]; then
  echo "✅ Dev:     android/app/src/dev/google-services.json"
else
  echo "❌ Dev:     Missing - Add your dev Firebase config here"
fi

if [ -f "android/app/src/staging/google-services.json" ]; then
  echo "✅ Staging: android/app/src/staging/google-services.json"
else
  echo "❌ Staging: Missing - Add your staging Firebase config here"
fi

if [ -f "android/app/src/prod/google-services.json" ]; then
  echo "✅ Prod:    android/app/src/prod/google-services.json"
else
  echo "❌ Prod:    Missing - Add your production Firebase config here"
fi

echo ""
echo "📖 See FIREBASE_SETUP.md for detailed instructions on creating Firebase projects"

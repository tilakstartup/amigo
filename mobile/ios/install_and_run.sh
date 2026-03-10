#!/bin/bash

# iOS Simulator Install and Run Script
# Usage: ./install_and_run.sh [device_name]

set -e

# Configuration
DEVICE_NAME="${1:-iPhone 16 Pro}"
BUNDLE_ID="com.amigoai.ios"
SCHEME="Amigo"
WORKSPACE="Amigo.xcworkspace"

echo "🔍 Finding device UDID for: $DEVICE_NAME"
DEVICE_UDID=$(xcrun simctl list devices | grep "$DEVICE_NAME" | grep "Booted" | grep -o "[A-F0-9]\{8\}-[A-F0-9]\{4\}-[A-F0-9]\{4\}-[A-F0-9]\{4\}-[A-F0-9]\{12\}" | head -1)

if [ -z "$DEVICE_UDID" ]; then
    echo "⚠️  Device not booted, trying to find any $DEVICE_NAME..."
    DEVICE_UDID=$(xcrun simctl list devices | grep "$DEVICE_NAME" | grep -v "unavailable" | grep -o "[A-F0-9]\{8\}-[A-F0-9]\{4\}-[A-F0-9]\{4\}-[A-F0-9]\{4\}-[A-F0-9]\{12\}" | head -1)
    
    if [ -z "$DEVICE_UDID" ]; then
        echo "❌ Device '$DEVICE_NAME' not found"
        echo "Available devices:"
        xcrun simctl list devices | grep "iPhone"
        exit 1
    fi
    
    echo "🚀 Booting device..."
    xcrun simctl boot "$DEVICE_UDID" 2>/dev/null || true
    sleep 2
fi

echo "✅ Device UDID: $DEVICE_UDID"

# Find the most recent build
echo "🔍 Finding most recent build..."
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -path "*/Debug-iphonesimulator/Amigo.app" -type d 2>/dev/null | while read path; do
    echo "$(stat -f %m "$path") $path"
done | sort -rn | head -1 | cut -d' ' -f2-)

if [ -z "$APP_PATH" ]; then
    echo "❌ No build found. Building now..."
    cd "$(dirname "$0")"
    xcodebuild -workspace "$WORKSPACE" -scheme "$SCHEME" -configuration Debug -sdk iphonesimulator -destination "platform=iOS Simulator,name=$DEVICE_NAME,OS=latest" build
    
    # Try finding again
    APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -path "*/Debug-iphonesimulator/Amigo.app" -type d 2>/dev/null | while read path; do
        echo "$(stat -f %m "$path") $path"
    done | sort -rn | head -1 | cut -d' ' -f2-)
fi

echo "📦 App path: $APP_PATH"

# Check if debug dylib exists
if [ ! -f "$APP_PATH/Amigo.debug.dylib" ]; then
    echo "⚠️  Warning: Amigo.debug.dylib not found in app bundle"
    echo "📋 Contents of app bundle:"
    ls -la "$APP_PATH"
    echo ""
    echo "❌ This build is incomplete. Rebuilding..."
    cd "$(dirname "$0")"
    xcodebuild -workspace "$WORKSPACE" -scheme "$SCHEME" -configuration Debug -sdk iphonesimulator -destination "platform=iOS Simulator,name=$DEVICE_NAME,OS=latest" clean build
    
    # Find the new build
    APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -path "*/Debug-iphonesimulator/Amigo.app" -type d 2>/dev/null | while read path; do
        echo "$(stat -f %m "$path") $path"
    done | sort -rn | head -1 | cut -d' ' -f2-)
    
    if [ ! -f "$APP_PATH/Amigo.debug.dylib" ]; then
        echo "❌ Still missing Amigo.debug.dylib after rebuild"
        exit 1
    fi
fi

echo "✅ Debug dylib found"

# Uninstall old version
echo "🗑️  Uninstalling old version..."
xcrun simctl uninstall "$DEVICE_UDID" "$BUNDLE_ID" 2>/dev/null || true

# Install app
echo "📲 Installing app..."
xcrun simctl install "$DEVICE_UDID" "$APP_PATH"

echo "✅ App installed successfully"

# Launch with console output
echo "🚀 Launching app..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
xcrun simctl launch --console "$DEVICE_UDID" "$BUNDLE_ID"

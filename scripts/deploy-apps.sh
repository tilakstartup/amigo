#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== ANDROID ==="
echo "→ Uninstalling..."
adb uninstall com.amigo.android || true

echo "→ Building..."
cd "$ROOT_DIR/mobile"
./gradlew :android:assembleDebug

echo "→ Installing..."
adb install "$ROOT_DIR/mobile/android/build/outputs/apk/debug/android-debug.apk"

echo "→ Launching..."
adb shell am start -n com.amigo.android/.MainActivity

echo ""
echo "=== iOS ==="
echo "→ Uninstalling..."
xcrun simctl uninstall booted com.amigoai.ios || true

echo "→ Building..."
xcodebuild \
  -workspace "$ROOT_DIR/mobile/ios/Amigo.xcworkspace" \
  -scheme Amigo \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath "$ROOT_DIR/mobile/ios/build/DerivedData" \
  build

echo "→ Installing..."
xcrun simctl install booted "$ROOT_DIR/mobile/ios/build/DerivedData/Build/Products/Debug-iphonesimulator/Amigo.app"

echo "→ Launching..."
xcrun simctl launch booted com.amigoai.ios

echo ""
echo "✅ Both apps deployed and launched."

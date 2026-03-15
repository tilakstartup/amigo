#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
IOS_SIM_UDID="F6E3DF5B-84A7-48F4-BF80-D2E12C48E9BE"

echo "=== ANDROID ==="
echo "→ Uninstalling..."
adb uninstall com.amigo.android || true

echo "→ Building..."
cd "$ROOT_DIR/mobile"
./gradlew :android:assembleDebug

echo "→ Installing..."
adb install "$ROOT_DIR/mobile/android/build/outputs/apk/debug/android-debug.apk" || { echo "⚠️  No Android device connected, skipping install/launch"; }

echo "→ Launching..."
adb shell am start -n com.amigo.android/.MainActivity || true

echo ""
echo "=== iOS ==="
echo "→ Uninstalling..."
xcrun simctl uninstall "$IOS_SIM_UDID" com.amigoai.ios || true

echo "→ Building..."
xcodebuild \
  -workspace "$ROOT_DIR/mobile/ios/Amigo.xcworkspace" \
  -scheme Amigo \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$IOS_SIM_UDID" \
  -derivedDataPath "$ROOT_DIR/mobile/ios/build/DerivedData" \
  build

echo "→ Booting simulator..."
xcrun simctl boot "$IOS_SIM_UDID" || true

echo "→ Installing..."
xcrun simctl install "$IOS_SIM_UDID" "$ROOT_DIR/mobile/ios/build/DerivedData/Build/Products/Debug-iphonesimulator/Amigo.app"

echo "→ Launching..."
xcrun simctl launch "$IOS_SIM_UDID" com.amigoai.ios

echo ""
echo "✅ Both apps deployed and launched."

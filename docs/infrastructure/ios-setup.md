# iOS Project Setup

## Prerequisites

- macOS with Xcode 15.0 or later
- CocoaPods installed (`sudo gem install cocoapods`)
- iOS 15.0+ deployment target

## Project Structure

```
mobile/ios/
├── Amigo/
│   ├── AmigoApp.swift          # Main app entry point
│   ├── ContentView.swift       # Main content view
│   └── Info.plist              # App configuration
├── Podfile                      # CocoaPods dependencies
└── Amigo.xcodeproj/            # Xcode project (to be created)
```

## Setup Steps

### 1. Create Xcode Project

Since we cannot run Xcode commands via CLI, you need to:

1. Open Xcode
2. Create a new iOS App project:
   - Product Name: `Amigo`
   - Team: Your development team
   - Organization Identifier: `com.amigo`
   - Bundle Identifier: `com.amigo.Amigo`
   - Interface: SwiftUI
   - Language: Swift
   - Location: `mobile/ios/`

3. Replace the generated files with the ones in `mobile/ios/Amigo/`

### 2. Link KMP Framework

1. In Xcode, go to your target's Build Phases
2. Add a "New Run Script Phase" before "Compile Sources"
3. Add this script:

```bash
cd "$SRCROOT/../shared"
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

4. In Build Settings, add to "Framework Search Paths":
```
$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
```

5. In General tab, under "Frameworks, Libraries, and Embedded Content":
   - Click "+" and add `shared.framework`
   - Set "Embed" to "Embed & Sign"

### 3. Install CocoaPods

```bash
cd mobile/ios
pod install
```

### 4. Open Workspace

After pod install, always open `Amigo.xcworkspace` (not `.xcodeproj`)

## Build and Run

1. Open `mobile/ios/Amigo.xcworkspace` in Xcode
2. Select a simulator or device
3. Press Cmd+R to build and run

## Troubleshooting

### Framework Not Found

If you get "Framework not found shared":
1. Clean build folder (Cmd+Shift+K)
2. Build the shared framework first:
   ```bash
   cd mobile/shared
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
3. Rebuild the iOS app

### CocoaPods Issues

If pod install fails:
```bash
pod repo update
pod install --repo-update
```

## Next Steps

After successful build:
- Verify the app launches on simulator
- Verify the greeting message displays correctly
- Verify KMP framework integration works

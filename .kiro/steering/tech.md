# Tech Stack

## Mobile

- **Architecture**: Kotlin Multiplatform (KMP) with native UI layers
- **Shared Logic**: Kotlin 2.3.0 (business logic, networking, data models)
- **iOS UI**: Swift 5.9 + SwiftUI (iOS 16.0+)
- **Android UI**: Kotlin + Jetpack Compose (minSdk 26, compileSdk 34)
- **Build System**: Gradle 8.5 (Android/Shared), XcodeGen (iOS project generation)

## Key Libraries

- **Networking**: Ktor 3.4.0 (client, content negotiation, serialization)
- **Backend SDK**: Supabase KT 3.4.1 (postgrest, storage, realtime, auth)
- **Serialization**: kotlinx-serialization 1.6.2
- **Coroutines**: kotlinx-coroutines 1.7.3
- **DateTime**: kotlinx-datetime 0.6.1
- **Android Security**: androidx.security:security-crypto 1.1.0-alpha06

## Infrastructure

- **IaC**: AWS CDK 2.140.0 with TypeScript 5.0
- **AI**: Amazon Bedrock (Claude models)
- **Backend**: Supabase (PostgreSQL, Auth, Storage, Edge Functions)
- **Email**: SendGrid SMTP (authentication emails)

## Common Commands

### Mobile Build

```bash
# Build shared KMP framework
cd mobile
./gradlew :shared:build

# Build Android app
./gradlew :android:assembleDebug

# Build iOS framework for Xcode
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

### iOS Development

```bash
# Generate Xcode project from project.yml
cd mobile/ios
xcodegen generate

# Build and run on simulator
xcodebuild -scheme Amigo -destination 'platform=iOS Simulator,name=iPhone 16 Pro'

# Install and launch with logs
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -path "*/Debug-iphonesimulator/Amigo.app" -type d 2>/dev/null | head -1)
xcrun simctl install <DEVICE_ID> "$APP_PATH"
xcrun simctl launch --console <DEVICE_ID> com.amigoai.ios
```

### Infrastructure

```bash
cd infrastructure

# Build TypeScript
npm run build

# Deploy to environments
npm run deploy:dev
npm run deploy:staging
npm run deploy:prod

# View changes before deploy
npm run diff
npm run synth
```

### Supabase

```bash
cd supabase

# Run migrations locally
supabase db reset

# Push migrations to remote
supabase db push

# Generate TypeScript types
supabase gen types typescript --local > types.ts
```

## Code Style

- **Kotlin**: Follow Kotlin coding conventions, use coroutines for async operations
- **Swift**: SwiftUI declarative patterns, async/await for concurrency
- **TypeScript**: AWS CDK construct patterns, strong typing
- **Naming**: camelCase (Kotlin/Swift), kebab-case (file names), PascalCase (types/classes)

# iOS App Architecture

## Overview

The iOS app is built with SwiftUI and follows MVVM (Model-View-ViewModel) architecture. It consumes the shared Kotlin Multiplatform framework for all business logic.

## Architecture Pattern: MVVM

```
View (SwiftUI) ↔ ViewModel (ObservableObject) ↔ Shared Framework (KMP) ↔ Backend
```

## Module Structure

```
ios/Amigo/
├── Auth/                        # Authentication views
│   ├── AuthViewModel.swift      # Manages auth state
│   ├── LoginView.swift          # Email/password login UI
│   └── SignUpView.swift         # Registration UI
│
├── Onboarding/                  # Conversational onboarding
│   ├── ConversationalOnboardingViewModel.swift
│   ├── ConversationalOnboardingView.swift
│   ├── PermissionsView.swift
│   ├── WelcomeView.swift
│   ├── ProfileSetupView.swift
│   └── GoalSelectionView.swift
│
├── Profile/                     # Profile and goal management
│   ├── ProfileView.swift
│   ├── GoalManagementView.swift
│   └── SmartGoalPlanningView.swift
│
├── Shared/                      # Shared utilities
│   └── SecureStorage.swift      # Keychain wrapper
│
├── Resources/                   # Assets and resources
│
├── AmigoApp.swift               # App entry point
├── MainTabView.swift            # Tab navigation
├── ContentView.swift            # Root view
└── BedrockTestView.swift        # AI testing view
```

## Key Components

### AmigoApp
- **Responsibility**: App initialization, deep link handling, navigation orchestration
- **Key Features**:
  - Initializes Supabase client via `AuthFactory`
  - Creates authentication components
  - Handles OAuth deep links (`amigo://auth`)
  - Manages onboarding state per user
  - Routes between Welcome → Login → Onboarding → MainApp

### AuthViewModel
- **Conforms to**: `ObservableObject`
- **Concurrency**: Swift async/await for coroutine interop
- **State Management**: `@Published` properties for reactive UI
- **Key Responsibilities**:
  - Email/password authentication
  - OAuth (Google, Apple) authentication
  - Session management
  - Error handling and user feedback
  - Deep link session handling

### ConversationalOnboardingViewModel
- **Purpose**: Manages AI conversation state for onboarding
- **Key Features**:
  - Sends messages to Bedrock Agent
  - Handles RETURN_CONTROL function invocations
  - Executes action groups client-side
  - Collects profile data during conversation
  - Provides profile data for saving

### Navigation Flow

```
App Launch
    ↓
AmigoApp.init()
    ↓
Check hasCompletedWelcome (UserDefaults)
    ↓
    ├─ No → WelcomeView
    │           ↓
    │       Set hasCompletedWelcome = true
    │
    └─ Yes → Check isAuthenticated (from SessionManager)
                ↓
                ├─ Not Authenticated → LoginView / SignUpView
                │                          ↓
                │                      OAuth Deep Link Handler
                │                          ↓
                │                      AuthViewModel.handleDeepLinkSession()
                │
                └─ Authenticated → Check onboarding status
                                      ↓
                                      ├─ Not Complete → OnboardingCoordinator
                                      │                     ↓
                                      │                 ConversationalOnboardingView
                                      │                     ↓
                                      │                 Save profile data
                                      │                     ↓
                                      │                 Mark onboarding complete
                                      │
                                      └─ Complete → MainTabView
```

## State Management

### @Published Pattern
All ViewModels expose state via `@Published`:
```swift
@Published var email: String = ""
@Published var isAuthenticated: Bool = false
```

### SwiftUI Binding
```swift
@StateObject private var authViewModel: AuthViewModel
```

## Deep Link Handling

### OAuth Callback Flow
1. User initiates OAuth (Google/Apple)
2. Browser redirects to `amigo://auth#access_token=...&refresh_token=...`
3. `.onOpenURL` modifier captures deep link
4. Extract tokens from URL fragment
5. Call `AuthViewModel.handleDeepLinkSession()`
6. `SessionManager.setSessionFromTokens()` saves to Keychain
7. UI updates via `@Published isAuthenticated`

## Kotlin-Swift Interop

### Async/Await Bridge
Swift async/await seamlessly calls Kotlin suspend functions:
```swift
// Kotlin: suspend fun signIn(email: String, password: String): AuthResult
// Swift:
let result = try await emailAuthenticator.signIn(email: email, password: password)
```

### Flow Collection
Kotlin `StateFlow` observed in Swift:
```swift
Task {
    for await value in sessionManager.isAuthenticated {
        await MainActor.run {
            isAuthenticated = value as! Bool
        }
    }
}
```

### Sealed Class Handling
Kotlin sealed classes mapped to Swift protocols:
```swift
if let success = result as? AuthResult.Success {
    // Handle success
} else if let error = result as? AuthResult.Error {
    // Handle error
}
```

## Platform-Specific Features

### Secure Storage (Keychain)
- Uses iOS Keychain Services
- Stores access/refresh tokens securely
- Automatic encryption by OS

### Permissions
- HealthKit permissions (future)
- Camera permissions for meal logging (future)
- Notification permissions for reminders (future)

### Sign in with Apple
- Native Apple ID authentication
- Handled by `OAuthAuthenticator` in shared module

## Testing Strategy

### Unit Tests
- ViewModel logic testing
- State transformation testing
- Mock shared framework dependencies

### UI Tests
- SwiftUI view testing
- Navigation flow testing
- User interaction testing

### Integration Tests
- End-to-end authentication flows
- Onboarding completion flow
- Deep link handling

## Performance Considerations

### Lazy Initialization
- ViewModels created with `@StateObject` only when needed
- Shared framework singletons prevent duplicate instances

### Task Management
- Use `Task` for async operations
- Automatic cancellation on view disappear
- `@MainActor` for UI updates

### SwiftUI Optimization
- Use `@State` for local state
- `@StateObject` for ViewModels
- `@ObservedObject` for passed ViewModels
- Minimize view body complexity

## Build Configuration

### XcodeGen (project.yml)
- Generates Xcode project from YAML
- Simplifies project file management
- Easier merge conflict resolution

### Build Settings
- **Deployment Target**: iOS 16.0+
- **Swift Version**: 5.9
- **Framework**: Shared KMP framework (embedded)

### Dependencies
- Shared KMP framework (Gradle build)
- No external Swift packages currently

## Project Generation

### XcodeGen Workflow
```bash
cd mobile/ios
xcodegen generate
```

### Framework Integration
```bash
cd mobile
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

## Known Issues & TODOs

1. **Onboarding State**: Currently stored in UserDefaults, should sync with backend
2. **Error Handling**: Could be more granular with specific error types
3. **Testing Coverage**: Need more comprehensive test suite
4. **User ID Resolution**: Complex JWT parsing for onboarding status check

## Future Enhancements

1. **Session Directory**: Add session initialization views (LoadingView, ErrorView, SessionCoordinatorView)
2. **Offline Support**: Add local database with CoreData or Realm
3. **Push Notifications**: Implement APNs for reminders
4. **HealthKit**: Integrate with Apple HealthKit
5. **Camera Integration**: Add AVFoundation for meal photo capture
6. **Biometric Auth**: Add Face ID/Touch ID unlock
7. **Widgets**: Add iOS widgets for quick tracking

## SwiftUI Best Practices

### View Composition
- Break down complex views into smaller components
- Use `@ViewBuilder` for conditional content
- Prefer composition over inheritance

### State Management
- Single source of truth principle
- Unidirectional data flow
- Minimize state duplication

### Performance
- Use `LazyVStack`/`LazyHStack` for long lists
- Avoid expensive operations in view body
- Use `task` modifier for async operations

## Debugging Tips

### Kotlin Framework Issues
- Check framework is embedded in Xcode build phases
- Verify framework search paths
- Clean build folder if framework changes not reflected

### Deep Link Testing
```bash
xcrun simctl openurl booted "amigo://auth#access_token=test&refresh_token=test"
```

### Logging
- Use `NSLog` for Kotlin-visible logs
- Use `print` for Swift-only logs
- Check Console.app for device logs

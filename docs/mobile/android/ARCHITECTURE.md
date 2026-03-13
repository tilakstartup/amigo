# Android App Architecture

## Overview

The Android app is built with Jetpack Compose and follows MVVM (Model-View-ViewModel) architecture. It consumes the shared Kotlin Multiplatform module for all business logic.

## Architecture Pattern: MVVM

```
View (Composable) ↔ ViewModel ↔ Shared Module (Business Logic) ↔ Backend
```

## Module Structure

```
android/src/main/java/com/amigo/android/
├── auth/                        # Authentication screens
│   ├── AuthViewModel.kt         # Manages auth state
│   ├── LoginScreen.kt           # Email/password login UI
│   └── SignUpScreen.kt          # Registration UI
│
├── onboarding/                  # Conversational onboarding
│   ├── AgentConversationViewModel.kt
│   ├── AgentConversationScreen.kt
│   └── PermissionsSheet.kt
│
├── profile/                     # Profile and goal management
│   ├── ProfileScreen.kt
│   ├── GoalManagementScreen.kt
│   └── SmartGoalPlanningScreen.kt
│
├── ui/theme/                    # Material Design theme
│   ├── Color.kt
│   ├── Theme.kt
│   └── Type.kt
│
├── MainActivity.kt              # App entry point
├── MainScreen.kt                # Bottom navigation
└── BedrockTestScreen.kt         # AI testing screen
```

## Key Components

### MainActivity
- **Responsibility**: App initialization, deep link handling, navigation orchestration
- **Key Features**:
  - Initializes Supabase client via `AuthFactory`
  - Creates authentication components (EmailAuthenticator, OAuthAuthenticator, SessionManager)
  - Handles OAuth deep links (`amigo://auth`)
  - Manages onboarding state per user
  - Routes between Login → Onboarding → MainApp

### AuthViewModel
- **Extends**: `ViewModel`
- **Scope**: `viewModelScope` for coroutine management
- **State Management**: `StateFlow` for reactive UI updates
- **Key Responsibilities**:
  - Email/password authentication
  - OAuth (Google) authentication
  - Session management
  - Error handling and user feedback
  - Deep link session handling

### AgentConversationViewModel
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
MainActivity.onCreate()
    ↓
Check isAuthenticated (from SessionManager)
    ↓
    ├─ Not Authenticated → LoginScreen / SignUpScreen
    │                          ↓
    │                      OAuth Deep Link Handler
    │                          ↓
    │                      AuthViewModel.handleDeepLinkSession()
    │
    └─ Authenticated → Check onboarding status
                          ↓
                          ├─ Not Complete → AgentConversationScreen
                          │                     ↓
                          │                 Save profile data
                          │                     ↓
                          │                 Mark onboarding complete
                          │
                          └─ Complete → MainScreen (Bottom Nav)
```

## State Management

### StateFlow Pattern
All ViewModels expose state via `StateFlow`:
```kotlin
private val _email = MutableStateFlow("")
val email: StateFlow<String> = _email.asStateFlow()
```

### Composable State Collection
```kotlin
val isAuthenticated by viewModel.isAuthenticated.collectAsState()
```

## Deep Link Handling

### OAuth Callback Flow
1. User initiates OAuth (Google/Apple)
2. Browser redirects to `amigo://auth#access_token=...&refresh_token=...`
3. `MainActivity.onNewIntent()` captures deep link
4. Extract tokens from URL fragment
5. Call `AuthViewModel.handleDeepLinkSession()`
6. `SessionManager.setSessionFromTokens()` saves to secure storage
7. UI updates via `isAuthenticated` StateFlow

## Dependency Injection

### Manual DI Pattern
- No DI framework (Hilt/Koin) currently used
- Dependencies created in `MainActivity` and passed to ViewModels
- Shared module factories provide singleton instances

### Example
```kotlin
val emailAuthenticator = AuthFactory.createEmailAuthenticator()
val sessionManager = AuthFactory.createSessionManager(secureStorage)
val authViewModel = AuthViewModel(emailAuthenticator, oauthAuthenticator, sessionManager)
```

## UI Layer (Jetpack Compose)

### Theme System
- Material Design 3
- Custom color scheme in `Color.kt`
- Typography definitions in `Type.kt`
- Dark/light theme support in `Theme.kt`

### Composable Patterns
- **Stateless Composables**: Accept state and callbacks as parameters
- **ViewModel Integration**: Use `collectAsState()` for reactive updates
- **Navigation**: `NavHost` with `rememberNavController()`

## Platform-Specific Features

### Secure Storage
- Uses `EncryptedSharedPreferences` from androidx.security
- Stores access/refresh tokens securely
- Automatic encryption/decryption

### Permissions
- Health Connect permissions (future)
- Camera permissions for meal logging (future)
- Notification permissions for reminders (future)

## Testing Strategy

### Unit Tests
- ViewModel logic testing
- State transformation testing
- Mock shared module dependencies

### UI Tests
- Composable testing with `ComposeTestRule`
- Navigation flow testing
- User interaction testing

### Integration Tests
- End-to-end authentication flows
- Onboarding completion flow
- Deep link handling

## Performance Considerations

### Lazy Initialization
- ViewModels created only when needed
- Shared module singletons prevent duplicate instances

### Coroutine Scoping
- `viewModelScope` automatically cancels on ViewModel clear
- Prevents memory leaks from long-running operations

### Compose Recomposition
- Use `remember` for expensive calculations
- `derivedStateOf` for computed state
- Minimize recomposition scope

## Build Configuration

### Gradle (Kotlin DSL)
- **minSdk**: 26 (Android 8.0)
- **compileSdk**: 34 (Android 14)
- **Compose**: Material 3 with BOM

### Dependencies
- Jetpack Compose (UI toolkit)
- Compose Navigation (screen navigation)
- Kotlin Coroutines (async operations)
- Shared KMP module (business logic)

## Known Issues & TODOs

1. **No DI Framework**: Consider adding Hilt for better dependency management
2. **Onboarding State**: Currently stored in SharedPreferences, should sync with backend
3. **Error Handling**: Could be more granular with specific error types
4. **Testing Coverage**: Need more comprehensive test suite

## Future Enhancements

1. **Session Directory**: Add session initialization screens (LoadingScreen, ErrorScreen)
2. **Offline Support**: Add local database with Room
3. **Push Notifications**: Implement FCM for reminders
4. **Health Connect**: Integrate with Android Health Connect
5. **Camera Integration**: Add CameraX for meal photo capture
6. **Biometric Auth**: Add fingerprint/face unlock

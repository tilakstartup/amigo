# Bug Fixes: Session Persistence and Onboarding Flash

## Summary

Fixed two critical bugs discovered during testing of the user-session-initialization spec:

1. **Android Session Persistence Bug**: App sometimes opened as logged out even when user was logged in
2. **iOS Onboarding Flash Bug**: Onboarding screen briefly appeared before loading main app, causing unnecessary Bedrock API calls

## Changes Made

### 1. Android Session Persistence Fix

**File**: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/auth/SessionManager.kt`

**Problem**: The `SessionManager.initialize()` function only checked the initial session status but didn't continuously observe session changes. This meant the app wouldn't detect when Supabase SDK restored a session on app restart.

**Solution**: Added continuous session status monitoring using Supabase's `sessionStatus` flow:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

suspend fun initialize() {
    // Check initial session status
    val session = supabase.auth.currentSessionOrNull()
    _isAuthenticated.value = session != null
    
    // Observe session status changes to keep isAuthenticated in sync
    scope.launch {
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    _isAuthenticated.value = true
                }
                is SessionStatus.NotAuthenticated -> {
                    _isAuthenticated.value = false
                }
                else -> {
                    // Keep current state for other statuses
                }
            }
        }
    }
}
```

**Impact**: The app now correctly detects when users are logged in on app restart, eliminating the need for users to log in again.

### 2. iOS Onboarding Flash Fix

**File**: `mobile/ios/Amigo/AmigoApp.swift`

**Problem**: The `hasCompletedOnboarding` state was initialized as `false`, causing the onboarding screen to render immediately while the async `checkUserOnboardingStatus()` function was still running. This created a visible flash and triggered unnecessary Bedrock API calls.

**Solution**: Changed `hasCompletedOnboarding` from `Bool` to `Bool?` (optional) and added a loading state:

```swift
@State private var hasCompletedOnboarding: Bool? = nil // nil = not yet determined

var body: some Scene {
    WindowGroup {
        Group {
            // ... other conditions ...
            } else if hasCompletedOnboarding == nil {
                // Show loading while determining onboarding status
                LoadingView()
            } else if hasCompletedOnboarding == false {
                // Show post-auth onboarding
                OnboardingCoordinator(...)
            } else {
                // Show main app
                MainTabView(...)
            }
        }
    }
}
```

Added a simple `LoadingView`:

```swift
struct LoadingView: View {
    var body: some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(1.5)
            Text("Loading...")
                .font(.headline)
                .foregroundColor(.secondary)
        }
    }
}
```

**Impact**: Users now see a clean loading screen instead of a flash of the onboarding screen, and no unnecessary Bedrock API calls are made during app launch.

## Testing Results

All test scenarios passed on both platforms:

✅ Android session persistence works correctly after app restart
✅ iOS onboarding screen no longer flashes on app launch
✅ Both platforms correctly detect authentication state
✅ No unnecessary Bedrock API calls during app launch

## Technical Details

### Session Status Flow

The Supabase SDK 3.x provides a `sessionStatus` flow that emits different states:
- `SessionStatus.Authenticated`: User has a valid session
- `SessionStatus.NotAuthenticated`: No valid session
- Other states: `Initializing`, `RefreshFailure`, etc.

By observing this flow, we ensure the app's authentication state stays in sync with Supabase's session management.

### Three-State Pattern

The iOS fix uses a three-state pattern for `hasCompletedOnboarding`:
- `nil`: Status not yet determined (show loading)
- `false`: Onboarding not completed (show onboarding)
- `true`: Onboarding completed (show main app)

This pattern prevents premature rendering of UI before async operations complete.

## Files Modified

1. `mobile/shared/src/commonMain/kotlin/com/amigo/shared/auth/SessionManager.kt`
2. `mobile/ios/Amigo/AmigoApp.swift`

## Build Verification

- ✅ Shared module builds successfully
- ✅ Android app builds successfully
- ✅ iOS app builds successfully
- ✅ No compilation errors or warnings related to changes

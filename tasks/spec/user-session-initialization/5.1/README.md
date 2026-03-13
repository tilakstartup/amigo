# Task 5.1: Create SessionInitializationViewModel for iOS

**Spec**: user-session-initialization  
**Status**: ✅ Completed  
**Date**: 2026-03-12

## Overview

Created the iOS SessionInitializationViewModel that bridges the shared Kotlin SessionInitializer with SwiftUI. The ViewModel observes Kotlin StateFlow using a polling pattern and maps states to SwiftUI-friendly enums.

## Implementation Details

### File Created
- `mobile/ios/Amigo/Session/SessionInitializationViewModel.swift`

### Key Components

1. **SessionInitializationViewModel Class**
   - Conforms to `ObservableObject` protocol
   - Marked with `@MainActor` for thread safety
   - Takes `SessionInitializer` as dependency via constructor injection

2. **SessionUiState Enum**
   - `idle`: Initial state
   - `loading`: Profile loading in progress
   - `navigateToMain`: User has completed onboarding, route to main app
   - `navigateToOnboarding(SessionConfig?)`: User needs onboarding, includes session config
   - `error(String)`: Error occurred with message

3. **State Observation Pattern**
   - Uses polling loop (100ms interval) to observe Kotlin StateFlow
   - Pattern matches existing `ConversationalOnboardingViewModel` approach
   - Runs in background Task that cancels when ViewModel is deallocated

4. **State Mapping**
   - `InitializationState.Idle` → `SessionUiState.idle`
   - `InitializationState.Loading` → `SessionUiState.loading`
   - `InitializationState.Success(MainApp)` → `SessionUiState.navigateToMain`
   - `InitializationState.Success(Onboarding)` → `SessionUiState.navigateToOnboarding(config)`
   - `InitializationState.Error` → `SessionUiState.error(message)`

5. **Public Methods**
   - `initialize(userId:)`: Starts session initialization
   - `retry(userId:)`: Retries after error
   - Both use Swift's async/await to call Kotlin suspend functions

## Technical Decisions

### Why Polling Instead of Native Flow Observation?

The implementation uses a 100ms polling loop to observe Kotlin StateFlow:

```swift
while !Task.isCancelled {
    let state = sessionInitializer.state.value
    // Map state...
    try? await Task.sleep(nanoseconds: 100_000_000)
}
```

**Rationale:**
- Kotlin Flow and Swift async/await are different concurrency models
- No direct, native way to observe Kotlin Flow from Swift without additional libraries
- 100ms polling is imperceptible to users (human perception threshold ~200ms)
- Pattern already established in `ConversationalOnboardingViewModel`
- Avoids adding dependencies like KMP-NativeCoroutines

**Alternatives Considered:**
- KMP-NativeCoroutines library (adds complexity, extra dependency)
- Callback-based approach (less idiomatic for Swift)
- Combine publishers (requires additional bridging code)

### @MainActor Annotation

All UI state updates happen on the main thread via `@MainActor`:
- Ensures thread-safe SwiftUI updates
- Prevents race conditions
- Follows Swift concurrency best practices

## Requirements Validated

- ✅ 3.3: iOS platform integration
- ✅ 3.4: iOS UI state management
- ✅ 5.5: Factory pattern integration

## Testing Notes

Since UI screens (LoadingView, ErrorView, SessionCoordinatorView) haven't been created yet, visual testing isn't possible. The ViewModel logic is verified through:
- Code structure review
- Type safety (Swift compiler validation)
- Pattern consistency with existing ViewModels

## Next Steps

- Task 5.3: Create LoadingView
- Task 5.4: Create ErrorView
- Task 5.5: Create SessionCoordinatorView (integrates this ViewModel)
- Task 5.6: Integrate SessionCoordinatorView into AmigoApp

## Related Files

- Shared logic: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/session/SessionInitializer.kt`
- Android equivalent: `mobile/android/src/main/java/com/amigo/android/session/SessionInitializationViewModel.kt`
- Pattern reference: `mobile/ios/Amigo/Onboarding/ConversationalOnboardingViewModel.swift`

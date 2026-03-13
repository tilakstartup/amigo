# Implementation Plan: User Session Initialization

## Overview

This implementation plan breaks down the user session initialization feature into discrete coding tasks. The feature implements automatic profile loading on app startup with intelligent routing based on onboarding completion status. The implementation follows a bottom-up approach: shared business logic first (Kotlin Multiplatform), then platform-specific integrations (Android and iOS).

The core SessionInitializer orchestrates profile loading, caching, retry logic, and routing decisions. Platform-specific ViewModels bridge the shared logic with native UI frameworks (Jetpack Compose for Android, SwiftUI for iOS).

## Tasks

- [ ] 1. Implement shared session initialization components
  - [x] 1.1 Create ProfileCache with TTL management
    - Implement ProfileCache class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/session/ProfileCache.kt`
    - Implement CachedProfile data class with isStale() method
    - Use CurrentTime.now() for timestamp management
    - Set TTL to 5 minutes using kotlinx-datetime Duration
    - Implement get(), put(), clear(), and clearAll() methods
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [ ]* 1.2 Write property test for ProfileCache
    - **Property 10: Cache Hit Enables Instant Routing**
    - **Property 11: Cache Miss Triggers Fresh Load**
    - **Validates: Requirements 7.1, 7.2, 7.3**
  
  - [x] 1.3 Implement SessionInitializer core logic
    - Create SessionInitializer class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/session/SessionInitializer.kt`
    - Implement initialize() method with cache-first strategy
    - Implement loadProfileWithRetry() with exponential backoff (500ms, 1000ms, 2000ms)
    - Implement determineRoute() method based on onboarding_completed flag
    - Implement refreshProfileInBackground() for cache updates
    - Implement retry() method for manual retry
    - Use MutableStateFlow for state management (Idle, Loading, Success, Error)
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 2.1, 2.2, 2.3, 2.4, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2_

  - [ ]* 1.4 Write property tests for SessionInitializer
    - **Property 1: Profile Load Triggers Correct Routing Decision**
    - **Property 2: Profile Load Failure Results in Error State**
    - **Property 3: Loading State Emitted During Initialization**
    - **Property 4: Onboarding Route Includes Session Config**
    - **Property 5: State Updates Are Observable**
    - **Property 6: Retry Logic with Exponential Backoff**
    - **Property 7: Error State After Max Retries**
    - **Property 8: Manual Retry Re-attempts Load**
    - **Property 9: Fallback to Main App When Config Load Fails**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.5, 2.1, 2.2, 2.3, 2.4, 6.2, 6.3, 6.4, 6.5**
  
  - [x] 1.5 Create SessionInitializerFactory
    - Implement SessionInitializerFactory object in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/session/SessionInitializerFactory.kt`
    - Implement singleton pattern with synchronized instance creation
    - Create shared ProfileCache instance
    - Implement create() method accepting SupabaseClient
    - Implement getInstance() method for retrieving existing instance
    - _Requirements: 5.1, 5.2_
  
  - [ ]* 1.6 Write unit tests for SessionInitializerFactory
    - Test singleton behavior (same instance returned)
    - Test factory creates SessionInitializer with correct dependencies
    - _Requirements: 5.1, 5.2_

- [x] 2. Checkpoint - Ensure shared components compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [-] 3. Implement Android integration
  - [x] 3.1 Create SessionInitializationViewModel for Android
    - Create SessionInitializationViewModel class in `mobile/android/src/main/java/com/amigo/android/session/SessionInitializationViewModel.kt`
    - Extend ViewModel with viewModelScope
    - Implement SessionUiState sealed class (Idle, Loading, NavigateToMain, NavigateToOnboarding, Error)
    - Implement initialize() method that collects from SessionInitializer.state
    - Map InitializationState to SessionUiState
    - Implement retry() method
    - Use StateFlow for uiState
    - _Requirements: 3.1, 3.2, 5.5_
  
  - [ ]* 3.2 Write unit tests for SessionInitializationViewModel
    - Test state mapping from InitializationState to SessionUiState
    - Test initialize() triggers SessionInitializer
    - Test retry() calls SessionInitializer.retry()
    - _Requirements: 3.1, 3.2, 5.5_
  
  - [x] 3.3 Create LoadingScreen composable
    - Create LoadingScreen composable in `mobile/android/src/main/java/com/amigo/android/session/LoadingScreen.kt`
    - Display centered CircularProgressIndicator
    - Use Material Design theme
    - _Requirements: 1.5_
  
  - [x] 3.4 Create ErrorScreen composable
    - Create ErrorScreen composable in `mobile/android/src/main/java/com/amigo/android/session/ErrorScreen.kt`
    - Display error icon, message, and retry button
    - Accept message and onRetry callback parameters
    - Use Material Design theme
    - _Requirements: 6.1, 6.4_
  
  - [x] 3.5 Integrate SessionInitializer into MainActivity
    - Update AmigoApp composable in `mobile/android/src/main/java/com/amigo/android/MainActivity.kt`
    - Create SessionInitializationViewModel using SessionInitializerFactory
    - Add LaunchedEffect to initialize session when authenticated
    - Add when expression to handle SessionUiState cases
    - Route to LoadingScreen, ErrorScreen, AgentConversationScreen (onboarding), or MainScreen
    - Pass SessionConfig to AgentConversationScreen for onboarding
    - Refresh session state after onboarding completion
    - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 4.1, 4.2, 5.3, 5.4, 5.5, 6.1, 6.4_
  
  - [ ]* 3.6 Write integration tests for Android flow
    - Test authenticated user with onboarding_completed=false routes to onboarding
    - Test authenticated user with onboarding_completed=true routes to main app
    - Test error state shows ErrorScreen with retry button
    - _Requirements: 2.1, 2.2, 3.1, 3.2, 6.1_

- [x] 4. Checkpoint - Ensure Android integration compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [-] 5. Implement iOS integration
  - [x] 5.1 Create SessionInitializationViewModel for iOS
    - Create SessionInitializationViewModel class in `mobile/ios/Amigo/Session/SessionInitializationViewModel.swift`
    - Conform to ObservableObject protocol
    - Implement SessionUiState enum (idle, loading, navigateToMain, navigateToOnboarding, error)
    - Implement observeState() method to observe Kotlin Flow from Swift
    - Implement initialize() method using Task and async/await
    - Map InitializationState to SessionUiState
    - Implement retry() method
    - Use @Published property for uiState
    - _Requirements: 3.3, 3.4, 5.5_
  
  - [ ]* 5.2 Write unit tests for SessionInitializationViewModel
    - Test state mapping from InitializationState to SessionUiState
    - Test initialize() triggers SessionInitializer
    - Test retry() calls SessionInitializer.retry()
    - _Requirements: 3.3, 3.4, 5.5_
  
  - [x] 5.3 Create LoadingView
    - Create LoadingView in `mobile/ios/Amigo/Session/LoadingView.swift`
    - Display centered ProgressView with loading text
    - Use SwiftUI styling
    - _Requirements: 1.5_
  
  - [x] 5.4 Create ErrorView
    - Create ErrorView in `mobile/ios/Amigo/Session/ErrorView.swift`
    - Display error icon, message, and retry button
    - Accept message and onRetry callback parameters
    - Use SwiftUI styling with async button action
    - _Requirements: 6.1, 6.4_
  
  - [x] 5.5 Create SessionCoordinatorView
    - Create SessionCoordinatorView in `mobile/ios/Amigo/Session/SessionCoordinatorView.swift`
    - Initialize SessionInitializationViewModel with SessionInitializer from factory
    - Add task modifier to initialize session when view appears
    - Add switch statement to handle SessionUiState cases
    - Route to LoadingView, ErrorView, ConversationalOnboardingView, or MainTabView
    - Pass SessionConfig to ConversationalOnboardingView for onboarding
    - Refresh session state after onboarding completion
    - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 3.3, 3.4, 4.1, 4.2, 5.3, 5.4, 5.5, 6.1, 6.4_
  
  - [x] 5.6 Integrate SessionCoordinatorView into AmigoApp
    - Update AmigoApp body in `mobile/ios/Amigo/AmigoApp.swift`
    - Replace direct MainTabView navigation with SessionCoordinatorView
    - Pass authViewModel and sessionManager to SessionCoordinatorView
    - Keep existing welcome and authentication flow
    - _Requirements: 3.3, 3.4, 4.1, 4.2_
  
  - [ ]* 5.7 Write integration tests for iOS flow
    - Test authenticated user with onboarding_completed=false routes to onboarding
    - Test authenticated user with onboarding_completed=true routes to main app
    - Test error state shows ErrorView with retry button
    - _Requirements: 2.1, 2.2, 3.3, 3.4, 6.1_

- [x] 6. Checkpoint - Ensure iOS integration compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Update XcodeGen project configuration
  - [x] 7.1 Add new Session directory to project.yml
    - Update `mobile/ios/project.yml` to include Session directory
    - Add SessionInitializationViewModel.swift, LoadingView.swift, ErrorView.swift, SessionCoordinatorView.swift
    - Ensure files are included in Amigo target
    - _Requirements: 3.3, 3.4_
  
  - [x] 7.2 Regenerate Xcode project
    - Run `xcodegen generate` in mobile/ios directory
    - Verify new files appear in Xcode project
    - _Requirements: 3.3, 3.4_

- [ ] 8. Final integration and testing
  - [x] 8.1 Test end-to-end flow on Android
    - Build and run Android app
    - Test authenticated user with incomplete onboarding routes to conversational onboarding
    - Test authenticated user with complete onboarding routes to main app
    - Test error handling with network failures
    - Test cache behavior with repeated app launches
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 6.1, 7.1, 7.2_
  
  - [x] 8.2 Test end-to-end flow on iOS
    - Build and run iOS app on simulator
    - Test authenticated user with incomplete onboarding routes to conversational onboarding
    - Test authenticated user with complete onboarding routes to main app
    - Test error handling with network failures
    - Test cache behavior with repeated app launches
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 6.1, 7.1, 7.2_

- [x] 9. Final checkpoint - Ensure all tests pass and feature is complete
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at platform boundaries
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end flows on each platform
- The implementation follows a bottom-up approach: shared logic → Android → iOS
- SessionInitializer uses cache-first strategy for optimal performance
- Retry logic implements exponential backoff (500ms, 1000ms, 2000ms)
- Error handling provides manual retry option for users
- Background refresh keeps cache fresh without blocking UI

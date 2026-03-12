# Implementation Plan: App Startup Flow and Amigo Chat Integration

## Overview

This implementation plan breaks down the app startup flow and Amigo chat integration feature into discrete, actionable coding tasks. The implementation follows a layered approach: shared Kotlin Multiplatform business logic first, followed by platform-specific UI implementations for iOS (SwiftUI) and Android (Jetpack Compose).

The feature introduces enhanced app startup with profile checking, a three-tab bottom navigation system, a full Amigo chat interface with session management, and comprehensive state persistence across app lifecycle events.

## Tasks

- [x] 1. Create core data models and interfaces
  - Create `AppStartupManager` interface and data models (`StartupResult`, `StartupDestination`, `OnboardingStatus`)
  - Create `BottomNavigationManager` interface and models (`NavigationTab`, `TabState`, `NavigationState`)
  - Create `AmigoChat` interface and models (`SessionType`, `SessionState`, `ConversationState`)
  - Create `AmigoSessionManager` class with `ChatSession` data model
  - Create `SessionPersistence` interface and `SessionPersistenceData` model
  - Create `AmigoSessionState` and `SessionMetrics` data models
  - _Requirements: 1.1, 2.1, 3.1, 4.1, 6.1_

- [ ]* 1.1 Write property test for data model serialization
  - **Property: Round-trip serialization consistency**
  - **Validates: Requirements 6.1, 6.2**
  - Test that all session state models can be serialized and deserialized without data loss

- [x] 2. Implement App Startup Manager (Shared Kotlin)
  - [x] 2.1 Create `AppStartupManagerImpl` class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/startup/`
    - Implement `initialize()` method with profile API call and retry logic
    - Implement `checkOnboardingStatus()` method to evaluate onboarding flag
    - Implement `handleStartupError()` method with exponential backoff (3 retries, 3s base delay)
    - Add timeout handling (5 second limit)
    - Integrate with existing `ProfileManager` and `SessionManager`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 7.1_

  - [ ]* 2.2 Write property test for startup manager
    - **Property 1: App Startup Profile API Call**
    - **Validates: Requirements 1.1**
    - Test that profile API is always called before navigation decisions

  - [ ]* 2.3 Write property test for onboarding navigation
    - **Property 2: Onboarding Flag Navigation Decision**
    - **Validates: Requirements 1.2, 1.3, 1.4**
    - Test that navigation destination is correctly determined based on onboarding flag

  - [ ]* 2.4 Write property test for retry mechanism
    - **Property 3: Profile API Retry with Exponential Backoff**
    - **Validates: Requirements 1.5, 7.1**
    - Test that failures trigger exactly 3 retries with exponential backoff

  - [ ]* 2.5 Write unit tests for startup manager
    - Test specific scenarios: completed onboarding, incomplete onboarding, network failures
    - Test timeout behavior and error messages
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [x] 3. Implement Session Management System (Shared Kotlin)
  - [x] 3.1 Create `AmigoSessionManager` class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/session/`
    - Implement `createSession()` method with session ID generation
    - Implement `endCurrentSession()` method to terminate sessions
    - Implement `getCurrentSession()` and `getCurrentSessionType()` methods
    - Add session history tracking with `ChatSession` data model
    - _Requirements: 4.1, 4.4, 4.5, 4.6_

  - [x] 3.2 Create `SessionPersistenceImpl` class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/session/`
    - Implement `saveSessionState()` method with message trimming (200 message limit)
    - Implement `loadSessionState()` method with data validation
    - Implement `clearSessionState()` and `hasPersistedSession()` methods
    - Integrate with platform-specific local storage (expect/actual pattern)
    - _Requirements: 6.1, 6.2, 6.5, 8.6_

  - [ ]* 3.3 Write property test for session state management
    - **Property 9: Session State Management**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6**
    - Test that minimize preserves state, end clears state, restore returns to previous state

  - [ ]* 3.4 Write property test for session persistence
    - **Property 10: App Lifecycle State Persistence**
    - **Validates: Requirements 4.7, 6.1, 6.2, 6.4**
    - Test that session state persists across app lifecycle events

  - [ ]* 3.5 Write unit tests for session management
    - Test session creation, termination, and state transitions
    - Test message history limits and trimming behavior
    - _Requirements: 4.1, 4.4, 4.5, 6.1, 6.2, 8.6_

- [x] 4. Implement General Chat Session Configuration (Shared Kotlin)
  - [x] 4.1 Create `GeneralChatSessionConfig` in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/sessions/`
    - Define session configuration with responsibilities, data collection, and initial message
    - Follow existing `SessionConfig` pattern from `GoalSettingSessionConfig`
    - Register configuration with `SessionConfigLoader`
    - _Requirements: 3.3, 7.5_

  - [ ]* 4.2 Write unit tests for general chat configuration
    - Test configuration loading and validation
    - Test integration with `SessionConfigLoader`
    - _Requirements: 3.3_

- [x] 5. Checkpoint - Ensure all shared Kotlin tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement Amigo Chat Interface (Shared Kotlin)
  - [x] 6.1 Create `AmigoChat` class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/`
    - Implement `startNewSession()` method with session type selection (onboarding/general_chat)
    - Implement `sendMessage()` and `sendQuickReply()` methods
    - Implement `minimizeSession()` method with state persistence
    - Implement `endSession()` method with cleanup
    - Implement `restoreSession()` method with error handling
    - Integrate with existing `AmigoAgentConversation` class
    - Add typing indicator state management
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.5, 4.6, 8.1_

  - [ ]* 6.2 Write property test for chat session creation
    - **Property 7: Amigo Chat Session Creation**
    - **Validates: Requirements 3.2, 3.3, 5.1**
    - Test that opening chat interface always creates appropriate session

  - [ ]* 6.3 Write property test for message exchange
    - **Property 8: Chat Message Exchange**
    - **Validates: Requirements 3.4, 3.5, 3.6**
    - Test that messages are properly sent, received, and stored in conversation history

  - [ ]* 6.4 Write unit tests for Amigo chat interface
    - Test session lifecycle operations (start, minimize, end, restore)
    - Test message handling and error states
    - Test typing indicator behavior
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

- [x] 7. Implement Bottom Navigation Manager (Shared Kotlin)
  - [x] 7.1 Create `BottomNavigationManager` class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/navigation/`
    - Implement tab selection with state flow management
    - Implement `selectTab()` method with performance tracking
    - Implement `getTabState()` method for individual tab states
    - Implement `persistTabState()` and `restoreTabState()` methods
    - Add tab history tracking
    - _Requirements: 2.5, 2.6, 2.7, 8.3_

  - [ ]* 7.2 Write property test for tab selection
    - **Property 5: Bottom Navigation Tab Selection**
    - **Validates: Requirements 2.5, 8.3**
    - Test that tab selection responds within 200ms and updates state correctly

  - [ ]* 7.3 Write property test for navigation persistence
    - **Property 6: Navigation State Persistence**
    - **Validates: Requirements 2.6, 2.7**
    - Test that navigation state persists during app session

  - [ ]* 7.4 Write unit tests for bottom navigation
    - Test tab selection and state updates
    - Test navigation history tracking
    - Test state persistence and restoration
    - _Requirements: 2.5, 2.6, 2.7_

- [x] 8. Implement Error Handling System (Shared Kotlin)
  - [x] 8.1 Create `ErrorHandler` class in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/error/`
    - Implement `handleStartupError()` method with recovery actions
    - Implement `handleSessionError()` method for session failures
    - Implement `handleNetworkError()` method with message queuing
    - Add error tracking and analytics integration
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 8.2 Write property test for error recovery
    - **Property 13: Error Handling and Recovery**
    - **Validates: Requirements 7.2, 7.3, 7.5**
    - Test that errors trigger appropriate recovery actions

  - [ ]* 8.3 Write property test for network interruption handling
    - **Property 14: Network Interruption Handling**
    - **Validates: Requirements 7.4**
    - Test that messages are queued during network interruptions

  - [ ]* 8.4 Write unit tests for error handling
    - Test specific error scenarios and recovery paths
    - Test message queuing and retry logic
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 9. Implement Onboarding Integration (Shared Kotlin)
  - [x] 9.1 Update `ProfileManager` to support onboarding flag updates
    - Add `updateOnboardingStatus()` method to set onboarding_completed flag
    - Integrate with existing profile update functionality
    - _Requirements: 5.3, 5.4_

  - [x] 9.2 Create onboarding completion handler in `AmigoChat`
    - Detect onboarding completion from conversation state
    - Trigger profile update and navigation transition
    - _Requirements: 5.2, 5.3, 5.4, 5.5_

  - [ ]* 9.3 Write property test for onboarding integration
    - **Property 11: Onboarding Integration**
    - **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
    - Test that onboarding completion updates flag and triggers navigation

  - [ ]* 9.4 Write unit tests for onboarding integration
    - Test onboarding flag updates
    - Test navigation transitions after completion
    - _Requirements: 5.2, 5.3, 5.4, 5.5_

- [x] 10. Checkpoint - Ensure all shared Kotlin implementation is complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement iOS App Startup Integration (SwiftUI)
  - [] 11.1 Update `AmigoApp.swift` to integrate `AppStartupManager`
    - Add startup manager initialization in app entry point
    - Implement startup flow with loading indicators
    - Handle navigation to main app or onboarding based on startup result
    - Add error handling UI for startup failures
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 7.1, 8.4_

  - [ ]* 11.2 Write UI tests for iOS startup flow
    - Test navigation to main app when onboarding is complete
    - Test navigation to onboarding when flag is false
    - Test error display and retry functionality
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [~] 12. Implement iOS Bottom Navigation (SwiftUI)
  - [ ] 12.1 Create `MainTabView.swift` in `mobile/ios/Amigo/Navigation/`
    - Implement three-tab navigation (Home, Profile, Amigo)
    - Use SF Symbols for tab icons (house, person, face.smiling)
    - Integrate with `BottomNavigationManager` for state management
    - Add tab selection handling and state persistence
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [ ]* 12.2 Write UI tests for iOS bottom navigation
    - Test tab selection and navigation
    - Test tab state persistence
    - Test performance (200ms response time)
    - _Requirements: 2.5, 2.6, 2.7, 8.3_

- [~] 13. Implement iOS Amigo Chat Interface (SwiftUI)
  - [ ] 13.1 Create `AmigoView.swift` in `mobile/ios/Amigo/Chat/`
    - Implement chat UI with message list and input field
    - Add session control buttons (minimize, end session)
    - Integrate with `AmigoChat` shared class
    - Display typing indicators and loading states
    - Handle message sending and receiving
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 8.1_

  - [ ] 13.2 Create `AmigoViewModel.swift` in `mobile/ios/Amigo/Chat/`
    - Bridge SwiftUI to shared `AmigoChat` Kotlin class
    - Manage conversation state and message flow
    - Handle session lifecycle operations
    - Implement error handling and retry logic
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

  - [ ] 13.3 Create `ChatMessageView.swift` for individual message display
    - Implement message bubble UI (user vs assistant)
    - Add timestamp and status indicators
    - Support quick reply buttons and interactive elements
    - _Requirements: 3.4, 3.5_

  - [ ]* 13.4 Write UI tests for iOS Amigo chat
    - Test message display and sending
    - Test session controls (minimize, end)
    - Test typing indicators and loading states
    - _Requirements: 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

- [~] 14. Implement iOS Session Persistence (Swift)
  - [ ] 14.1 Create `LocalStorage.swift` actual implementation for iOS
    - Implement UserDefaults-based storage for session state
    - Add data serialization and deserialization
    - Implement storage quota management
    - _Requirements: 6.1, 6.2, 6.5_

  - [ ] 14.2 Add app lifecycle handlers in `AmigoApp.swift`
    - Handle app backgrounding with session state persistence
    - Handle app foregrounding with session state restoration
    - Handle app termination with state cleanup
    - _Requirements: 4.7, 6.1, 6.2, 6.6_

  - [ ]* 14.3 Write tests for iOS session persistence
    - Test state saving and loading
    - Test app lifecycle state transitions
    - _Requirements: 6.1, 6.2, 4.7_

- [~] 15. Checkpoint - Ensure all iOS implementation is complete
  - Ensure all tests pass, ask the user if questions arise.

- [~] 16. Implement Android App Startup Integration (Jetpack Compose)
  - [ ] 16.1 Update `MainActivity.kt` to integrate `AppStartupManager`
    - Add startup manager initialization in activity onCreate
    - Implement startup flow with loading composables
    - Handle navigation to main app or onboarding based on startup result
    - Add error handling UI for startup failures
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 7.1, 8.4_

  - [ ]* 16.2 Write UI tests for Android startup flow
    - Test navigation to main app when onboarding is complete
    - Test navigation to onboarding when flag is false
    - Test error display and retry functionality
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [~] 17. Implement Android Bottom Navigation (Jetpack Compose)
  - [ ] 17.1 Create `MainBottomNavigation.kt` in `mobile/android/src/main/java/com/amigo/android/navigation/`
    - Implement three-tab navigation composable (Home, Profile, Amigo)
    - Use Material Icons for tab icons (Home, Person, Face)
    - Integrate with `BottomNavigationManager` for state management
    - Add tab selection handling and state persistence
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [ ] 17.2 Create `MainScreen.kt` to host bottom navigation and content
    - Implement navigation host with three destinations
    - Handle tab content switching
    - Manage navigation state
    - _Requirements: 2.1, 2.5, 2.6, 2.7_

  - [ ]* 17.3 Write UI tests for Android bottom navigation
    - Test tab selection and navigation
    - Test tab state persistence
    - Test performance (200ms response time)
    - _Requirements: 2.5, 2.6, 2.7, 8.3_

- [~] 18. Implement Android Amigo Chat Interface (Jetpack Compose)
  - [ ] 18.1 Create `AmigoScreen.kt` in `mobile/android/src/main/java/com/amigo/android/chat/`
    - Implement chat UI with LazyColumn for messages and input field
    - Add session control buttons (minimize, end session)
    - Integrate with `AmigoChat` shared class
    - Display typing indicators and loading states
    - Handle message sending and receiving
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 8.1_

  - [ ] 18.2 Create `AmigoViewModel.kt` in `mobile/android/src/main/java/com/amigo/android/chat/`
    - Bridge Compose to shared `AmigoChat` Kotlin class
    - Manage conversation state and message flow
    - Handle session lifecycle operations
    - Implement error handling and retry logic
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

  - [ ] 18.3 Create `ChatMessageItem.kt` for individual message composable
    - Implement message bubble UI (user vs assistant)
    - Add timestamp and status indicators
    - Support quick reply buttons and interactive elements
    - _Requirements: 3.4, 3.5_

  - [ ]* 18.4 Write UI tests for Android Amigo chat
    - Test message display and sending
    - Test session controls (minimize, end)
    - Test typing indicators and loading states
    - _Requirements: 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

- [~] 19. Implement Android Session Persistence (Kotlin)
  - [ ] 19.1 Create `LocalStorage.kt` actual implementation for Android
    - Implement SharedPreferences-based storage for session state
    - Add data serialization and deserialization
    - Implement storage quota management
    - _Requirements: 6.1, 6.2, 6.5_

  - [ ] 19.2 Add app lifecycle handlers in `MainActivity.kt`
    - Handle app backgrounding with session state persistence
    - Handle app foregrounding with session state restoration
    - Handle app termination with state cleanup
    - _Requirements: 4.7, 6.1, 6.2, 6.6_

  - [ ]* 19.3 Write tests for Android session persistence
    - Test state saving and loading
    - Test app lifecycle state transitions
    - _Requirements: 6.1, 6.2, 4.7_

- [~] 20. Checkpoint - Ensure all Android implementation is complete
  - Ensure all tests pass, ask the user if questions arise.

- [~] 21. Implement Performance Optimizations
  - [ ] 21.1 Add message pagination in `AmigoChat`
    - Implement lazy loading for conversations exceeding 50 messages
    - Add scroll-to-load-more functionality
    - Optimize memory usage for large conversation histories
    - _Requirements: 8.5, 8.6_

  - [ ] 21.2 Add performance monitoring and metrics
    - Track startup time and report metrics
    - Track tab navigation response times
    - Track message response times
    - Track session restoration times
    - _Requirements: 1.6, 8.2, 8.3_

  - [ ]* 21.3 Write property test for performance guarantees
    - **Property 4: Startup Performance Guarantee**
    - **Validates: Requirements 1.6**
    - Test that startup completes within 5 seconds

  - [ ]* 21.4 Write property test for session history management
    - **Property 12: Session History Management**
    - **Validates: Requirements 6.3, 8.2, 8.6**
    - Test that history is maintained correctly and loads within performance limits

  - [ ]* 21.5 Write property test for UI feedback
    - **Property 16: UI Feedback and Performance**
    - **Validates: Requirements 8.1, 8.4, 8.5**
    - Test that appropriate feedback is displayed during processing

- [~] 22. Implement Offline Mode Support
  - [ ] 22.1 Add offline profile data caching in `AppStartupManager`
    - Cache last successful profile fetch
    - Provide offline mode access to cached data
    - Display offline indicator in UI
    - _Requirements: 7.6_

  - [ ] 22.2 Add message queuing for offline chat
    - Queue messages when network is unavailable
    - Send queued messages when connection is restored
    - Display pending message status in UI
    - _Requirements: 7.4_

  - [ ]* 22.3 Write property test for offline mode
    - **Property 15: Offline Mode Support**
    - **Validates: Requirements 7.6**
    - Test that offline mode provides access to cached profile data

  - [ ]* 22.4 Write unit tests for offline functionality
    - Test profile caching and retrieval
    - Test message queuing and sending
    - _Requirements: 7.4, 7.6_

- [~] 23. Final Integration and Testing
  - [ ] 23.1 End-to-end integration testing
    - Test complete onboarding flow (new user → profile check → onboarding chat → completion → main app)
    - Test returning user flow (existing user → profile check → main app → Amigo chat)
    - Test session persistence flow (start chat → minimize → background app → restore → continue chat)
    - Test error recovery flow (network failure → retry → success → continue)
    - _Requirements: All requirements_

  - [ ] 23.2 Cross-platform consistency verification
    - Verify iOS and Android implementations behave identically
    - Test shared Kotlin logic on both platforms
    - Verify UI consistency within platform guidelines
    - _Requirements: All requirements_

  - [ ]* 23.3 Performance benchmarking
    - Measure and verify startup time < 5 seconds (95th percentile)
    - Measure and verify tab navigation < 200ms (99th percentile)
    - Measure and verify session restoration < 1 second for < 100 messages
    - _Requirements: 1.6, 8.2, 8.3_

- [~] 24. Final checkpoint - Ensure all tests pass and feature is complete
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional testing tasks and can be skipped for faster MVP delivery
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties across all inputs
- Unit tests validate specific examples, edge cases, and error conditions
- The implementation follows a bottom-up approach: shared logic first, then platform-specific UI
- All shared Kotlin code should use expect/actual pattern for platform-specific implementations
- iOS implementation uses SwiftUI with native patterns and SF Symbols
- Android implementation uses Jetpack Compose with Material Design components
- Session state persistence uses platform-specific storage (UserDefaults for iOS, SharedPreferences for Android)
- Performance requirements are validated through both property tests and benchmarking

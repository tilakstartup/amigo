# Requirements Document

## Introduction

This feature implements proper user session initialization logic for authenticated users in both Android and iOS applications. When a logged-in user launches the app, the system will load their profile, check their onboarding completion status, and route them to the appropriate screen - either directly to the main app experience or to the conversational onboarding flow with the Amigo AI agent.

## Glossary

- **Session_Initializer**: The component responsible for loading user profile and determining initial app state on startup
- **Profile_Manager**: The shared Kotlin Multiplatform component that handles user profile operations with Supabase
- **Onboarding_Session**: A configured AI conversation session using SessionConfig that guides new users through profile setup
- **Amigo_Chat**: The conversational AI interface powered by Bedrock agent for user interactions
- **Authenticated_User**: A user who has successfully logged in and has a valid session token
- **Onboarding_Status**: A boolean field (onboarding_completed) in the user profile indicating whether onboarding is complete

## Requirements

### Requirement 1: Load User Profile on App Startup

**User Story:** As an authenticated user, I want my profile to be loaded automatically when I open the app, so that the app can personalize my experience immediately.

#### Acceptance Criteria

1. WHEN an Authenticated_User launches the app, THE Session_Initializer SHALL retrieve the user profile from Profile_Manager
2. WHEN the profile retrieval succeeds, THE Session_Initializer SHALL extract the Onboarding_Status from the profile
3. IF the profile retrieval fails, THEN THE Session_Initializer SHALL log the error and display an error message to the user
4. THE Session_Initializer SHALL complete profile loading within 3 seconds under normal network conditions
5. WHILE the profile is loading, THE Session_Initializer SHALL display a loading indicator to the user

### Requirement 2: Route Based on Onboarding Status

**User Story:** As a new user who hasn't completed onboarding, I want to be guided through the setup process, so that I can configure my health goals and preferences.

#### Acceptance Criteria

1. WHEN the Onboarding_Status is false, THE Session_Initializer SHALL open Amigo_Chat with the Onboarding_Session configuration
2. WHEN the Onboarding_Status is true, THE Session_Initializer SHALL navigate to the main app screen and skip the Onboarding_Session
3. THE Session_Initializer SHALL load the Onboarding_Session configuration using SessionConfigLoader before opening Amigo_Chat
4. THE routing decision SHALL occur immediately after profile loading completes

### Requirement 3: Android Implementation

**User Story:** As an Android user, I want seamless session initialization, so that I can start using the app quickly.

#### Acceptance Criteria

1. WHEN MainActivity launches with an authenticated session, THE Android_Session_Initializer SHALL invoke the Session_Initializer logic
2. THE Android_Session_Initializer SHALL use Jetpack Compose navigation to route to the appropriate screen
3. WHEN routing to Onboarding_Session, THE Android_Session_Initializer SHALL pass the onboarding SessionConfig to the conversation screen
4. THE Android_Session_Initializer SHALL handle configuration changes (rotation, theme) without re-triggering initialization
5. THE Android_Session_Initializer SHALL integrate with the existing AuthViewModel to check authentication state

### Requirement 4: iOS Implementation

**User Story:** As an iOS user, I want seamless session initialization, so that I can start using the app quickly.

#### Acceptance Criteria

1. WHEN AmigoApp launches with an authenticated session, THE iOS_Session_Initializer SHALL invoke the Session_Initializer logic
2. THE iOS_Session_Initializer SHALL use SwiftUI navigation to route to the appropriate screen
3. WHEN routing to Onboarding_Session, THE iOS_Session_Initializer SHALL pass the onboarding SessionConfig to the conversation view
4. THE iOS_Session_Initializer SHALL handle app lifecycle events (background, foreground) without re-triggering initialization on return
5. THE iOS_Session_Initializer SHALL integrate with the existing AuthViewModel to check authentication state

### Requirement 5: Shared Business Logic

**User Story:** As a developer, I want session initialization logic to be shared between platforms, so that behavior is consistent and maintenance is simplified.

#### Acceptance Criteria

1. THE Session_Initializer SHALL be implemented in Kotlin Multiplatform shared code
2. THE Session_Initializer SHALL expose a suspend function that returns the routing decision (onboarding or main app)
3. THE Session_Initializer SHALL use Profile_Manager for all profile data access
4. THE Session_Initializer SHALL use SessionConfigLoader to load the Onboarding_Session configuration
5. THE Session_Initializer SHALL emit state updates that platform-specific ViewModels can observe

### Requirement 6: Error Handling and Edge Cases

**User Story:** As a user, I want the app to handle errors gracefully during startup, so that I'm not stuck on a loading screen.

#### Acceptance Criteria

1. IF the user session is invalid or expired, THEN THE Session_Initializer SHALL navigate to the login screen
2. IF the network is unavailable during profile loading, THEN THE Session_Initializer SHALL retry up to 3 times with exponential backoff
3. IF all retry attempts fail, THEN THE Session_Initializer SHALL display an error message with a manual retry option
4. WHEN the user manually retries, THE Session_Initializer SHALL re-attempt profile loading
5. IF the Onboarding_Session configuration fails to load, THEN THE Session_Initializer SHALL log the error and fall back to the main app screen

### Requirement 7: Performance and User Experience

**User Story:** As a user, I want the app to start quickly, so that I can begin tracking my health without delay.

#### Acceptance Criteria

1. THE Session_Initializer SHALL cache the last known Onboarding_Status locally to enable instant routing on subsequent launches
2. WHEN using cached data, THE Session_Initializer SHALL still fetch fresh profile data in the background and update if changed
3. THE Session_Initializer SHALL complete the routing decision within 500ms when using cached data
4. THE loading indicator SHALL appear only if profile loading takes longer than 300ms
5. THE Session_Initializer SHALL preload the Onboarding_Session configuration during profile loading to minimize transition delay

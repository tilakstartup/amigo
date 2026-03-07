# Implementation Plan: Amigo Health Coaching Application

## Overview

This implementation plan covers the development of Amigo, an AI-first personal health coaching application using:
- **iOS**: Swift with native UIKit/SwiftUI (in `mobile/ios/`)
- **Android**: Kotlin with Jetpack Compose (in `mobile/android/`)
- **Shared Logic**: Kotlin Multiplatform for business logic, data models, and API clients (in `mobile/shared/`)
- **Backend**: Supabase (Auth, Database, Storage, Realtime)
- **AI**: Amazon Bedrock with Claude AI
- **Infrastructure**: AWS CloudFormation templates (in `infrastructure/`)
- **Documentation**: All documentation files (in `docs/`)
- **Task Summaries**: Task completion summaries (in `task_summaries/task_N/`)
- **Architecture**: Native UIs with shared Kotlin Multiplatform module for core functionality

## Project Structure

```
amigo/
├── mobile/
│   ├── ios/                    # iOS app (Swift/SwiftUI)
│   ├── android/                # Android app (Kotlin/Compose)
│   └── shared/                 # KMP shared module
├── supabase/                   # Supabase configuration (created by CLI)
│   ├── migrations/            # Database migrations
│   ├── functions/             # Edge functions
│   └── config.toml            # Supabase config
├── infrastructure/             # AWS CloudFormation templates (Bedrock only)
│   └── bedrock.yaml           # CloudFormation for AWS Bedrock
├── docs/                       # Documentation
│   ├── infrastructure/        # Infrastructure setup guides
│   │   ├── aws-bedrock.md    # AWS Bedrock setup
│   │   └── supabase-setup.md # Supabase setup
│   ├── api/                   # API documentation
│   ├── deployment/            # Deployment guides
│   └── user/                  # User documentation
└── task_summaries/            # Task completion summaries
    ├── task_1/
    ├── task_2/
    └── ...
```

The implementation follows a feature-by-feature approach, building core infrastructure first, then progressively adding features with testing at each stage.

## IMPORTANT: Verification Process

After completing EACH task (not just checkpoints), you MUST:
1. Build the app for both iOS and Android platforms
2. Launch and test the implemented functionality
3. Verify the task is fully complete and working
4. Create a task summary in `task_summaries/task_N/` with:
   - `summary.md`: What was implemented, decisions made, issues encountered
   - Any relevant code snippets or configuration files
   - Screenshots or test results if applicable
5. Wait for explicit user approval after they verify both apps
6. Once approved, ask the user for a git commit message
7. Commit the changes with the provided message
8. Push to the remote repository
9. Wait for confirmation before proceeding to the next task

Do NOT proceed to the next task until the user gives you the go-ahead.

## Tasks

- [x] 1. Project setup and infrastructure
  - [x] 1.1 Initialize Kotlin Multiplatform shared module
    - Create `mobile/shared/` directory
    - Create KMP module with common, iOS, and Android source sets
    - Configure Gradle build files for multiplatform compilation
    - Set up dependency management (Ktor, kotlinx.serialization, kotlinx.coroutines)
    - _Requirements: 99.1_

  - [x] 1.2 Initialize iOS project with Swift
    - Create `mobile/ios/` directory
    - Create iOS app project in Xcode
    - Configure project to link with KMP framework from `mobile/shared/`
    - Set up CocoaPods/SPM for iOS-specific dependencies
    - Configure build phases to compile KMP framework
    - _Requirements: 99.1_

  - [x] 1.3 Initialize Android project with Kotlin
    - Create `mobile/android/` directory
    - Create Android app project with Jetpack Compose
    - Configure Gradle to depend on KMP shared module from `mobile/shared/`
    - Set up Android-specific dependencies
    - _Requirements: 99.1_

  - [x] 1.4 Configure Supabase integration in KMP shared module
    - Add Supabase client library to `mobile/shared/` KMP common code
    - Create SupabaseClient wrapper with connection configuration
    - Implement secure credential storage interface (expect/actual)
    - Store Supabase configuration in `.env` file (not committed to git)
    - _Requirements: 99.1, 99.6_

  - [x] 1.5 Configure Amazon Bedrock integration in KMP shared module
    - Add AWS SDK for Bedrock to `mobile/shared/` KMP common code
    - Create BedrockClient wrapper for Claude AI access
    - Implement request signing and authentication
    - Store AWS credentials configuration in `.env` file (not committed to git)
    - _Requirements: 109.1, 109.2, 109.3_

  - [x] 1.6 Set up AWS infrastructure with CloudFormation
    - Create `infrastructure/` directory
    - Create CloudFormation template for AWS Bedrock access in `infrastructure/bedrock.yaml`
    - Configure IAM roles and policies for Bedrock API access
    - Document infrastructure setup in `docs/infrastructure/aws-bedrock.md`
    - _Requirements: 109.1, 109.2_

  - [x] 1.7 Initialize Supabase project with CLI
    - Install Supabase CLI
    - Run `supabase init` to create `supabase/` directory
    - Configure `supabase/config.toml` with project settings
    - Link to remote Supabase project with `supabase link`
    - Document Supabase setup in `docs/infrastructure/supabase-setup.md`
    - Store Supabase URL and anon key in `.env` file (not committed to git)
    - _Requirements: 99.1, 99.6_

  - [ ]* 1.8 Set up error logging and crash reporting
    - Integrate crash reporting service (Sentry or similar)
    - Implement ErrorLogger in `mobile/shared/` KMP shared module
    - Configure platform-specific crash reporters (iOS/Android)
    - _Requirements: 93.1, 94.1_

- [x] 2. Checkpoint - Verify project structure
  - Build iOS app from `mobile/ios/` and verify it launches successfully
  - Build Android app from `mobile/android/` and verify it launches successfully
  - Verify KMP framework from `mobile/shared/` integrates with both platforms
  - Ensure all projects compile without errors
  - Verify `supabase/` directory exists with config.toml and migrations folder
  - Verify `infrastructure/bedrock.yaml` CloudFormation template is valid
  - Verify Supabase CLI is linked to remote project
  - Create task summary in `task_summaries/task_1/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 3**

- [x] 3. Authentication system (Supabase Auth)
  - [x] 3.1 Implement authentication data models in KMP
    - Create User, Session, AuthCredentials data classes in `mobile/shared/`
    - Define AuthResult sealed class for success/error states
    - _Requirements: 1.3, 2.1_

  - [x] 3.2 Implement email/password authentication in KMP
    - Create EmailAuthenticator class in `mobile/shared/` using Supabase Auth
    - Implement signUp(email, password) function
    - Implement signIn(email, password) function
    - Implement email format validation
    - Implement password strength validation (min 8 characters)
    - _Requirements: 1.1, 1.2, 1.4, 1.8, 2.1, 2.2, 100.1_

  - [ ]* 3.3 Write property test for email format validation
    - **Property 1: Email Format Validation**
    - **Validates: Requirements 1.1**

  - [ ]* 3.4 Write property test for password hashing security
    - **Property 4: Password Hashing Security**
    - **Validates: Requirements 1.4**

  - [x] 3.5 Implement OAuth authentication in KMP
    - Create GoogleAuthenticator class in `mobile/shared/` using Supabase Auth
    - Create AppleAuthenticator class in `mobile/shared/` using Supabase Auth
    - Implement OAuth flow handling with platform-specific redirects
    - _Requirements: 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 6.1, 6.2, 100.2_

  - [x] 3.6 Implement session management in KMP
    - Create SessionManager class in `mobile/shared/`
    - Implement session token storage (expect/actual for platform-specific secure storage)
    - Implement session validation and refresh logic
    - Implement signOut functionality
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 100.5, 100.8_

  - [ ]* 3.7 Write property test for session token generation
    - **Property 5: Session Token Generation on Authentication**
    - **Validates: Requirements 1.5, 2.3**

  - [x] 3.8 Build iOS authentication UI (Swift)
    - Create login screen in `mobile/ios/` with email/password fields
    - Create sign-up screen
    - Add Google Sign-In button with OAuth flow
    - Add Sign in with Apple button
    - Implement form validation and error display
    - _Requirements: 1.1-1.8, 2.1-2.7, 3.1-3.7, 4.1-4.7, 5.1-5.8, 6.1-6.7_

  - [x] 3.9 Build Android authentication UI (Kotlin/Compose)
    - Create login screen in `mobile/android/` with email/password fields
    - Create sign-up screen
    - Add Google Sign-In button with OAuth flow
    - Implement form validation and error display
    - _Requirements: 1.1-1.8, 2.1-2.7, 3.1-3.7, 4.1-4.7_

  - [ ]* 3.10 Write unit tests for authentication flows
    - Test successful sign-up and sign-in
    - Test invalid email/password handling
    - Test OAuth flow error handling
    - Test session persistence

- [x] 4. Checkpoint - Verify authentication
  - Build and launch iOS app from `mobile/ios/`
  - Test sign-up with email/password on iOS
  - Test sign-in with email/password on iOS
  - Test Google OAuth on iOS
  - Test Apple Sign In on iOS
  - Build and launch Android app from `mobile/android/`
  - Test sign-up with email/password on Android
  - Test sign-in with email/password on Android
  - Test Google OAuth on Android
  - Verify session persistence across app restarts on both platforms
  - Run all authentication tests and verify they pass
  - Create task summary in `task_summaries/task_3/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 5**


- [ ] 5. Database schema and models
  - [ ] 5.1 Create Supabase database schema with migrations
    - Create migration file in `supabase/migrations/` for users_profiles table with RLS policies
    - Create migration file for meal_logs table with RLS policies
    - Create migration file for water_logs table with RLS policies
    - Create migration file for fasting_sessions table with RLS policies
    - Create migration file for health_metrics table with RLS policies
    - Create migration file for subscriptions table with RLS policies
    - Create migration file for payment_transactions table with RLS policies
    - Create migration file for user_ai_context table with RLS policies
    - Create migration file for health_goals table with RLS policies
    - Create migration file for goal_history table with RLS policies
    - Create migration file for conversation_history table with RLS policies
    - Create migration file for oauth_tokens table with RLS policies
    - Create migration file for custom_foods table with RLS policies
    - Run `supabase db push` to apply migrations
    - _Requirements: 101.1-101.8, 102.1-102.8, 103.1-103.8, 104.1-104.9, 105.1-105.8, 115.1-115.3, 138.4, 139.1_

  - [ ] 5.2 Implement data models in KMP
    - Create UserProfile data class
    - Create MealLog data class with FoodItem and NutritionalData
    - Create WaterLog data class
    - Create FastingSession data class
    - Create HealthMetric data class
    - Create Subscription data class with UsageQuotas
    - Create UserAIContext data class with PatternProfile
    - Create HealthGoal data class with GoalContext
    - Create ConversationMessage data class
    - _Requirements: 101.2-101.5, 102.2-102.5, 103.2-103.7_

  - [ ] 5.3 Configure Supabase Storage bucket for food photos
    - Create storage bucket configuration in `supabase/` or via Supabase CLI
    - Create food-photos storage bucket
    - Configure RLS policies for user-specific access
    - Set file size limits and format validation
    - _Requirements: 106.1-106.7_

  - [ ]* 5.4 Write property test for RLS isolation
    - **Property 32: Row-Level Security Isolation**
    - **Validates: Requirements 107.3, 107.4, and all RLS requirements**

- [ ] 6. User profile and onboarding
  - [ ] 6.1 Implement profile management in KMP
    - Create ProfileManager class
    - Implement profile CRUD operations with Supabase
    - Implement unit preferences (metric/imperial)
    - _Requirements: 60.1-60.9, 87.1-87.7, 90.1-90.8_

  - [ ] 6.2 Build iOS onboarding flow (Swift)
    - Create welcome screens with feature introduction
    - Create profile setup wizard (name, age, height, weight)
    - Create health goal selection screen
    - Create tutorial walkthrough
    - Implement permission requests (camera, notifications, HealthKit)
    - _Requirements: 59.1-59.7, 60.1-60.9, 61.1-61.7, 62.1-62.8, 63.1-63.9, 64.1-64.6_

  - [ ] 6.3 Build Android onboarding flow (Kotlin/Compose)
    - Create welcome screens with feature introduction
    - Create profile setup wizard (name, age, height, weight)
    - Create health goal selection screen
    - Create tutorial walkthrough
    - Implement permission requests (camera, notifications, Health Connect)
    - _Requirements: 59.1-59.7, 60.1-60.9, 61.1-61.7, 62.1-62.8, 63.1-63.9, 64.1-64.6_

  - [ ]* 6.4 Write unit tests for profile validation
    - Test age validation (13-120)
    - Test height/weight validation
    - Test unit conversion accuracy

- [ ] 7. Subscription system
  - [ ] 7.1 Implement subscription models in KMP
    - Create SubscriptionTier enum (Free, Pro)
    - Create SubscriptionManager class
    - Implement quota tracking logic
    - _Requirements: 13.1-13.6, 14.1-14.10, 15.1-15.6, 16.1-16.5_

  - [ ]* 7.2 Write property test for quota tracking accuracy
    - **Property 15: Usage Quota Tracking Accuracy**
    - **Validates: Requirements 17.1, 17.2**

  - [ ] 7.3 Integrate iOS In-App Purchase
    - Implement StoreKit integration for subscription purchase
    - Handle purchase flow and receipt validation
    - Implement purchase restoration
    - _Requirements: 65.1-65.6, 66.1-66.8, 67.1-67.7, 68.1-68.8, 71.1-71.8_

  - [ ] 7.4 Integrate Google Play Billing
    - Implement Play Billing Library for subscription purchase
    - Handle purchase flow and receipt validation
    - Implement purchase restoration
    - _Requirements: 65.1-65.6, 66.1-66.8, 67.1-67.7, 68.1-68.8, 71.1-71.8_

  - [ ] 7.5 Build subscription management UI (iOS and Android)
    - Create subscription tier comparison screen
    - Create payment method management screen
    - Create subscription status and renewal screen
    - Display usage quotas and limits
    - _Requirements: 15.1-15.6, 66.1-66.8, 67.1-67.7_

  - [ ]* 7.6 Write unit tests for subscription flows
    - Test quota enforcement at boundaries
    - Test upgrade/downgrade flows
    - Test trial period handling

- [ ] 8. Checkpoint - Verify core infrastructure
  - Build and launch both iOS and Android apps
  - Test profile creation and updates on both platforms
  - Test subscription purchase flow on both platforms
  - Test quota tracking and enforcement
  - Verify all data persists correctly in Supabase
  - Run all tests and verify they pass
  - Create task summary in `task_summaries/task_5-7/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 9**


- [ ] 9. AI integration with Amazon Bedrock
  - [ ] 9.1 Implement Bedrock client in KMP
    - Create BedrockClient class with Claude AI integration
    - Implement request/response handling
    - Implement retry logic with exponential backoff
    - Implement rate limiting and cost tracking
    - _Requirements: 109.1-109.8, 135.1-135.7_

  - [ ] 9.2 Implement AI image analyzer in KMP
    - Create AIImageAnalyzer class
    - Implement food identification from images using Claude
    - Implement nutritional estimation
    - Return confidence scores
    - _Requirements: 110.1-110.8_

  - [ ] 9.3 Implement AI text processor in KMP
    - Create AITextProcessor class
    - Implement meal description parsing using Claude
    - Extract food items, quantities, and preparation methods
    - _Requirements: 111.1-111.8_

  - [ ] 9.4 Implement AI speech transcriber in KMP
    - Create AISpeechTranscriber class
    - Implement audio transcription using Claude
    - Pass transcribed text to AITextProcessor
    - _Requirements: 112.1-112.8_

  - [ ] 9.5 Implement AI conversation engine in KMP
    - Create AIConversationEngine class
    - Implement context loading and management
    - Implement Amigo personality instructions
    - Implement response generation with Claude
    - _Requirements: 113.1-113.8, 128.1-128.8_

  - [ ] 9.6 Implement AI insight generator in KMP
    - Create AIInsightGenerator class
    - Implement health data analysis using Claude
    - Generate daily, weekly, and monthly insights
    - _Requirements: 114.1-114.8_

  - [ ]* 9.7 Write property test for AI identification correction
    - **Property 33: AI Identification Correction**
    - **Validates: Requirements 110.8**

  - [ ]* 9.8 Write unit tests for AI error handling
    - Test AI service unavailability fallbacks
    - Test timeout handling
    - Test malformed response handling

- [ ] 10. Meal logging - Image-based
  - [ ] 10.1 Implement image logger in KMP
    - Create ImageLogger class
    - Implement photo upload to Supabase Storage
    - Integrate with AIImageAnalyzer
    - Create MealLog with AI results
    - _Requirements: 10.1-10.5_

  - [ ] 10.2 Build iOS camera interface (Swift)
    - Create camera capture screen
    - Implement photo preview and confirmation
    - Handle camera permissions
    - Display AI analysis results
    - _Requirements: 10.1-10.5_

  - [ ] 10.3 Build Android camera interface (Kotlin/Compose)
    - Create camera capture screen using CameraX
    - Implement photo preview and confirmation
    - Handle camera permissions
    - Display AI analysis results
    - _Requirements: 10.1-10.5_

  - [ ]* 10.4 Write property test for meal log creation
    - **Property 10: Image-Based Meal Log Creation**
    - **Validates: Requirements 10.1, 10.2**

  - [ ]* 10.5 Write unit tests for image upload
    - Test upload success and failure
    - Test file size limits
    - Test format validation

- [ ] 11. Meal logging - Voice-based
  - [ ] 11.1 Implement voice logger in KMP
    - Create VoiceLogger class
    - Integrate with AISpeechTranscriber
    - Create MealLog with parsed results
    - _Requirements: 11.1-11.5_

  - [ ] 11.2 Build iOS voice recording interface (Swift)
    - Create voice recording screen
    - Implement audio recording with AVFoundation
    - Handle microphone permissions
    - Display transcription and parsing results
    - _Requirements: 11.1-11.5_

  - [ ] 11.3 Build Android voice recording interface (Kotlin/Compose)
    - Create voice recording screen
    - Implement audio recording with MediaRecorder
    - Handle microphone permissions
    - Display transcription and parsing results
    - _Requirements: 11.1-11.5_

  - [ ]* 11.4 Write property test for voice transcription
    - **Property 12: Voice Input Transcription**
    - **Validates: Requirements 11.1, 11.2**

- [ ] 12. Meal logging - Text-based
  - [ ] 12.1 Implement text logger in KMP
    - Create TextLogger class
    - Integrate with AITextProcessor
    - Create MealLog with parsed results
    - _Requirements: 12.1-12.5_

  - [ ] 12.2 Build text input UI (iOS and Android)
    - Create text entry screen
    - Implement natural language input field
    - Display parsing results and confirmation
    - _Requirements: 12.1-12.5_

  - [ ]* 12.3 Write property test for text parsing
    - **Property 13: Text Meal Description Parsing**
    - **Validates: Requirements 12.1**

- [ ] 13. Checkpoint - Verify meal logging
  - Build and launch both iOS and Android apps
  - Test image-based meal logging on both platforms
  - Test voice-based meal logging on both platforms
  - Test text-based meal logging on both platforms
  - Verify AI analysis produces accurate results
  - Verify meal logs are created and stored correctly
  - Test quota enforcement for Free tier users
  - Run all meal logging tests and verify they pass
  - Create task summary in `task_summaries/task_9-12/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 14**


- [ ] 14. USDA FoodData Central integration
  - [ ] 14.1 Implement USDA API client in KMP
    - Create USDAClient class
    - Implement food search functionality
    - Implement food details retrieval
    - Implement caching strategy (30-day cache)
    - _Requirements: 31.1-31.6, 32.1-32.6_

  - [ ] 14.2 Implement nutrition enricher in KMP
    - Create NutritionEnricher class
    - Query USDA API for identified foods
    - Prioritize USDA data over AI estimates
    - Add data source indicators
    - _Requirements: 36.1-36.6, 37.1-37.5_

  - [ ] 14.3 Build food search UI (iOS and Android)
    - Create food search screen
    - Display search results with categories
    - Show detailed nutritional information
    - Allow food selection for meal logs
    - _Requirements: 32.1-32.6_

  - [ ]* 14.4 Write property test for data source prioritization
    - **Property 21: Data Source Prioritization**
    - **Validates: Requirements 37.6**

  - [ ]* 14.5 Write unit tests for USDA integration
    - Test API error handling
    - Test caching behavior
    - Test rate limiting

- [ ] 15. Barcode scanning
  - [ ] 15.1 Implement barcode API client in KMP
    - Create BarcodeClient class
    - Integrate with Open Food Facts API
    - Implement fallback to UPC Database API
    - Implement caching (90-day cache)
    - _Requirements: 34.1-34.6_

  - [ ] 15.2 Build iOS barcode scanner (Swift)
    - Create barcode scanning screen with AVFoundation
    - Implement UPC/EAN barcode decoding
    - Display product information
    - Allow serving quantity input
    - _Requirements: 33.1-33.6, 35.1-35.6_

  - [ ] 15.3 Build Android barcode scanner (Kotlin/Compose)
    - Create barcode scanning screen with ML Kit
    - Implement UPC/EAN barcode decoding
    - Display product information
    - Allow serving quantity input
    - _Requirements: 33.1-33.6, 35.1-35.6_

  - [ ] 15.4 Implement manual barcode entry UI (iOS and Android)
    - Create manual entry screen
    - Validate barcode format
    - Query barcode API
    - _Requirements: 38.1-38.5_

  - [ ]* 15.5 Write property test for barcode decoding
    - **Property 19: Barcode Decoding**
    - **Validates: Requirements 34.2**

  - [ ]* 15.6 Write unit tests for barcode scanning
    - Test barcode format validation
    - Test API fallback logic
    - Test serving quantity scaling

- [ ] 16. Custom foods
  - [ ] 16.1 Implement custom food management in KMP
    - Create CustomFoodManager class
    - Implement CRUD operations for custom foods
    - Store in Supabase custom_foods table
    - _Requirements: 40.1-40.6_

  - [ ] 16.2 Build custom food UI (iOS and Android)
    - Create custom food creation screen
    - Create custom food list screen
    - Allow editing and deletion
    - Include custom foods in search results
    - _Requirements: 40.1-40.6_

- [ ] 17. Water tracking
  - [ ] 17.1 Implement water tracker in KMP
    - Create WaterTracker class
    - Implement water log creation
    - Implement daily goal management
    - Calculate hydration progress
    - Implement daily reset at midnight
    - _Requirements: 43.1-43.8, 44.1-44.7, 45.1-45.6_

  - [ ]* 17.2 Write property test for water log and progress
    - **Property 22: Water Log Creation and Progress Update**
    - **Validates: Requirements 44.1, 44.6**

  - [ ] 17.3 Build water logging UI (iOS and Android)
    - Create water logging screen with quick-add buttons
    - Display daily progress with visual indicator
    - Show remaining volume to goal
    - Allow custom volume entry
    - _Requirements: 43.1-43.8, 45.1-45.6_

  - [ ] 17.4 Implement water reminders in KMP
    - Create WaterReminderScheduler class
    - Implement reminder frequency and active hours
    - Reset timer on water log creation
    - _Requirements: 46.1-46.7_

  - [ ] 17.5 Implement water history and trends in KMP
    - Create HydrationHistoryManager class
    - Calculate daily averages and trends
    - Generate 7-day and 30-day history
    - _Requirements: 47.1-47.7_

  - [ ] 17.6 Build water history UI (iOS and Android)
    - Create history screen with charts
    - Display goal achievement indicators
    - Show detailed logs by date
    - _Requirements: 47.1-47.7_

  - [ ]* 17.7 Write unit tests for water tracking
    - Test midnight reset logic
    - Test goal achievement detection
    - Test reminder scheduling

- [ ] 18. Checkpoint - Verify nutrition and water features
  - Build and launch both iOS and Android apps
  - Test USDA food search functionality
  - Test barcode scanning on both platforms
  - Test water logging and goal tracking
  - Test water reminder notifications
  - Verify nutritional data accuracy
  - Run all nutrition and water tests and verify they pass
  - Create task summary in `task_summaries/task_14-17/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 19**


- [ ] 19. Fasting tracking
  - [ ] 19.1 Implement fasting tracker in KMP
    - Create FastingTracker class
    - Implement fasting session start/stop
    - Implement fasting timer with real-time updates
    - Support fasting protocols (16:8, 18:6, 20:4, custom)
    - Calculate fasting duration
    - _Requirements: 48.1-48.8, 49.1-49.8, 50.1-50.6_

  - [ ]* 19.2 Write property test for fasting duration calculation
    - **Property 24: Fasting Session Duration Calculation**
    - **Validates: Requirements 49.1, 49.4**

  - [ ] 19.3 Implement fasting streak calculator in KMP
    - Create FastingStreakCalculator class
    - Increment streak on goal completion
    - Reset streak on missed days
    - Track longest streak
    - _Requirements: 51.1-51.8_

  - [ ]* 19.4 Write property test for fasting streak logic
    - **Property 25: Fasting Streak Increment**
    - **Property 26: Fasting Streak Reset**
    - **Validates: Requirements 52.5, 52.6**

  - [ ] 19.5 Build fasting UI (iOS and Android)
    - Create fasting timer screen with protocol selection
    - Display elapsed time and progress
    - Show start/stop controls
    - Display fasting history and streaks
    - _Requirements: 48.1-48.8, 49.1-49.8, 50.1-50.6, 51.1-51.8_

  - [ ] 19.6 Implement fasting notifications
    - Create FastingNotificationManager class
    - Send notification when fasting goal reached
    - Allow notification preferences configuration
    - _Requirements: 52.1-52.6_

  - [ ] 19.7 Implement fasting and meal log integration
    - Detect meals during fasting sessions
    - Prompt to end fast when meal logged
    - Display fasting/eating windows in timeline
    - _Requirements: 53.1-53.5_

  - [ ] 19.8 Implement fasting statistics in KMP
    - Calculate total sessions, average duration, completion rate
    - Track longest single fast
    - Calculate weekly frequency
    - _Requirements: 56.1-56.7_

  - [ ]* 19.9 Write unit tests for fasting tracking
    - Test timer accuracy
    - Test protocol validation
    - Test streak calculation edge cases

- [ ] 20. Health platform integrations
  - [ ] 20.1 Implement platform connector base in KMP
    - Create PlatformConnector interface
    - Implement OAuth token storage and management
    - Implement sync scheduling (daily automatic sync)
    - _Requirements: 21.1-21.6, 29.1-29.5_

  - [ ] 20.2 Implement Fitbit integration in KMP
    - Create FitbitIntegration class
    - Implement OAuth 2.0 flow
    - Fetch steps, heart rate, sleep, exercise data
    - Respect rate limits (150 req/hour)
    - _Requirements: 22.1-22.5_

  - [ ] 20.3 Implement Garmin integration in KMP
    - Create GarminIntegration class
    - Implement OAuth 1.0a flow
    - Fetch steps, heart rate, sleep, activities
    - _Requirements: 23.1-23.5_

  - [ ] 20.4 Implement Apple Health integration (iOS)
    - Create AppleHealthIntegration class in iOS source set
    - Use HealthKit framework
    - Request permissions per data type
    - Fetch steps, heart rate, sleep, workouts
    - _Requirements: 24.1-24.5_

  - [ ] 20.5 Implement Google Health Connect integration (Android)
    - Create GoogleHealthIntegration class in Android source set
    - Use Health Connect API
    - Request permissions per data type
    - Fetch steps, heart rate, sleep, exercise
    - _Requirements: 25.1-25.5_

  - [ ] 20.6 Implement health metrics storage in KMP
    - Create MetricStore class
    - Store metrics in Supabase health_metrics table
    - Prevent duplicates with unique constraints
    - _Requirements: 26.1-26.5_

  - [ ] 20.7 Build health platform connection UI (iOS and Android)
    - Create platform selection screen
    - Implement OAuth flows for each platform
    - Display connection status
    - Show last sync time
    - Allow manual sync trigger
    - Allow disconnection
    - _Requirements: 21.1-21.6, 29.1-29.5_

  - [ ] 20.8 Build health metrics display UI (iOS and Android)
    - Create metrics dashboard
    - Display steps, heart rate, sleep, exercise
    - Indicate source platform for each metric
    - _Requirements: 27.1-27.5_

  - [ ]* 20.9 Write unit tests for health platform sync
    - Test OAuth flow error handling
    - Test duplicate prevention
    - Test sync retry logic

- [ ] 21. Checkpoint - Verify fasting and health integrations
  - Build and launch both iOS and Android apps
  - Test fasting timer and streak tracking
  - Test fasting notifications
  - Test health platform connections (Fitbit, Garmin, Apple Health, Google Health Connect)
  - Test health data sync from connected platforms
  - Verify metrics display correctly in the app
  - Run all fasting and health integration tests and verify they pass
  - Create task summary in `task_summaries/task_19-20/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 22**


- [ ] 22. AI personalization and context management
  - [ ] 22.1 Implement AI context storage models in KMP
    - Create UserAIContext data class with PatternProfile
    - Create ConversationMessage data class
    - Create SessionContext data class
    - _Requirements: 115.1-115.8, 116.1-116.8_

  - [ ] 22.2 Implement pattern analyzer in KMP
    - Create PatternAnalyzer class
    - Analyze meal patterns (timing, foods, portions)
    - Analyze fasting patterns (protocols, success rates)
    - Analyze hydration patterns (timing, consistency)
    - Store patterns in UserPatternProfile
    - _Requirements: 117.1-117.8, 118.1-118.8, 119.1-119.8_

  - [ ]* 22.3 Write property test for pattern profile updates
    - **Property 36: Pattern Profile Updates**
    - **Validates: Requirements 117.7**

  - [ ] 22.4 Implement preference tracker in KMP
    - Create PreferenceTracker class
    - Learn meal logging method preferences
    - Track food preferences and dietary restrictions
    - Track health goals and priorities
    - Store in UserAIContext
    - _Requirements: 120.1-120.8_

  - [ ] 22.5 Implement AI memory store in KMP
    - Create AIMemoryStore class
    - Persist UserAIContext to Supabase
    - Persist ConversationHistory to Supabase
    - Implement RLS policies for AI context data
    - _Requirements: 115.1-115.8, 116.1-116.8_

  - [ ]* 22.6 Write property test for AI context persistence
    - **Property 34: AI Context Persistence**
    - **Validates: Requirements 115.4**

  - [ ]* 22.7 Write property test for conversation history storage
    - **Property 35: Conversation History Storage**
    - **Validates: Requirements 116.4, 116.5**

  - [ ] 22.8 Implement session context loader in KMP
    - Create SessionContextLoader class
    - Load conversation history (last 20 messages)
    - Load pattern profile
    - Load recent meals, water logs, fasting sessions
    - Load health metrics
    - Load current goals
    - Optimize for Claude context window
    - _Requirements: 121.1-121.8_

  - [ ]* 22.9 Write property test for session context provision
    - **Property 37: Session Context Provision**
    - **Validates: Requirements 121.7**

  - [ ] 22.10 Implement coaching adaptation engine in KMP
    - Create CoachingAdaptationEngine class
    - Analyze user progress toward goals
    - Adjust coaching style (supportive/challenging/balanced)
    - Adjust coaching frequency
    - Store coaching preferences in UserAIContext
    - _Requirements: 122.1-122.8_

  - [ ] 22.11 Implement AI context summarization in KMP
    - Create ContextSummarizer class
    - Summarize old conversation history using Claude
    - Replace detailed data with summaries when thresholds exceeded
    - Preserve key insights
    - _Requirements: 136.1-136.8_

  - [ ]* 22.12 Write property test for AI context summarization
    - **Property 39: AI Context Summarization**
    - **Validates: Requirements 136.1, 136.4**

  - [ ]* 22.13 Write unit tests for pattern analysis
    - Test meal pattern identification
    - Test fasting pattern calculation
    - Test hydration pattern recognition

- [ ] 23. Goal-based personalization
  - [ ] 23.1 Implement health goal management in KMP
    - Create HealthGoalManager class
    - Support goal types (weight_loss, muscle_gain, maintenance, improved_energy, better_sleep)
    - Implement goal creation with start date
    - Set as active goal
    - Update user_ai_context.active_goal_id
    - _Requirements: 138.1-138.8_

  - [ ]* 23.2 Write property test for goal creation and activation
    - **Property 41: Health Goal Creation and Activation**
    - **Validates: Requirements 138.3, 138.4**

  - [ ] 23.3 Implement goal transition logic in KMP
    - Create GoalTransitionManager class
    - Generate goal summary using Claude AI
    - Create goal_history record
    - Update previous goal end_date
    - Activate new goal
    - _Requirements: 139.1-139.7, 147.1-147.8_

  - [ ]* 23.4 Write property test for goal transition history
    - **Property 42: Goal Transition History Creation**
    - **Validates: Requirements 139.2, 139.3**

  - [ ] 23.5 Implement goal context storage in KMP
    - Store goal-specific patterns in GoalContext
    - Store goal-specific progress metrics
    - Store goal-specific coaching adaptations
    - Preserve context when goal deactivated
    - _Requirements: 140.1-140.8_

  - [ ]* 23.6 Write property test for goal context preservation
    - **Property 43: Goal Context Preservation**
    - **Validates: Requirements 140.5**

  - [ ] 23.7 Implement active goal prioritization in KMP
    - Update SessionContextLoader to prioritize active goal
    - Merge goal-specific patterns with general patterns
    - Load relevant goal history
    - _Requirements: 141.1-141.8_

  - [ ]* 23.8 Write property test for active goal context loading
    - **Property 44: Active Goal Context Loading**
    - **Validates: Requirements 141.1, 141.2**

  - [ ] 23.9 Implement goal-specific pattern analysis in KMP
    - Update PatternAnalyzer for goal-specific analysis
    - Identify correlations with goal progress
    - Store in active goal's GoalContext
    - Compare to previous goal instances
    - _Requirements: 142.1-142.8_

  - [ ]* 23.10 Write property test for goal-specific pattern storage
    - **Property 45: Goal-Specific Pattern Storage**
    - **Validates: Requirements 142.1, 142.5**

  - [ ] 23.11 Implement cross-goal pattern recognition in KMP
    - Identify patterns consistent across goals
    - Recognize behaviors supporting multiple goals
    - Identify optimal goal sequencing
    - Store in general pattern profile
    - _Requirements: 148.1-148.8_

  - [ ]* 23.12 Write property test for cross-goal pattern recognition
    - **Property 51: Cross-Goal Pattern Recognition**
    - **Validates: Requirements 148.1, 148.2**

  - [ ] 23.13 Build goal management UI (iOS and Android)
    - Create goal selection screen
    - Create goal history screen
    - Display active goal on dashboard
    - Allow goal switching with transition reason
    - _Requirements: 138.1-138.8, 139.1-139.7_

- [ ] 24. Checkpoint - Verify AI personalization
  - Build and launch both iOS and Android apps
  - Test pattern analysis and context accumulation
  - Test goal creation and transitions
  - Verify goal-specific patterns are stored correctly
  - Test AI context persistence across sessions
  - Verify Amigo references user history in responses
  - Run all AI personalization tests and verify they pass
  - Create task summary in `task_summaries/task_22-23/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 25**


- [ ] 25. Amigo chat interface (Pro tier)
  - [ ] 25.1 Implement conversation engine in KMP
    - Update AIConversationEngine with full context loading
    - Implement Amigo personality instructions
    - Generate responses using Claude with context
    - Store conversation messages
    - Update AI context after each interaction
    - _Requirements: 17.1-17.5, 113.1-113.8, 123.1-123.8, 128.1-128.8_

  - [ ] 25.2 Implement proactive coaching in KMP
    - Create ProactiveCoachingEngine class
    - Analyze patterns for coaching opportunities
    - Generate proactive messages
    - Limit frequency to avoid overwhelming
    - _Requirements: 129.1-129.8_

  - [ ] 25.3 Implement feedback system in KMP
    - Create FeedbackManager class
    - Store feedback ratings with conversations
    - Analyze feedback patterns
    - Adjust coaching based on feedback
    - _Requirements: 137.1-137.8_

  - [ ]* 25.4 Write property test for feedback rating storage
    - **Property 40: Feedback Rating Storage**
    - **Validates: Requirements 137.1**

  - [ ] 25.5 Build chat UI (iOS and Android)
    - Create chat interface with message history
    - Display user and Amigo messages
    - Allow message input and sending
    - Show typing indicators during AI processing
    - Allow feedback ratings on responses
    - Lock for Free tier with upgrade prompt
    - _Requirements: 17.1-17.5, 113.1-113.8_

  - [ ]* 25.6 Write unit tests for conversation flow
    - Test message sending and receiving
    - Test context loading
    - Test Pro tier restriction

- [ ] 26. AI-powered insights and coaching
  - [ ] 26.1 Implement insight generator in KMP
    - Update AIInsightGenerator with goal-based logic
    - Generate daily, weekly, monthly insights
    - Focus insights on active goal
    - Reference goal history for comparisons
    - _Requirements: 114.1-114.8, 124.1-124.8, 146.1-146.8_

  - [ ]* 26.2 Write property test for goal-focused insights
    - **Property 49: Goal-Focused Insight Generation**
    - **Validates: Requirements 146.1, 146.2**

  - [ ] 26.3 Implement goal-based recommendations in KMP
    - Create GoalBasedRecommendationEngine class
    - Generate meal recommendations aligned with active goal
    - Generate fasting protocol suggestions for active goal
    - Generate water intake targets for active goal
    - _Requirements: 143.1-143.8, 144.1-144.8, 145.1-145.8_

  - [ ]* 26.4 Write property test for goal-based meal recommendations
    - **Property 46: Goal-Based Recommendation Alignment**
    - **Validates: Requirements 143.2, 143.3, 143.4**

  - [ ]* 26.5 Write property test for goal-based fasting suggestions
    - **Property 47: Goal-Based Fasting Protocol Suggestions**
    - **Validates: Requirements 144.2, 144.3**

  - [ ]* 26.6 Write property test for goal-based water targets
    - **Property 48: Goal-Based Water Target Adjustment**
    - **Validates: Requirements 145.2, 145.3**

  - [ ] 26.7 Implement Amigo coaching for meal logs
    - Integrate AI_Coach with meal logging
    - Provide coaching based on meal patterns
    - Reference user's meal history
    - _Requirements: 9.1-9.5, 28.1-28.5_

  - [ ] 26.8 Implement Amigo coaching for fasting
    - Integrate AI_Coach with fasting tracking
    - Provide coaching based on fasting patterns
    - Suggest protocol adjustments
    - Provide positive reinforcement for streaks
    - _Requirements: 54.1-54.7_

  - [ ] 26.9 Implement goal-specific coaching style in KMP
    - Update CoachingAdaptationEngine for goal-based styles
    - Apply appropriate tone for each goal type
    - Store goal-specific coaching preferences
    - _Requirements: 149.1-149.8_

  - [ ]* 26.10 Write property test for goal-specific coaching style
    - **Property 52: Goal-Specific Coaching Style Application**
    - **Validates: Requirements 149.2, 149.3**

  - [ ]* 26.11 Write unit tests for insight generation
    - Test insight relevance to active goal
    - Test insight generation frequency
    - Test Pro tier restriction

- [ ] 27. Dashboard and navigation
  - [ ] 27.1 Implement dashboard data aggregation in KMP
    - Create DashboardDataManager class
    - Calculate daily summary (calories, macros, water, fasting)
    - Generate activity feed (recent logs)
    - Calculate quick stats (streaks, averages, consistency)
    - Load goal-specific progress metrics
    - _Requirements: 77.1-77.7, 78.1-78.8, 79.1-79.8, 80.1-80.7, 150.1-150.8_

  - [ ]* 27.2 Write property test for goal progress tracking
    - **Property 53: Goal Progress Metrics Tracking**
    - **Validates: Requirements 150.1, 150.7**

  - [ ] 27.3 Build iOS dashboard (Swift)
    - Create main dashboard screen
    - Display daily summary with visual indicators
    - Show activity feed with recent logs
    - Display quick stats and insights
    - Show active goal and progress
    - Provide quick action buttons (log meal, water, fasting)
    - Implement navigation to all features
    - _Requirements: 77.1-77.7, 78.1-78.8, 79.1-79.8, 80.1-80.7, 81.1-81.9_

  - [ ] 27.4 Build Android dashboard (Kotlin/Compose)
    - Create main dashboard screen
    - Display daily summary with visual indicators
    - Show activity feed with recent logs
    - Display quick stats and insights
    - Show active goal and progress
    - Provide quick action buttons (log meal, water, fasting)
    - Implement navigation to all features
    - _Requirements: 77.1-77.7, 78.1-78.8, 79.1-79.8, 80.1-80.7, 81.1-81.9_

  - [ ]* 27.5 Write unit tests for dashboard
    - Test data aggregation accuracy
    - Test quick stats calculations
    - Test activity feed ordering

- [ ] 28. Checkpoint - Verify AI coaching and dashboard
  - Build and launch both iOS and Android apps
  - Test Amigo chat conversations with full context
  - Test insight generation and recommendations
  - Test dashboard displays all data correctly
  - Verify goal-based coaching and recommendations
  - Test proactive coaching messages (Pro tier)
  - Run all AI coaching and dashboard tests and verify they pass
  - Create task summary in `task_summaries/task_25-27/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 29**


- [ ] 29. Notifications system
  - [ ] 29.1 Implement notification scheduler in KMP
    - Create NotificationScheduler interface (expect/actual)
    - Implement scheduling logic for water reminders
    - Implement scheduling logic for fasting notifications
    - Implement scheduling logic for coaching prompts (Pro tier)
    - Respect quiet hours and user preferences
    - _Requirements: 72.1-72.7, 73.1-73.8, 74.1-74.7, 75.1-75.7_

  - [ ] 29.2 Implement iOS notifications (Swift)
    - Use UserNotifications framework
    - Request notification permissions
    - Implement APNs integration
    - Handle notification taps with deep links
    - _Requirements: 72.1-72.7, 76.1-76.7_

  - [ ] 29.3 Implement Android notifications (Kotlin)
    - Use NotificationManager and WorkManager
    - Request notification permissions
    - Implement FCM integration
    - Handle notification taps with deep links
    - _Requirements: 72.1-72.7, 76.1-76.7_

  - [ ] 29.4 Build notification preferences UI (iOS and Android)
    - Create notification settings screen
    - Allow enable/disable for each notification type
    - Configure water reminder frequency and active hours
    - Configure quiet hours
    - _Requirements: 73.1-73.8_

  - [ ]* 29.5 Write unit tests for notification scheduling
    - Test reminder timing
    - Test quiet hours enforcement
    - Test notification cancellation

- [ ] 30. Real-time synchronization
  - [ ] 30.1 Implement Realtime sync in KMP
    - Create RealtimeManager class
    - Subscribe to Supabase Realtime channels
    - Handle meal_logs, water_logs, fasting_sessions updates
    - Handle subscriptions, user_ai_context updates
    - Handle health_goals updates
    - _Requirements: 108.1-108.8_

  - [ ]* 30.2 Write property test for cross-device sync
    - **Property 38: Cross-Device AI Context Sync**
    - **Validates: Requirements 130.2**

  - [ ] 30.3 Implement offline queue in KMP
    - Create OfflineQueueManager class
    - Queue operations when offline
    - Sync queued operations when online
    - Handle conflict resolution (last-write-wins)
    - _Requirements: 96.1-96.8, 108.1-108.8_

  - [ ]* 30.4 Write property test for offline queueing and sync
    - **Property 31: Offline Data Queueing and Sync**
    - **Validates: Requirements 96.5, 96.6**

  - [ ]* 30.5 Write unit tests for realtime sync
    - Test channel subscription
    - Test update propagation
    - Test conflict resolution

- [ ] 31. Data export and privacy
  - [ ] 31.1 Implement data exporter in KMP
    - Create DataExporter class
    - Export all user data (meals, water, fasting, profile, goals)
    - Support JSON format
    - Support CSV format
    - Include metadata (dates, sources)
    - _Requirements: 82.1-82.10, 83.1-83.6_

  - [ ]* 31.2 Write property test for data export format validity
    - **Property 27: Data Export Format Validity**
    - **Validates: Requirements 82.3, 82.4**

  - [ ]* 31.3 Write property test for data export completeness
    - **Property 28: Data Export Completeness**
    - **Validates: Requirements 82.10**

  - [ ] 31.4 Implement account deletion in KMP
    - Create AccountDeletionManager class
    - Delete all user data from all tables
    - Delete food photos from storage
    - Revoke OAuth tokens
    - Cancel subscriptions
    - _Requirements: 84.1-84.11_

  - [ ]* 31.5 Write property test for account deletion cascade
    - **Property 29: Account Deletion Cascade**
    - **Validates: Requirements 84.4, 84.5**

  - [ ] 31.6 Build data export UI (iOS and Android)
    - Create data export screen
    - Allow format selection (JSON/CSV)
    - Allow data type selection
    - Provide download/share functionality
    - _Requirements: 82.1-82.10_

  - [ ] 31.7 Build account deletion UI (iOS and Android)
    - Create account deletion screen
    - Display warning about data loss
    - Require authentication confirmation
    - _Requirements: 84.1-84.11_

  - [ ] 31.8 Implement privacy policy and terms acceptance
    - Display privacy policy and terms during sign-up
    - Require acceptance before account creation
    - Store acceptance date and version
    - _Requirements: 85.1-85.7_

  - [ ]* 31.9 Write unit tests for data export
    - Test JSON format validity
    - Test CSV format validity
    - Test data completeness

- [ ] 32. Settings and preferences
  - [ ] 32.1 Implement settings management in KMP
    - Create SettingsManager class
    - Manage unit preferences (metric/imperial)
    - Manage language settings
    - Manage theme settings (light/dark/auto)
    - _Requirements: 87.1-87.7, 88.1-88.7, 89.1-89.8_

  - [ ]* 32.2 Write property test for unit conversion accuracy
    - **Property 30: Unit Conversion Accuracy**
    - **Validates: Requirements 87.5**

  - [ ] 32.3 Build settings UI (iOS and Android)
    - Create settings screen with sections
    - Account settings (profile, email, password)
    - Unit preferences
    - Language settings
    - Theme settings
    - Notification preferences
    - Subscription management
    - Connected health platforms
    - Data export
    - Account deletion
    - Help and support
    - About and app information
    - _Requirements: 87.1-87.7, 88.1-88.7, 89.1-89.8, 90.1-90.8, 91.1-91.8, 92.1-92.7_

  - [ ]* 32.4 Write unit tests for settings
    - Test unit conversion
    - Test theme switching
    - Test preference persistence

- [ ] 33. Checkpoint - Verify notifications, sync, and settings
  - Build and launch both iOS and Android apps
  - Test notification delivery and deep links
  - Test real-time sync across multiple devices
  - Test offline queue and sync when back online
  - Test data export (JSON and CSV formats)
  - Test account deletion flow
  - Test all settings and preferences
  - Run all notification, sync, and settings tests and verify they pass
  - Create task summary in `task_summaries/task_29-32/summary.md`
  - **STOP: Wait for user to verify both apps**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation before proceeding to task 34**


- [ ] 34. Error handling and graceful degradation
  - [ ] 34.1 Implement error handling in KMP
    - Create ErrorHandler class
    - Define error types (network, auth, API, validation, storage)
    - Implement user-friendly error messages
    - Log errors for monitoring
    - _Requirements: 95.1-95.8_

  - [ ] 34.2 Implement network connectivity handling in KMP
    - Create NetworkHandler class (expect/actual)
    - Monitor connectivity status
    - Display offline indicator
    - Enable offline features (cached data)
    - _Requirements: 96.1-96.8_

  - [ ] 34.3 Implement graceful degradation in KMP
    - Handle USDA API unavailability (use cache or AI estimates)
    - Handle Barcode API unavailability (fallback to manual search)
    - Handle Bedrock unavailability (allow manual entry)
    - Handle health platform API failures (show last sync)
    - _Requirements: 97.1-97.9, 132.1-132.8_

  - [ ] 34.4 Build error UI (iOS and Android)
    - Display error messages with clear explanations
    - Provide actionable next steps
    - Show offline indicator
    - Allow retry actions
    - _Requirements: 95.1-95.8_

  - [ ]* 34.5 Write unit tests for error handling
    - Test network error handling
    - Test API error fallbacks
    - Test validation error messages

- [ ] 35. Performance optimization
  - [ ] 35.1 Optimize AI request performance in KMP
    - Implement request caching for repeated queries
    - Implement streaming responses for long outputs
    - Set timeouts for AI requests (5 seconds)
    - Allow cancellation of long-running requests
    - _Requirements: 131.1-131.8_

  - [ ] 35.2 Optimize database queries in KMP
    - Add indexes for common queries
    - Implement pagination for large result sets
    - Cache frequently accessed data
    - _Requirements: 134.1-134.8_

  - [ ] 35.3 Optimize image uploads
    - Compress images before upload
    - Implement progress indicators
    - Set size limits (10MB)
    - _Requirements: 106.6_

  - [ ]* 35.4 Write performance tests
    - Test meal log creation time (< 3 seconds)
    - Test dashboard load time (< 2 seconds)
    - Test Amigo response time (< 3 seconds)

- [ ] 36. Accessibility
  - [ ] 36.1 Implement iOS accessibility (Swift)
    - Add VoiceOver labels and hints
    - Support Dynamic Type
    - Ensure color contrast ratios (WCAG AA)
    - Support keyboard navigation
    - _Requirements: Accessibility compliance_

  - [ ] 36.2 Implement Android accessibility (Kotlin)
    - Add TalkBack content descriptions
    - Support font scaling
    - Ensure color contrast ratios (WCAG AA)
    - Support keyboard navigation
    - _Requirements: Accessibility compliance_

- [ ] 37. Testing and quality assurance
  - [ ]* 37.1 Write remaining property-based tests
    - Property 2: Email Uniqueness Enforcement
    - Property 3: Account Creation Completeness
    - Property 6: Account Retrieval by Email
    - Property 7: Password Verification Correctness
    - Property 8: Session Token Secure Storage
    - Property 9: Session Token Validation
    - Property 11: Nutritional Data Completeness
    - Property 14: Free Tier Quota Enforcement
    - Property 16: Meal History Chronological Ordering
    - Property 17: Data Persistence Round Trip
    - Property 18: USDA Food Search
    - Property 20: Serving Quantity Scaling
    - Property 23: Water Goal Validation
    - Property 50: Goal Transition Acknowledgment
    - Property 54: Historical Goal Context Retrieval

  - [ ]* 37.2 Write integration tests
    - End-to-end meal logging flow (image → AI → USDA → storage)
    - Complete authentication flow
    - Health platform sync flow
    - Subscription purchase flow
    - Cross-device sync flow

  - [ ]* 37.3 Write UI tests (iOS and Android)
    - Test onboarding flow
    - Test meal logging workflows
    - Test water and fasting tracking
    - Test chat with Amigo
    - Test dashboard navigation

  - [ ]* 37.4 Perform security testing
    - Test SQL injection attempts
    - Test XSS attack vectors
    - Test authentication bypass attempts
    - Verify RLS policy enforcement
    - Test token theft and replay attacks

  - [ ]* 37.5 Perform performance testing
    - Load test with 1000 concurrent users
    - Test database performance under load
    - Test Realtime channel scalability
    - Measure AI service costs

- [ ] 38. Monitoring and observability
  - [ ] 38.1 Implement monitoring in KMP
    - Track error rates by feature
    - Track API response times
    - Track AI service costs
    - Track database query performance
    - Track user engagement metrics
    - _Requirements: 98.1-98.8_

  - [ ] 38.2 Set up alerting
    - Alert on error rate > 5%
    - Alert on API response time > 5 seconds
    - Alert on database query time > 2 seconds
    - Alert on AI service costs exceeding budget
    - Alert on payment processing failures
    - _Requirements: 98.1-98.8_

  - [ ] 38.3 Implement analytics tracking
    - Track feature usage
    - Track subscription conversions
    - Track meal logging method preferences
    - Track user retention

- [ ] 39. Documentation
  - [ ] 39.1 Write API documentation
    - Document KMP shared module APIs in `docs/api/`
    - Document platform-specific interfaces in `docs/api/`
    - Document data models and schemas in `docs/api/`

  - [ ] 39.2 Write deployment documentation
    - Document Supabase setup and configuration in `docs/deployment/supabase-setup.md`
    - Document Amazon Bedrock setup in `docs/deployment/bedrock-setup.md`
    - Document CloudFormation deployment in `docs/deployment/cloudformation.md`
    - Document environment variables and secrets in `docs/deployment/environment.md`
    - Document CI/CD pipeline in `docs/deployment/ci-cd.md`

  - [ ] 39.3 Write user documentation
    - Create in-app help content
    - Create FAQ in `docs/user/faq.md`
    - Create feature tutorials in `docs/user/tutorials/`
    - _Requirements: 91.1-91.8_

- [ ] 40. Final checkpoint and deployment preparation
  - Build final production versions for iOS and Android
  - Run full test suite (unit, property, integration, UI) and verify all pass
  - Test all features on both iOS and Android platforms
  - Test on multiple device sizes (phones, tablets) and OS versions
  - Verify accessibility compliance (VoiceOver, TalkBack, color contrast)
  - Review and verify security compliance (encryption, RLS, authentication)
  - Verify GDPR compliance (data export, account deletion, privacy policy)
  - Test complete subscription flows end-to-end (purchase, upgrade, downgrade, restore)
  - Verify AI costs are within budget limits
  - Perform final user acceptance testing
  - Create final task summary in `task_summaries/task_40/summary.md`
  - **STOP: Wait for user to verify both apps for production readiness**
  - **After approval: Ask for git commit message, commit changes, and push**
  - **STOP: Wait for user confirmation for production deployment**

## Notes

- **CRITICAL**: After completing EACH task, you must build and launch the app to verify it works before proceeding
- **CRITICAL**: Create a task summary in `task_summaries/task_N/summary.md` documenting what was done
- **CRITICAL**: After user verifies both apps, ask for git commit message, commit, and push
- **CRITICAL**: Wait for explicit user approval before moving to the next task
- **Project Structure**:
  - `mobile/ios/` - iOS app code (Swift/SwiftUI)
  - `mobile/android/` - Android app code (Kotlin/Compose)
  - `mobile/shared/` - Kotlin Multiplatform shared code
  - `supabase/` - Supabase configuration (created by `supabase init`)
    - `supabase/migrations/` - Database migration files
    - `supabase/functions/` - Edge functions (if needed)
    - `supabase/config.toml` - Supabase project configuration
  - `infrastructure/` - AWS CloudFormation templates (Bedrock only)
  - `docs/infrastructure/` - Infrastructure setup documentation (AWS Bedrock, Supabase)
  - `docs/` - All documentation
  - `task_summaries/` - Task completion summaries
- **Backend Services**:
  - Supabase: Managed via Supabase CLI - migrations in `supabase/migrations/`
  - AWS Bedrock: Managed via CloudFormation in `infrastructure/bedrock.yaml`
- Tasks marked with `*` are optional testing tasks and can be skipped for faster MVP delivery
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation throughout development
- Property tests validate universal correctness properties across all inputs
- Unit tests validate specific examples, edge cases, and error conditions
- The implementation uses Kotlin Multiplatform for shared business logic, reducing code duplication
- Native UIs (Swift for iOS, Kotlin/Compose for Android) provide optimal user experience
- Supabase provides backend infrastructure (auth, database, storage, realtime)
- Amazon Bedrock with Claude AI powers all AI features
- Goal-based personalization allows Amigo to focus coaching on the user's current health objective
- AI context accumulates over time, making Amigo progressively more knowledgeable about each user


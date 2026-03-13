# Shared Module Architecture (Kotlin Multiplatform)

## Overview

The shared module contains all business logic, data models, and API integrations for the Amigo health coaching app. Built with Kotlin Multiplatform (KMP), it provides a single source of truth for both iOS and Android platforms.

## Core Architecture Patterns

### 1. Factory Pattern
All major components use factory objects for instantiation:
- `AuthFactory` - Authentication components (EmailAuthenticator, OAuthAuthenticator, SessionManager)
- `BedrockClientFactory` - Bedrock AI client with singleton pattern
- `AmigoAgentConversationFactory` - Agent conversation instances
- `ProfileManagerFactory` - Profile management
- `SessionInitializerFactory` - Session initialization with shared cache
- `SubscriptionManagerFactory` - Subscription management

**Benefits**: Centralized dependency management, easier testing, consistent initialization

### 2. Repository Pattern
Data access is abstracted through manager classes:
- `ProfileManager` - User profile CRUD operations
- `SessionManager` - Authentication session management
- `SubscriptionManager` - Subscription state management

### 3. Action Group Pattern (Bedrock Integration)
AI function calls are organized into action groups:
- `ActionGroupRegistry` - Central registry for all action groups
- `DataOperationsActionGroup` - Profile and onboarding data operations
- `HealthCalculationsActionGroup` - BMR, TDEE, goal validation calculations
- `GoalManagementActionGroup` - Health goal persistence

**Flow**: Bedrock Agent → RETURN_CONTROL → Client executes → Returns results → Agent continues

## Module Structure

```
shared/src/commonMain/kotlin/com/amigo/shared/
├── ai/                          # AI/Bedrock integration
│   ├── actions/                 # Action groups for Bedrock Agent
│   │   ├── ActionGroupRegistry.kt
│   │   ├── DataOperationsActionGroup.kt
│   │   ├── HealthCalculationsActionGroup.kt
│   │   └── GoalManagementActionGroup.kt
│   ├── models/                  # AI-specific data models
│   ├── sessions/                # Session configurations
│   │   ├── OnboardingSessionConfig.kt
│   │   └── GoalSettingSessionConfig.kt
│   ├── AmigoAgentConversation.kt
│   ├── BedrockClient.kt
│   ├── HealthMetricsCalculator.kt
│   └── SessionConfigLoader.kt
│
├── auth/                        # Authentication
│   ├── models/                  # Auth data models
│   ├── AuthFactory.kt           # Factory for auth components
│   ├── EmailAuthenticator.kt    # Email/password auth
│   ├── OAuthAuthenticator.kt    # OAuth (Google, Apple)
│   ├── SessionManager.kt        # Session lifecycle management
│   └── SecureStorage.kt         # Platform-specific secure storage (expect/actual)
│
├── data/                        # Data layer
│   ├── models/                  # Domain models
│   │   ├── UserProfile.kt
│   │   ├── MealLog.kt
│   │   ├── FastingSession.kt
│   │   ├── HealthMetric.kt
│   │   ├── Subscription.kt
│   │   └── ConversationMessage.kt
│   ├── repositories/            # Data repositories
│   └── SupabaseClient.kt        # Supabase client provider
│
├── goals/                       # Goal planning
│   ├── GoalCalculationEngine.kt
│   ├── GoalPlanningConversationEngine.kt
│   ├── ManualGoalPlanningManager.kt
│   └── ProgressProjectionGenerator.kt
│
├── profile/                     # Profile management
│   ├── ProfileManager.kt
│   ├── ProfileManagerFactory.kt
│   ├── ProfileUpdate.kt
│   └── UnitConverter.kt
│
├── session/                     # Session initialization (NEW)
│   ├── SessionInitializer.kt
│   ├── SessionInitializerFactory.kt
│   └── ProfileCache.kt
│
├── subscription/                # Subscription management
│   ├── SubscriptionManager.kt
│   └── SubscriptionManagerFactory.kt
│
├── config/                      # Configuration
│   └── AppConfig.kt
│
└── utils/                       # Utilities
    ├── CurrentTime.kt
    ├── Logger.kt
    └── TimeProvider.kt
```

## Key Data Flows

### 1. Authentication Flow
```
User Input → EmailAuthenticator/OAuthAuthenticator → Supabase Auth
→ SessionManager.setSessionFromTokens() → SecureStorage (platform-specific)
→ SessionManager.isAuthenticated (StateFlow) → UI updates
```

### 2. Profile Loading Flow (NEW)
```
App Startup → SessionInitializer.initialize(userId)
→ Check ProfileCache (5min TTL)
→ If cache hit: Instant routing + background refresh
→ If cache miss: ProfileManager.getProfileOrThrow() with retry (3x, exponential backoff)
→ Determine route based on onboarding_completed flag
→ Return RouteDecision (MainApp or Onboarding with SessionConfig)
```

### 3. AI Conversation Flow (RETURN_CONTROL)
```
User Message → BedrockClient.invokeAgent()
→ Bedrock Agent processes → Returns RETURN_CONTROL with function invocations
→ Client executes functions via ActionGroupRegistry
→ Client returns results → BedrockClient.invokeAgent() with returnControlInvocationResults
→ Agent continues → Returns completion text
```

### 4. Goal Setting Flow
```
User Input → GoalPlanningConversationEngine
→ HealthMetricsCalculator (BMR, TDEE, daily calories)
→ GoalManagementActionGroup.saveGoal()
→ ProfileManager.updateGoal() → Supabase (users_profiles + health_goals tables)
```

## Dependencies

### External Libraries
- **Supabase KT 3.4.1**: Backend SDK (auth, postgrest, storage, realtime)
- **Ktor 3.4.0**: HTTP client for Bedrock proxy
- **kotlinx-serialization 1.6.2**: JSON serialization
- **kotlinx-coroutines 1.7.3**: Async operations
- **kotlinx-datetime 0.6.1**: Date/time handling

### Internal Dependencies
- All factories depend on `SupabaseClientProvider`
- Action groups depend on `ProfileManager` and `SupabaseClient`
- `SessionInitializer` depends on `ProfileManager` and `ProfileCache`
- `AmigoAgentConversation` depends on `BedrockClient` and `SessionManager`

## Platform-Specific Code (expect/actual)

### SecureStorage
- **Android**: Uses `EncryptedSharedPreferences` (androidx.security)
- **iOS**: Uses `Keychain` (Security framework)

### Synchronized
- **Android/JVM**: Uses `@Synchronized` annotation
- **iOS/Native**: Uses platform-specific locking mechanisms

## Unused/Deprecated Code

### Identified Issues
1. **BedrockClient.analyzeImage()**: Marked as TODO, not yet supported by Lambda proxy
2. **BedrockClient.invokeModelStreaming()**: Marked as TODO, falls back to regular invoke
3. **MealLogRepository**: Empty implementation with TODO comment
4. **Greeting.kt / Platform.kt**: Sample KMP files, not used in production

### Cleanup Opportunities
1. Remove `Greeting.kt` and `Platform.kt` if not needed
2. Implement or remove `MealLogRepository` stub
3. Complete streaming support in BedrockClient or remove method
4. Complete image analysis support or remove method

## Testing Strategy

### Unit Tests (Optional tasks in spec)
- Factory singleton behavior
- State mapping in ViewModels
- Calculation accuracy in HealthMetricsCalculator

### Property-Based Tests (Optional tasks in spec)
- Cache TTL behavior
- Retry logic with exponential backoff
- Routing decisions based on profile state

### Integration Tests
- End-to-end authentication flows
- Profile loading with cache behavior
- AI conversation with RETURN_CONTROL

## Performance Considerations

### Caching
- **ProfileCache**: 5-minute TTL, reduces network calls on app startup
- **SupabaseClient**: Singleton pattern, reuses connection pool
- **BedrockClient**: Singleton pattern with configurable retry logic

### Retry Logic
- **SessionInitializer**: 3 retries with exponential backoff (500ms, 1000ms, 2000ms)
- **BedrockClient**: Configurable max retries (default 3)

### Background Operations
- Profile refresh happens in background after cache hit
- Coroutines with Dispatchers.Default for non-blocking operations

## Security

### Token Management
- Access tokens stored in platform-specific secure storage
- Tokens passed via `X-Amigo-Auth` header to Bedrock proxy
- Lambda verifies tokens with Supabase `/auth/v1/user` endpoint

### RLS (Row Level Security)
- All Supabase queries respect RLS policies
- User ID extracted from JWT for authorization
- Action groups receive authenticated `ActionContext`

## Future Enhancements

1. **Streaming Support**: Complete streaming implementation in BedrockClient
2. **Image Analysis**: Implement image analysis for meal logging
3. **Offline Support**: Add local database with sync mechanism
4. **Real-time Updates**: Leverage Supabase Realtime for live data
5. **Meal Logging**: Complete MealLogRepository implementation

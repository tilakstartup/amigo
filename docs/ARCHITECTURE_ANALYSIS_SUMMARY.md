# Architecture Analysis Summary

**Date**: 2026-03-12  
**Scope**: Complete codebase analysis across mobile (KMP/iOS/Android), infrastructure (AWS CDK), and database (Supabase)

## Executive Summary

The Amigo health coaching app follows a clean, well-structured architecture with clear separation of concerns:
- **Shared Business Logic**: Kotlin Multiplatform module (single source of truth)
- **Platform UI**: Native SwiftUI (iOS) and Jetpack Compose (Android)
- **Backend**: Supabase (PostgreSQL, Auth, Storage, Realtime)
- **AI**: AWS Bedrock (Claude 3 Haiku) with custom agent and action groups
- **Infrastructure**: AWS CDK (TypeScript) for IaC

## Architecture Patterns

### 1. Factory Pattern (Shared Module)
All major components use factory objects for centralized dependency management:
- `AuthFactory` - Authentication components
- `BedrockClientFactory` - AI client (singleton)
- `ProfileManagerFactory` - Profile management
- `SessionInitializerFactory` - Session initialization (NEW)
- `SubscriptionManagerFactory` - Subscription management

### 2. MVVM (Mobile Platforms)
Both iOS and Android follow MVVM with reactive state management:
- **Android**: ViewModel + StateFlow + Jetpack Compose
- **iOS**: ObservableObject + @Published + SwiftUI

### 3. RETURN_CONTROL Pattern (AI Integration)
Bedrock Agent uses RETURN_CONTROL for client-side function execution:
1. Agent identifies function to call
2. Returns control to client with invocation details
3. Client executes function locally (via ActionGroupRegistry)
4. Client returns results to agent
5. Agent continues conversation

### 4. Repository Pattern (Data Access)
Manager classes abstract data access:
- `ProfileManager` - User profiles (Supabase)
- `SessionManager` - Authentication sessions
- `SubscriptionManager` - Subscription state

## Key Data Flows

### Authentication Flow
```
User Input → Authenticator → Supabase Auth → SessionManager
→ SecureStorage (Keychain/EncryptedSharedPreferences)
→ StateFlow/Published → UI Update
```

### Session Initialization Flow (NEW)
```
App Startup → SessionInitializer.initialize(userId)
→ ProfileCache check (5min TTL)
→ Cache hit: Instant routing + background refresh
→ Cache miss: ProfileManager.getProfileOrThrow() with retry (3x exponential backoff)
→ Determine route (onboarding_completed flag)
→ RouteDecision (MainApp or Onboarding)
```

### AI Conversation Flow
```
User Message → BedrockClient → Lambda Proxy → Bedrock Agent
→ RETURN_CONTROL with invocations
→ Client executes via ActionGroupRegistry
→ Client returns results → Agent continues
→ Completion text → UI
```

## Module Dependencies

### Shared Module
```
ai/ → auth/, profile/, data/
auth/ → data/
profile/ → data/
session/ → profile/, data/
goals/ → ai/, profile/
subscription/ → data/
```

### Platform Apps
```
Android: MainActivity → ViewModels → Shared Module
iOS: AmigoApp → ViewModels → Shared Framework
```

### Infrastructure
```
CDK App → Bedrock Proxy Stack + Bedrock Agent Stack
Lambda → Bedrock Runtime + Supabase Auth
Agent → Action Groups (RETURN_CONTROL)
```

## Unused/Deprecated Code

### Identified Issues
1. **BedrockClient.analyzeImage()**: TODO, not implemented in Lambda
2. **BedrockClient.invokeModelStreaming()**: TODO, falls back to regular invoke
3. **MealLogRepository**: Empty stub with TODO
4. **Greeting.kt / Platform.kt**: Sample KMP files, unused in production
5. **Lambda DEPRECATED functions**: Old server-side execution code (kept for reference)

### Cleanup Recommendations
1. ✅ Remove `Greeting.kt` and `Platform.kt` if not needed
2. ✅ Implement or remove `MealLogRepository` stub
3. ✅ Complete streaming support or remove method
4. ✅ Complete image analysis or remove method
5. ✅ Remove deprecated Lambda functions (marked for reference only)

## Integration Points

### Mobile ↔ Shared Module
- **Android**: Direct Kotlin dependency
- **iOS**: Embedded framework via Gradle task

### Mobile ↔ Backend
- **Supabase**: Direct SDK integration (auth, postgrest, storage, realtime)
- **Bedrock**: Via Lambda proxy (API Gateway + JWT auth)

### Infrastructure ↔ Backend
- **Lambda**: Verifies JWT with Supabase `/auth/v1/user`
- **Agent**: Receives user context via session attributes

## Security Architecture

### Token Management
- Access tokens in platform-specific secure storage
- Tokens passed via `X-Amigo-Auth` header to Lambda
- Lambda verifies with Supabase before Bedrock invocation

### Row Level Security (RLS)
- All Supabase queries respect RLS policies
- User ID extracted from JWT for authorization
- Action groups receive authenticated `ActionContext`

### IAM Permissions
- Lambda: Minimal permissions (Bedrock + CloudWatch)
- Agent: Model invocation + S3 read (knowledge base)
- Custom Resources: Agent management APIs

## Performance Optimizations

### Caching
- **ProfileCache**: 5-minute TTL, reduces network calls
- **SupabaseClient**: Singleton, reuses connection pool
- **BedrockClient**: Singleton with configurable retry

### Retry Logic
- **SessionInitializer**: 3 retries, exponential backoff (500ms, 1s, 2s)
- **BedrockClient**: Configurable max retries (default 3)

### Background Operations
- Profile refresh after cache hit (non-blocking)
- Coroutines with Dispatchers.Default

## Testing Strategy

### Unit Tests (Optional in specs)
- Factory singleton behavior
- ViewModel state mapping
- Calculation accuracy

### Property-Based Tests (Optional in specs)
- Cache TTL behavior
- Retry logic
- Routing decisions

### Integration Tests
- End-to-end auth flows
- Profile loading with cache
- AI conversation with RETURN_CONTROL

## Technology Stack

### Mobile
- **Shared**: Kotlin 2.3.0, KMP, Ktor 3.4.0, Supabase KT 3.4.1
- **Android**: Jetpack Compose, Material 3, Gradle 8.5
- **iOS**: SwiftUI, Swift 5.9, XcodeGen

### Infrastructure
- **IaC**: AWS CDK 2.140.0, TypeScript 5.0
- **Compute**: Lambda (Python 3.11)
- **AI**: Bedrock (Claude 3 Haiku)
- **API**: API Gateway (REST)

### Backend
- **Database**: Supabase (PostgreSQL)
- **Auth**: Supabase Auth (JWT)
- **Storage**: Supabase Storage
- **Realtime**: Supabase Realtime (future)

## Recent Additions

### Session Initialization Feature (NEW)
- **Components**: SessionInitializer, ProfileCache, SessionInitializerFactory
- **Purpose**: Automatic profile loading on app startup with intelligent routing
- **Features**:
  - Cache-first strategy (5min TTL)
  - Retry logic (3x exponential backoff)
  - Background refresh
  - Routing based on onboarding_completed flag
- **Status**: Shared components complete, platform integration pending

## Known Limitations

### Current
1. No streaming support in Lambda
2. No rate limiting on API Gateway
3. Onboarding state in local storage (not synced with backend)
4. No offline support
5. Single region deployment

### Future Enhancements
1. **Streaming**: WebSocket API for real-time responses
2. **Offline**: Local database with sync (Room/CoreData)
3. **Rate Limiting**: API Gateway usage plans
4. **Multi-Region**: Deploy to multiple regions
5. **Knowledge Base**: Integrate S3 documents with Bedrock
6. **Health Integrations**: HealthKit (iOS), Health Connect (Android)
7. **Meal Logging**: Complete implementation with image analysis
8. **Push Notifications**: FCM (Android), APNs (iOS)

## Documentation Created

1. **docs/mobile/shared/ARCHITECTURE.md** - Shared module architecture
2. **docs/mobile/android/ARCHITECTURE.md** - Android app architecture
3. **docs/mobile/ios/ARCHITECTURE.md** - iOS app architecture
4. **docs/infrastructure/ARCHITECTURE.md** - AWS infrastructure architecture
5. **docs/ARCHITECTURE_ANALYSIS_SUMMARY.md** - This summary

## Recommendations for New Specs

### Leverage Existing Patterns
1. Use factory pattern for new components
2. Follow MVVM for UI features
3. Use RETURN_CONTROL for new AI functions
4. Add action groups for new AI capabilities

### Avoid Conflicts
1. Check ActionGroupRegistry before adding functions
2. Verify database schema before new tables
3. Review existing ViewModels before creating new ones
4. Check for naming conflicts in shared module

### Align with Architecture
1. Business logic in shared module
2. UI in platform-specific code
3. AI functions as action groups
4. Data access via manager classes

### Consider Performance
1. Add caching for frequently accessed data
2. Use background operations for non-critical tasks
3. Implement retry logic for network calls
4. Optimize Bedrock model selection (Haiku for speed, Sonnet for quality)

## Next Steps

1. Complete session initialization platform integration (Tasks 3-7)
2. Clean up unused code (Greeting.kt, MealLogRepository stub)
3. Implement streaming support or remove TODOs
4. Add comprehensive test coverage
5. Implement offline support with local database
6. Add health platform integrations
7. Complete meal logging feature

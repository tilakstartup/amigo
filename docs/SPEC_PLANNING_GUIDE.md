# Spec Planning Guide

**Last Updated**: March 12, 2026

This guide helps you write effective specs that align with the Amigo architecture, avoid conflicts, and leverage established patterns.

## Architecture Patterns to Leverage

### 1. Factory Pattern (Dependency Management)
All new components should use factory objects for instantiation:

```kotlin
// Good: Factory pattern
object MyComponentFactory {
    private var instance: MyComponent? = null
    
    fun create(dependency: Dependency): MyComponent {
        return instance ?: synchronized(this) {
            instance ?: MyComponent(dependency).also { instance = it }
        }
    }
}

// Bad: Direct instantiation
val component = MyComponent(dependency)
```

**When to use**: Any component that needs to be a singleton or has complex dependencies.

### 2. MVVM Pattern (Mobile UI)
Both Android and iOS follow MVVM with reactive state:

**Android**:
```kotlin
class MyViewModel : ViewModel() {
    private val _state = MutableStateFlow<MyState>(MyState.Idle)
    val state: StateFlow<MyState> = _state.asStateFlow()
    
    fun doSomething() {
        viewModelScope.launch {
            // Async work
        }
    }
}
```

**iOS**:
```swift
@MainActor
class MyViewModel: ObservableObject {
    @Published var state: MyState = .idle
    
    func doSomething() {
        Task {
            // Async work
        }
    }
}
```

**When to use**: Any feature with UI state management.

### 3. Action Group Pattern (Bedrock Integration)
New AI functions should be organized as action groups:

```kotlin
object MyActionGroup {
    suspend fun myFunction(params: Map<String, String>): Result<JsonObject> {
        return try {
            // Validate params
            val param1 = params["param1"] ?: return Result.failure(...)
            
            // Execute logic
            val result = doWork(param1)
            
            // Return JSON result
            Result.success(buildJsonObject {
                put("result", result)
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**When to use**: Any new AI capability that needs to be called from Bedrock Agent.

### 4. Repository Pattern (Data Access)
Data access should be abstracted through manager classes:

```kotlin
class MyManager(private val supabaseClient: SupabaseClient) {
    suspend fun getData(id: String): Result<MyData> {
        return try {
            val data = supabaseClient.from("my_table")
                .select()
                .eq("id", id)
                .single()
                .decodeAs<MyData>()
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**When to use**: Any feature that needs to access backend data.

## Avoiding Conflicts

### 1. Check ActionGroupRegistry Before Adding Functions
```kotlin
// In ActionGroupRegistry.kt, verify your function isn't already registered
val registry = mapOf(
    "data_operations" to DataOperationsActionGroup,
    "health_calculations" to HealthCalculationsActionGroup,
    "goal_management" to GoalManagementActionGroup,
    // Add new action groups here
)
```

### 2. Verify Database Schema Before New Tables
Check existing migrations in `supabase/migrations/`:
- `*_create_users_profiles.sql` - User profile data
- `*_create_meal_logs.sql` - Meal logging
- `*_create_health_metrics.sql` - Health tracking
- `*_create_ai_context_goals.sql` - Goal data
- `*_create_conversation_history.sql` - Chat history

### 3. Review Existing ViewModels Before Creating New Ones
Check `mobile/android/src/main/java/com/amigo/android/` and `mobile/ios/Amigo/`:
- `AuthViewModel` - Authentication state
- `AgentConversationViewModel` - AI conversations
- `SessionInitializationViewModel` - Session initialization (NEW)

### 4. Check for Naming Conflicts
- Screens: `*Screen.kt` (Android), `*View.swift` (iOS)
- ViewModels: `*ViewModel.kt/swift`
- Managers: `*Manager.kt`
- Clients: `*Client.kt`
- Factories: `*Factory.kt`

## Aligning with Architecture

### 1. Business Logic in Shared Module
```
✅ DO: Put business logic in mobile/shared/src/commonMain/kotlin/
❌ DON'T: Put business logic in platform-specific code
```

### 2. UI in Platform-Specific Code
```
✅ DO: Put UI in mobile/android/ and mobile/ios/
❌ DON'T: Put UI logic in shared module
```

### 3. AI Functions as Action Groups
```
✅ DO: Add new AI functions to action groups
❌ DON'T: Call Bedrock directly from mobile code
```

### 4. Data Access via Manager Classes
```
✅ DO: Use ProfileManager, SessionManager, etc.
❌ DON'T: Call Supabase directly from ViewModels
```

## Performance Considerations

### 1. Caching Strategy
- **ProfileCache**: 5-minute TTL for user profiles
- **SupabaseClient**: Singleton pattern reuses connection pool
- **BedrockClient**: Singleton pattern with configurable retry

**When to add caching**: For frequently accessed data that doesn't change often.

### 2. Retry Logic
- **SessionInitializer**: 3 retries with exponential backoff (500ms, 1s, 2s)
- **BedrockClient**: Configurable max retries (default 3)

**When to add retry logic**: For network operations that might fail temporarily.

### 3. Background Operations
- Use `Dispatchers.Default` for non-blocking operations
- Profile refresh happens in background after cache hit
- Coroutines automatically cancel on ViewModel clear

**When to use background operations**: For non-critical tasks that shouldn't block UI.

## Security Best Practices

### 1. Token Management
- Store tokens in platform-specific secure storage (Keychain/EncryptedSharedPreferences)
- Pass tokens via `X-Amigo-Auth` header to Lambda
- Lambda verifies tokens with Supabase `/auth/v1/user` endpoint

### 2. Row Level Security (RLS)
- All Supabase queries respect RLS policies
- User ID extracted from JWT for authorization
- Action groups receive authenticated `ActionContext`

### 3. Input Validation
- Validate all parameters in action groups
- Use Result types for error handling
- Never trust client input

## Testing Strategy

### Unit Tests (Optional in specs)
- Factory singleton behavior
- ViewModel state mapping
- Calculation accuracy

### Property-Based Tests (Optional in specs)
- Cache TTL behavior
- Retry logic with exponential backoff
- Routing decisions based on profile state

### Integration Tests
- End-to-end authentication flows
- Profile loading with cache behavior
- AI conversation with RETURN_CONTROL

## Spec Writing Checklist

Before writing a spec, verify:

- [ ] Feature doesn't conflict with existing components
- [ ] Database schema is defined (if needed)
- [ ] Action groups are registered (if AI feature)
- [ ] ViewModels follow MVVM pattern
- [ ] Business logic is in shared module
- [ ] UI is platform-specific
- [ ] Error handling uses Result types
- [ ] Caching strategy is defined (if applicable)
- [ ] Retry logic is implemented (if network calls)
- [ ] Security considerations are addressed
- [ ] Testing strategy is defined

## Common Spec Patterns

### Pattern 1: New Data Feature
1. Add database migration
2. Create data model in shared module
3. Create manager class for data access
4. Create ViewModel for UI state
5. Create screens/views for UI
6. Add action group if AI-related

### Pattern 2: New AI Capability
1. Create action group with functions
2. Register in ActionGroupRegistry
3. Add OpenAPI schema for Bedrock
4. Update Bedrock Agent instruction
5. Create ViewModel to handle responses
6. Create screens/views for UI

### Pattern 3: New Authentication Method
1. Create authenticator in shared module
2. Update AuthFactory
3. Update SessionManager
4. Create login screen/view
5. Update navigation flow
6. Add deep link handling (if OAuth)

### Pattern 4: New Health Tracking Feature
1. Add database migration
2. Create data model
3. Create manager class
4. Create action group for calculations
5. Create ViewModel for UI state
6. Create screens/views for UI

## Known Limitations

### Current
1. No streaming support in Lambda
2. No rate limiting on API Gateway
3. Onboarding state in local storage (not synced)
4. No offline support
5. Single region deployment

### Future Enhancements
1. Streaming via WebSocket API
2. Rate limiting via API Gateway usage plans
3. Offline support with local database
4. Multi-region deployment
5. Knowledge base integration with Bedrock

## Resources

- **Architecture Docs**: `docs/ARCHITECTURE_ANALYSIS_SUMMARY.md`
- **Shared Module**: `docs/mobile/shared/ARCHITECTURE.md`
- **Android App**: `docs/mobile/android/ARCHITECTURE.md`
- **iOS App**: `docs/mobile/ios/ARCHITECTURE.md`
- **Infrastructure**: `docs/infrastructure/ARCHITECTURE.md`
- **Tech Stack**: `tech.md`
- **Project Structure**: `structure.md`

## Questions?

Refer to the architecture documentation or check existing implementations for patterns.

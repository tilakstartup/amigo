# Kotlin Multiplatform Development

Expert guidance for Kotlin Multiplatform (KMP) development in the Amigo project.

## Architecture Pattern

- **Shared Logic**: All business logic, data models, and API clients live in `mobile/shared/src/commonMain/kotlin/`
- **Platform UI**: Native UI implementations in `mobile/android/` (Compose) and `mobile/ios/` (SwiftUI)
- **Expect/Actual**: Use for platform-specific implementations (e.g., secure storage, platform APIs)

## Package Structure

```
com.amigo.shared/
├── ai/              # Bedrock client, conversation engines, action groups
├── auth/            # Authentication logic (Supabase, OAuth, session management)
├── data/            # Data models, Supabase client, repositories
├── profile/         # Profile and user management
├── goals/           # Goal planning and calculation engines
├── config/          # App configuration
└── utils/           # Utilities (time, unit conversion, logging)
```

## Key Patterns

### Data Models
- Use `@Serializable` for all data classes that need JSON serialization
- Keep models in `data/models/` package
- Use kotlinx-datetime for date/time handling (not platform-specific types)

```kotlin
@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    @SerialName("created_at")
    val createdAt: Instant
)
```

### Managers
- Singleton objects for stateful services (e.g., `ProfileManager`, `SessionManager`)
- Use suspend functions for async operations
- Expose StateFlow for reactive state

```kotlin
object ProfileManager {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()
    
    suspend fun loadProfile(userId: String): Result<UserProfile> { }
}
```

### Clients
- Wrap external APIs (Supabase, Bedrock) in dedicated client classes
- Use Result<T> for error handling
- Implement retry logic with exponential backoff

```kotlin
class BedrockClient(
    private val apiEndpoint: String,
    private val getAuthToken: suspend () -> String?
) {
    suspend fun invokeAgent(...): Result<BedrockResponse> { }
}
```

## Coroutines

- Use `suspend` functions for async operations
- Launch coroutines in ViewModels (platform-specific)
- Use `withContext(Dispatchers.IO)` for blocking operations
- Prefer `Flow` and `StateFlow` for reactive streams

## Dependencies

Current versions:
- Kotlin: 2.3.0
- Coroutines: 1.7.3
- Serialization: 1.6.2
- Ktor: 3.4.0
- Supabase KT: 3.4.1
- DateTime: 0.6.1

## Platform Integration

### iOS (Swift)
- Shared framework built via Gradle: `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
- Import in Swift: `import shared`
- Kotlin suspend functions become Swift async functions
- Kotlin Flow becomes Swift AsyncSequence

### Android (Kotlin)
- Direct dependency on shared module
- Same language, seamless integration
- Use Compose for UI layer

## Common Pitfalls

- Don't use platform-specific types in commonMain (e.g., java.util.Date, NSDate)
- Don't expose mutable collections from shared code
- Always use expect/actual for platform-specific code
- Test shared code with commonTest source set

## Build Commands

```bash
# Build shared framework
cd mobile
./gradlew :shared:build

# Build iOS framework for Xcode
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# Run shared tests
./gradlew :shared:test
```

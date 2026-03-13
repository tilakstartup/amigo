# Supabase Backend Integration

Expert guidance for Supabase integration in the Amigo project.

## Architecture

Supabase provides:
- **Auth**: User authentication (email/password, OAuth providers)
- **Database**: PostgreSQL with Row Level Security (RLS)
- **Storage**: File storage for user uploads
- **Realtime**: WebSocket subscriptions for live data
- **Edge Functions**: Serverless Deno functions

## SupabaseClient Setup

Location: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/data/SupabaseClient.kt`

```kotlin
// Initialize once at app startup
SupabaseClientProvider.initialize(
    supabaseUrl = "https://your-project.supabase.co",
    supabaseKey = "your-anon-key"
)

// Get client anywhere
val client = SupabaseClientProvider.getClient()
```

## Authentication

### SessionManager Pattern
Location: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/auth/SessionManager.kt`

```kotlin
object SessionManager {
    private val supabase = SupabaseClientProvider.getClient()
    
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun getAccessToken(): String?
    fun observeAuthState(): Flow<AuthState>
}
```


### OAuth Integration
```kotlin
// Configure in SupabaseClient initialization
install(Auth) {
    scheme = "amigo"  // Deep link scheme
    host = "auth"
}

// Trigger OAuth flow
val url = supabase.auth.signInWith(Google) {
    redirectUrl = "amigo://auth/callback"
}
// Open URL in browser, handle callback in app

// Handle OAuth callback
supabase.auth.handleDeeplink(callbackUrl)
```

## Database Operations

### Using Postgrest
```kotlin
val supabase = SupabaseClientProvider.getClient()

// Insert
val profile = supabase.from("users_profiles")
    .insert(mapOf("user_id" to userId, "name" to name))
    .decodeSingle<UserProfile>()

// Select
val profiles = supabase.from("users_profiles")
    .select()
    .decodeList<UserProfile>()

// Update
supabase.from("users_profiles")
    .update(mapOf("name" to newName))
    .eq("user_id", userId)
    .execute()

// Delete
supabase.from("users_profiles")
    .delete()
    .eq("user_id", userId)
    .execute()
```


## Database Schema

Migrations in `supabase/migrations/` (timestamped):

Key tables:
- `users_profiles`: User profile data (name, age, height, weight, goals)
- `meal_logs`: Meal tracking entries
- `water_logs`: Water intake tracking
- `fasting_sessions`: Intermittent fasting sessions
- `health_metrics`: Daily health metrics
- `ai_context_goals`: User goals for AI context
- `conversation_history`: AI conversation logs
- `oauth_tokens`: OAuth provider tokens
- `custom_foods`: User-created food entries
- `subscriptions`: User subscription status

### Migration Pattern
```sql
-- 20260307000001_create_users_profiles.sql
CREATE TABLE users_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable RLS
ALTER TABLE users_profiles ENABLE ROW LEVEL SECURITY;

-- Policy: Users can only see their own profile
CREATE POLICY "Users can view own profile"
    ON users_profiles FOR SELECT
    USING (auth.uid() = user_id);
```


## Storage

```kotlin
val storage = supabase.storage.from("user-uploads")

// Upload file
val bytes = imageFile.readBytes()
storage.upload("avatars/${userId}.jpg", bytes)

// Download file
val downloadedBytes = storage.downloadAuthenticated("avatars/${userId}.jpg")

// Get public URL
val url = storage.publicUrl("avatars/${userId}.jpg")

// Delete file
storage.delete("avatars/${userId}.jpg")
```

## Realtime Subscriptions

```kotlin
val channel = supabase.realtime.createChannel("profile-changes")

channel.postgrestChangeFlow<UserProfile>(schema = "public") {
    table = "users_profiles"
    filter = "user_id=eq.$userId"
}.collect { change ->
    when (change) {
        is PostgrestAction.Insert -> handleInsert(change.record)
        is PostgrestAction.Update -> handleUpdate(change.record)
        is PostgrestAction.Delete -> handleDelete(change.oldRecord)
    }
}

channel.subscribe()
```

## Edge Functions

Location: `supabase/functions/`

Example: `health-calculations/index.ts`
```typescript
Deno.serve(async (req) => {
  const { userId, metrics } = await req.json()
  
  // Calculate BMR, TDEE, etc.
  const results = calculateHealthMetrics(metrics)
  
  return new Response(JSON.stringify(results), {
    headers: { "Content-Type": "application/json" }
  })
})
```


## Best Practices

1. **Row Level Security**: Always enable RLS on tables with user data
2. **Policies**: Create specific policies for SELECT, INSERT, UPDATE, DELETE
3. **Indexes**: Add indexes on frequently queried columns (user_id, created_at)
4. **Migrations**: Never modify existing migrations, create new ones
5. **Auth Tokens**: Store securely using platform-specific secure storage
6. **Error Handling**: Wrap Supabase calls in try-catch or Result<T>
7. **Connection Pooling**: Reuse SupabaseClient instance (singleton pattern)

## Common Patterns

### Repository Pattern
```kotlin
class ProfileRepository(private val supabase: SupabaseClient) {
    suspend fun getProfile(userId: String): Result<UserProfile> = runCatching {
        supabase.from("users_profiles")
            .select()
            .eq("user_id", userId)
            .decodeSingle<UserProfile>()
    }
    
    suspend fun updateProfile(profile: UserProfile): Result<Unit> = runCatching {
        supabase.from("users_profiles")
            .update(profile)
            .eq("user_id", profile.userId)
            .execute()
    }
}
```

## Testing

```kotlin
// Mock Supabase for testing
class MockSupabaseClient : SupabaseClient {
    override suspend fun from(table: String) = MockPostgrestClient()
}
```

## Common Issues

- **401 Unauthorized**: Token expired, call refreshSession()
- **403 Forbidden**: RLS policy blocking access
- **Connection Failed**: Check network, verify Supabase URL/key
- **Serialization Error**: Ensure @Serializable on data classes

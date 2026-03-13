# Project Structure

## Root Layout

```
amigo/
├── mobile/              # Mobile applications (KMP + native UI)
├── infrastructure/      # AWS CDK infrastructure as code
├── supabase/           # Backend configuration and migrations
├── docs/               # Project documentation
├── .kiro/              # Kiro AI assistant configuration
└── task_summaries/     # Implementation task summaries
```

## Mobile Directory (`mobile/`)

```
mobile/
├── shared/                          # Kotlin Multiplatform shared module
│   └── src/
│       └── commonMain/kotlin/com/amigo/shared/
│           ├── ai/                  # AI/Bedrock client and conversation engine
│           │   ├── actions/         # Bedrock agent action groups
│           │   └── sessions/        # Session configurations for AI flows
│           ├── auth/                # Authentication logic (Supabase, OAuth)
│           ├── data/                # Data models and Supabase client
│           │   └── models/          # Domain models (UserProfile, etc.)
│           ├── profile/             # Profile and user management
│           ├── goals/               # Goal planning and calculation engines
│           ├── config/              # App configuration
│           └── utils/               # Utilities (time, unit conversion)
│
├── android/                         # Android app (Kotlin + Compose)
│   └── src/main/java/com/amigo/android/
│       ├── auth/                    # Login, SignUp screens + ViewModel
│       ├── onboarding/              # Conversational onboarding flow
│       ├── profile/                 # Profile, goal management screens
│       ├── ui/theme/                # Material Design theme
│       └── MainActivity.kt          # Main entry point
│
└── ios/                             # iOS app (Swift + SwiftUI)
    └── Amigo/
        ├── Auth/                    # Login, SignUp views + ViewModel
        ├── Onboarding/              # Conversational onboarding flow
        ├── Profile/                 # Profile, goal management views
        └── AmigoApp.swift           # App entry point
```

## Infrastructure Directory (`infrastructure/`)

```
infrastructure/
├── lib/stacks/
│   ├── bedrock-agent/               # Bedrock agent configuration
│   │   ├── action-groups/schemas/   # OpenAPI schemas for action groups
│   │   ├── instruction.md           # Agent system instructions
│   │   └── stack.ts                 # CDK stack for Bedrock agent
│   └── bedrock-proxy/               # Lambda proxy for Bedrock API
│       ├── lambda/                  # Python Lambda function
│       └── stack.ts                 # CDK stack for proxy
├── bin/app.ts                       # CDK app entry point
└── cdk.out/                         # Synthesized CloudFormation templates
```

## Supabase Directory (`supabase/`)

```
supabase/
├── migrations/                      # Database schema migrations (timestamped)
│   ├── *_create_users_profiles.sql
│   ├── *_create_meal_logs.sql
│   ├── *_create_water_fasting.sql
│   ├── *_create_health_metrics.sql
│   ├── *_create_subscriptions.sql
│   ├── *_create_ai_context_goals.sql
│   ├── *_create_conversation_history.sql
│   ├── *_create_oauth_tokens.sql
│   └── *_create_storage_bucket.sql
├── functions/                       # Supabase Edge Functions (Deno)
│   ├── health-calculations/
│   ├── get-profile/
│   └── save-onboarding-data/
└── config.toml                      # Supabase project configuration
```

## Key Conventions

### File Organization

- **Shared Kotlin**: Package by feature (`auth/`, `profile/`, `goals/`, `ai/`)
- **Native UI**: Mirror shared structure in platform-specific directories
- **ViewModels**: Co-located with screens (Android: `*ViewModel.kt`, iOS: `*ViewModel.swift`)
- **Action Groups**: Each action group has a schema JSON and Kotlin implementation

### Naming Patterns

- **Screens/Views**: `*Screen.kt` (Android), `*View.swift` (iOS)
- **ViewModels**: `*ViewModel.kt/swift`
- **Managers**: `*Manager.kt` (e.g., `ProfileManager`, `SessionManager`)
- **Clients**: `*Client.kt` (e.g., `BedrockClient`, `SupabaseClient`)
- **Migrations**: `YYYYMMDDHHMMSS_description.sql`
- **CDK Stacks**: `stack.ts` in feature directories

### Cross-Platform Patterns

- Business logic lives in `shared/` (Kotlin Multiplatform)
- UI layer is platform-specific (SwiftUI for iOS, Compose for Android)
- Expect/actual pattern for platform-specific implementations
- Shared models use `@Serializable` for JSON serialization
- Coroutines in shared code, async/await in Swift, suspend in Kotlin

### AI Integration

- Bedrock agent instructions in `infrastructure/lib/stacks/bedrock-agent/instruction.md`
- Action group schemas in `infrastructure/lib/stacks/bedrock-agent/action-groups/schemas/`
- Action group implementations in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/actions/`
- Session configs in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/sessions/`

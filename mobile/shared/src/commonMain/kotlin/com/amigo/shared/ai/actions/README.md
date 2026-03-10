# Action Groups Implementation

This directory contains the implementation of Bedrock Agent action groups that are executed client-side using the RETURN_CONTROL mechanism.

## Architecture

When the Bedrock Agent needs to execute a function:
1. Agent returns a RETURN_CONTROL response with function invocations
2. Lambda proxy forwards these invocations to the mobile client
3. Client executes functions via `ActionGroupRegistry`
4. Results are sent back to the agent via `returnControlInvocationResults`
5. Agent continues with the results

## Implemented Action Groups

### 1. DataOperationsActionGroup

Handles profile and onboarding data operations.

**Functions:**
- `get_profile()` - Fetch authenticated user profile from Supabase
  - Returns: userId, first_name, last_name, age, weight, height, gender, activity_level
  - Handles both authenticated and unauthenticated (onboarding) scenarios
  
- `save_onboarding_data(payload_json)` - Save or update user onboarding fields
  - Accepts JSON payload with: first_name, last_name, age, weight, height, gender, activity_level, onboarding_completed
  - Updates Supabase users_profiles table
  - Returns: status, message, userId, fields_updated
  
- `get_onboarding_status()` - Check onboarding progress
  - Returns: completed, completion_percentage, completed_fields[], missing_fields[]
  - Calculates progress based on required fields

**Authentication:**
- Uses `ActionContext.userId` from Supabase session
- Supports onboarding cap for unauthenticated users
- Row-Level Security (RLS) enforced by Supabase

### 2. HealthCalculationsActionGroup

Handles BMR, TDEE, and goal validation calculations.

**Functions:**
- `calculate_bmr(weight_kg, height_cm, age, gender)` - Calculate Basal Metabolic Rate using Mifflin-St Jeor equation
  - Returns: bmr (kcal/day)
  
- `calculate_tdee(weight_kg, height_cm, age, gender, activity_level)` - Calculate Total Daily Energy Expenditure
  - Calculates BMR internally if not provided
  - Applies activity multiplier (1.2 - 1.9)
  - Returns: tdee, bmr, activity_level, multiplier (kcal/day)
  
- `validate_goal(goal_type, daily_calories)` - Validate if a goal is realistic
  - Checks calorie ranges for weight_loss (1200-2500), weight_gain (2000-4000), maintenance (1500-3500)
  - Returns: is_valid, message, goal_type, daily_calories

**Activity Levels:**
- sedentary: 1.2x BMR
- lightly_active: 1.375x BMR
- moderately_active: 1.55x BMR
- very_active: 1.725x BMR
- extra_active: 1.9x BMR

## Action Context

All action functions receive an `ActionContext` with:
- `userId`: Extracted from Supabase session (null for unauthenticated)
- `sessionId`: Unique session identifier
- `isAuthenticated`: Boolean flag
- `supabaseClient`: For database operations
- `additionalContext`: Map with cap, profileData, etc.

## Error Handling

Functions return `Result<JsonObject>`:
- Success: JSON with status="success" and data
- Failure: JSON with status="error" and error message
- Never throws exceptions - always returns a result

## Testing

Test action groups by:
1. Starting a conversation with the agent
2. Triggering function calls through natural language
3. Checking logs for function execution
4. Verifying results in Supabase

Example:
```kotlin
val conversation = AmigoAgentConversation(bedrockClient, sessionManager, supabaseClient)
conversation.startSession(
    cap = "onboarding",
    responsibilities = listOf("Collect user profile data"),
    collectData = listOf("first_name", "last_name", "age", "weight", "height", "activity_level")
)
```

## Adding New Action Groups

1. Create a new object implementing `ActionGroup` interface
2. Define functions with `ActionFunction` metadata
3. Implement `executeFunction()` to route to private functions
4. Register in `ActionGroupRegistry.init()`
5. Create OpenAPI schema in `infrastructure/lib/stacks/bedrock-agent/action-groups/schemas/`
6. Add to CDK stack in `infrastructure/lib/stacks/bedrock-agent/stack.ts`

## Schema Synchronization

Keep these in sync:
- Kotlin action group function signatures
- OpenAPI schema in infrastructure
- Agent instruction.md documentation
- This README

## Security

- Authentication via Supabase JWT tokens
- User ID extracted from session, not from parameters
- RLS policies enforce data access control
- No sensitive data in logs
- All database operations use parameterized queries

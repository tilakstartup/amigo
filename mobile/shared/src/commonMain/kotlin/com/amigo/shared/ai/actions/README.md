# Action Group Registry

This package implements the action group registry system for handling Bedrock Agent RETURN_CONTROL invocations.

## Architecture

### ActionGroupRegistry
Central registry that maps action groups and their functions to implementations. Automatically registers all action groups on initialization.

**Usage:**
```kotlin
val result = ActionGroupRegistry.executeInvocation(
    invocation = functionInvocation,
    context = ActionContext(
        userId = userId,
        sessionId = sessionId,
        isAuthenticated = true
    )
)
```

### Action Groups

#### DataOperations
Handles profile and onboarding data operations (requires authentication).

**Functions:**
- `get_profile` - Fetch authenticated user profile
- `save_onboarding_data(payload_json)` - Save/update onboarding data
- `get_onboarding_status` - Check onboarding progress

#### HealthCalculations
Handles health metric calculations (no authentication required).

**Functions:**
- `calculate_bmr(weight_kg, height_cm, age, gender)` - Calculate Basal Metabolic Rate using Mifflin-St Jeor equation
- `calculate_tdee(bmr, activity_level)` - Calculate Total Daily Energy Expenditure
- `validate_goal(goal_type, daily_calories)` - Validate if a goal is realistic

## Integration with AmigoAgentConversation

The `AmigoAgentConversation` class automatically:
1. Detects RETURN_CONTROL invocations from Bedrock Agent
2. Executes them using the ActionGroupRegistry
3. Sends results back to the agent
4. Continues the conversation with the agent's response

## Flow

```
User Message
    ↓
Bedrock Agent (decides action needed)
    ↓
RETURN_CONTROL with invocations
    ↓
ActionGroupRegistry.executeInvocation()
    ↓
Action Group Implementation
    ↓
Results sent back to Agent
    ↓
Agent continues conversation
    ↓
Response to User
```

## Adding New Action Groups

1. Create a new object implementing `ActionGroup` interface
2. Define functions in the `functions` list
3. Implement `executeFunction()` method
4. Register in `ActionGroupRegistry.init()`

Example:
```kotlin
object MyActionGroup : ActionGroup {
    override val name = "MyActionGroup"
    override val description = "My custom actions"
    
    override val functions = listOf(
        ActionFunction(
            name = "my_function",
            description = "Does something",
            parameters = listOf(
                ActionParameter("param1", "string", "Description", required = true)
            )
        )
    )
    
    override suspend fun executeFunction(
        functionName: String,
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        return when (functionName) {
            "my_function" -> myFunction(params, context)
            else -> Result.failure(IllegalArgumentException("Unknown function"))
        }
    }
    
    private suspend fun myFunction(
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        // Implementation
    }
}
```

## TODO

- Implement actual Supabase integration in DataOperationsActionGroup
- Add error handling and retry logic
- Add telemetry/logging for action executions
- Add unit tests for each action group

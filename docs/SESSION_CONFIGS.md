# Session Configuration System

## Overview

The Amigo AI health coach uses a session configuration system to define different conversation types. Instead of hardcoding conversation flows in the app code, we use YAML configuration files that define the "cap" (role/hat) the agent wears and the responsibilities it must complete.

## Architecture

### 1. YAML Configuration Files

Location: `mobile/shared/src/commonMain/resources/session-configs/`

Each YAML file defines:
- **cap**: Unique identifier for the session type
- **responsibilities**: Ordered list of tasks the agent must complete
- **collect_data**: User data fields to gather
- **collect_metrics**: Calculated metrics needed
- **initial_message**: Default message to start the session
- **notes**: Additional guidance for the agent

### 2. Kotlin Data Classes

Location: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/SessionConfig.kt`

- `SessionConfig`: Data class representing a session configuration
- `SessionConfigs`: Object containing predefined configs matching the YAML files

### 3. Agent Instructions

Location: `infrastructure/lib/stacks/bedrock-agent/instruction.md`

The agent instructions have been restructured to:
- Separate general instructions from cap-specific responsibilities
- Emphasize that ALL responsibilities must be completed before marking as "completed"
- Make it clear that function calls are MANDATORY when specified in responsibilities

## Usage

### Starting a Session with Config Name

```kotlin
// Recommended: Use predefined config by name
conversation.startSessionByName("onboarding")
```

### Starting a Session with Config Object

```kotlin
// Use a predefined config object
conversation.startSessionWithConfig(SessionConfigs.ONBOARDING)
```

### Starting a Session with Custom Parameters (Legacy)

```kotlin
// Still supported for custom sessions
conversation.startSession(
    cap = "custom_cap",
    responsibilities = listOf("task 1", "task 2"),
    collectData = listOf("field1", "field2"),
    collectMetrics = listOf("metric1"),
    initialMessage = "Let's start"
)
```

## Available Session Types

### 1. Onboarding (`onboarding.yaml`)

**Purpose**: New user profile setup

**Collects**:
- first_name, last_name
- age, weight, height, gender
- activity_level
- goal_type, goal_detail, goal_by_when

**Key Responsibilities**:
- Call `get_profile()` first if authenticated
- Ask for missing fields one at a time
- Call `save_onboarding_data()` when all fields collected
- Mark complete only after successful save

### 2. Goal Setting (`goal_setting.yaml`)

**Purpose**: Define and save health goals

**Collects**:
- goal_type (weight_loss/muscle_gain/maintenance)
- target_weight
- target_date

**Calculates**:
- BMR (Basal Metabolic Rate)
- TDEE (Total Daily Energy Expenditure)
- daily_calories
- weekly_weight_change

**Key Responsibilities**:
- Call `get_profile()` to get current metrics
- Ask for goal details
- Calculate BMR and TDEE
- Validate goal
- Present summary for confirmation
- Call `save_goal()` when user confirms
- Mark complete only after successful save

## Critical Rules

### 1. All Responsibilities Must Be Completed

The agent CANNOT set `aimofchat.status = "completed"` unless:
- ✅ Every item in the responsibilities list has been executed
- ✅ All required function calls have been made AND returned successfully
- ✅ All required data has been collected
- ✅ All save operations have completed successfully

### 2. Function Calls Are Mandatory

If a responsibility says "call save_goal()", the agent MUST:
1. Actually call the save_goal() function using RETURN_CONTROL
2. Wait for the function to return success
3. THEN set status to "completed"

The agent CANNOT:
- ❌ Claim it saved data without actually calling the save function
- ❌ Set status to "completed" before all responsibilities are done
- ❌ Skip function calls and just respond with JSON

### 3. Order Is Flexible (Unless Specified)

Unless explicitly stated in the notes, the agent can choose the natural conversational order for asking questions. However, certain responsibilities have dependencies (e.g., must call `get_profile()` before asking questions).

## Adding New Session Types

### Step 1: Create YAML File

Create a new file in `mobile/shared/src/commonMain/resources/session-configs/`:

```yaml
# meal_logging.yaml
cap: meal_logging

responsibilities:
  - Greet the user and explain meal logging
  - Ask what meal they want to log (breakfast/lunch/dinner/snack)
  - Ask for food items one at a time
  - For each food, ask for quantity
  - Calculate total calories and macros
  - Present summary and ask for confirmation
  - Call save_meal() when user confirms
  - Set status to completed after successful save

collect_data:
  - meal_type
  - food_items
  - quantities

collect_metrics:
  - total_calories
  - total_protein
  - total_carbs
  - total_fat

initial_message: "Let's log your meal together."

notes:
  - Allow user to add multiple food items
  - Provide nutritional feedback after logging
```

### Step 2: Add to SessionConfigs Object

Add the config to `SessionConfig.kt`:

```kotlin
val MEAL_LOGGING = SessionConfig(
    cap = "meal_logging",
    responsibilities = listOf(
        "Greet the user and explain meal logging",
        // ... rest of responsibilities
    ),
    collectData = listOf("meal_type", "food_items", "quantities"),
    collectMetrics = listOf("total_calories", "total_protein", "total_carbs", "total_fat"),
    initialMessage = "Let's log your meal together.",
    notes = listOf(
        "Allow user to add multiple food items",
        "Provide nutritional feedback after logging"
    )
)
```

### Step 3: Update getConfig Method

```kotlin
fun getConfig(cap: String): SessionConfig? {
    return when (cap) {
        "onboarding" -> ONBOARDING
        "goal_setting" -> GOAL_SETTING
        "meal_logging" -> MEAL_LOGGING  // Add new config
        else -> null
    }
}
```

### Step 4: Use the New Session

```kotlin
conversation.startSessionByName("meal_logging")
```

## Benefits

1. **Separation of Concerns**: Conversation logic is separate from app code
2. **Easy to Modify**: Change conversation flows without recompiling the app
3. **Version Control**: Track changes to conversation flows in YAML files
4. **Testability**: Easy to test different conversation configurations
5. **Consistency**: All sessions follow the same structure and rules
6. **Documentation**: YAML files serve as documentation for conversation flows

## Future Enhancements

- Load YAML files dynamically at runtime (currently hardcoded in Kotlin)
- Support for conditional responsibilities (if/else logic)
- Support for loops (e.g., "repeat until all food items logged")
- Validation of YAML files against a schema
- Hot-reload of configs during development

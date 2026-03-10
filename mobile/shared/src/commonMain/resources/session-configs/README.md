# Session Configuration Files

This directory contains YAML configuration files that define different conversation session types for the Amigo AI health coach.

## Structure

Each YAML file defines a session "cap" (role/hat) that the agent wears during a conversation.

### Required Fields

- `cap`: Unique identifier for the session type (e.g., "onboarding", "goal_setting")
- `responsibilities`: List of tasks the agent must complete (in order or flexible based on notes)
- `collect_data`: List of user data fields to gather
- `collect_metrics`: List of calculated metrics needed
- `initial_message`: Default message to start the session

### Optional Fields

- `notes`: Additional guidance for the agent about this session type

## Usage

The app loads these YAML files and uses them to initialize conversation sessions instead of hardcoding the configuration in the code.

### Example: Starting an Onboarding Session

```kotlin
val config = SessionConfigLoader.load("onboarding")
conversation.startSession(
    cap = config.cap,
    responsibilities = config.responsibilities,
    collectData = config.collectData,
    collectMetrics = config.collectMetrics,
    initialMessage = config.initialMessage
)
```

## Creating New Session Types

1. Create a new YAML file in this directory (e.g., `meal_logging.yaml`)
2. Define the cap, responsibilities, collect_data, collect_metrics, and initial_message
3. Add any notes for special handling
4. The agent will automatically follow the responsibilities defined in the file

## Important Rules

- **All responsibilities must be completed** before the agent can mark the session as "completed"
- **Function calls are mandatory** when specified in responsibilities (e.g., "call save_goal()")
- **Order is flexible** unless explicitly stated in notes
- **Agent cannot skip steps** - each responsibility must be executed

## Available Session Types

- `onboarding.yaml` - New user profile setup
- `goal_setting.yaml` - Health goal definition and saving

## Future Session Types (Examples)

- `meal_logging.yaml` - Log meals and track nutrition
- `progress_review.yaml` - Review progress toward goals
- `coaching.yaml` - General health coaching conversation
- `exercise_planning.yaml` - Create workout plans

# Session Configurations

This directory contains individual session configuration files. Each session is defined in its own file for easy maintenance and updates.

## Structure

Each session config file follows this pattern:

```kotlin
package com.amigo.shared.ai.sessions

import com.amigo.shared.ai.SessionConfig

object [SessionName]SessionConfig {
    val config = SessionConfig(
        cap = "session_name",
        responsibilities = listOf(...),
        collectData = listOf(...),
        collectMetrics = listOf(...),
        initialMessage = "...",
        notes = listOf(...)
    )
}
```

## Available Sessions

- **OnboardingSessionConfig.kt** - New user onboarding flow
- **GoalSettingSessionConfig.kt** - Health goal definition and calculation

## Adding a New Session

1. Create a new file: `[SessionName]SessionConfig.kt`
2. Define the config object following the pattern above
3. Register it in `SessionConfig.kt` by:
   - Adding a val in `SessionConfigs` object
   - Adding a case in `getConfig()` function
   - Adding to `getAllConfigs()` list

## Configuration Fields

- **cap**: Unique identifier for the session
- **responsibilities**: List of tasks the agent must complete (in order)
- **collectData**: Fields to collect from the user
- **collectMetrics**: Calculated metrics to track
- **initialMessage**: First message to send to the agent
- **notes**: Additional guidance for the agent

## Best Practices

1. Keep responsibilities clear and actionable
2. List function calls explicitly (e.g., "Call calculate_bmr()")
3. Specify the sequence when order matters
4. Include validation rules in notes
5. Document any special handling requirements

package com.amigo.shared.ai.sessions

import com.amigo.shared.ai.SessionConfig

/**
 * Onboarding session configuration.
 * Collects basic profile information and health goals from new users.
 */
object OnboardingSessionConfig {
    
    val config = SessionConfig(
        cap = "onboarding",
        responsibilities = listOf(
            "just introduce yourself as Amigo, a friendly and supportive health coach",
            "get the user profile first using get profile tool and greet if you get their name",
            "Fill UI render data too with all the information you get from profile",
            "jump into questions to collect onboarding profile information from user that you dont have",
            "start with the health goal question and move to other questions",
            "Validate and normalize collected onboarding fields",
            "Summarize the onboarding details and review with the user",
            "Save onboarding data only after user confirmation",
            "Mark onboarding as complete once you save"
        ),
        collectData = listOf(
            "first_name",
            "last_name",
            "age",
            "weight",
            "height",
            "gender",
            "activity_level",
            "goal_type",
            "goal_detail",
            "goal_by_when"
        ),
        collectMetrics = listOf(
            "bmr",
            "tdee",
            "daily_calories"
        ),
        initialMessage = "Hi, get my profile and let's start onboarding.",
        notes = listOf(
            "Always call get_profile() first if authenticated",
            "Collect all required fields before saving",
            "Only call save_profile() after user confirms"
        )
    )
}

package com.amigo.shared.ai.sessions

import com.amigo.shared.ai.SessionConfig

/**
 * Goal setting session configuration.
 * Helps users define health goals with calculated metrics.
 */
object GoalSettingSessionConfig {
    
    val config = SessionConfig(
        cap = "goal_setting",
        responsibilities = listOf(
            "Get user profile to retrieve current weight, height, age, gender, activity_level",
            "Confirm or ask for goal type (weight_loss, muscle_gain, or maintenance)",
            "Ask for target weight in kg",
            "Ask for target date in yyyy-MM-dd format",
            "Calculate BMR using calculate_bmr(weight, height, age, gender)",
            "Calculate TDEE using calculate_tdee(weight, height, age, gender, activity_level)",
            "Calculate daily calories using calculate_daily_calories(goal_type, tdee, current_weight, target_weight, target_date)",
            "Validate goal using validate_goal(goal_type, daily_calories, gender, current_weight, target_weight, target_date, tdee)",
            "If goal is invalid, present the 3 suggestions: extend timeline, adjust target weight, or proceed with user override",
            "For override option, explain: 'Override lets you keep your original timeline but with custom daily calories. You can set your own calorie target instead of our recommended amount. This may affect the safety and effectiveness of your goal.'",
            "If user chooses override, ask for their preferred daily calories and store both recommended and user values",
            "CRITICAL: When user_overridden=true, you MUST collect and include user_daily_calories parameter in save_goal call",
            "If user chooses a suggestion, recalculate and revalidate with new parameters",
            "Present final summary with: current weight, target weight, target date, BMR, TDEE, daily calories, weekly weight change rate",
            "If user_overridden=true, show both recommended calories and user's custom calories in summary",
            "Ask user to confirm the goal",
            "When confirmed, call save_goal with all parameters including user_overridden flag and user_daily_calories if applicable",
            "Set status to completed after successful save"
        ),
        collectData = listOf(
            "weight",
            "gender",
            "current_weight",
            "target_weight",
            "target_date",
            "goal_type",
            "user_overridden",
            "user_daily_calories"
        ),
        collectMetrics = listOf(
            "bmr",
            "tdee",
            "daily_calories",
            "user_daily_calories",
            "weekly_weight_change"
        ),
        initialMessage = "I'd like to set a weight_loss goal",
        notes = listOf(
            "CRITICAL: Never show placeholders like [BMR] or [TDEE] - always call the calculation functions first",
            "Function sequence is MANDATORY: get_profile → collect 3 fields → calculate_bmr → calculate_tdee → calculate_daily_calories → validate_goal",
            "If validate_goal returns is_valid=false, present all 3 suggestions to the user with clear options",
            "When explaining override option, be clear: 'Override allows you to keep your original timeline with custom daily calories. You set your own calorie target instead of our recommended [X] calories. This may affect safety and effectiveness.'",
            "If user chooses override, collect their preferred daily calories and save both recommended (calculated_daily_calories) and user values (user_daily_calories)",
            "CRITICAL: save_goal call MUST include user_daily_calories parameter when user_overridden=true",
            "Always show both recommended and user calories in summary when user_overridden=true",
            "user_overridden should be true only if user explicitly chooses the override option",
            "Do not skip any function in the sequence",
            "Do not generate JSON between function calls in the sequence"
        )
    )
}

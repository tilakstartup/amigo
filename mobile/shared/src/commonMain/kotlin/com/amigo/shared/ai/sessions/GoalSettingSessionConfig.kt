package com.amigo.shared.ai.sessions

import com.amigo.shared.ai.SessionConfig

/**
 * Goal setting session configuration.
 * Helps users define health goals with calculated metrics.
 */
object GoalSettingSessionConfig {
    
    val config = SessionConfig(
        hat = "goal_setting",
        responsibilities = listOf(
            "if user is authenticated, call get_profile immidiately to get user profile information",
            "greet the user and say you will help them set their health goal",
            "based on users goal, collect data",
            "for example for weight loss collect current weight, target weight, age, height etc to calchulate health metrics",
            "once all information is provided, validate the goal",
            "once validation passes, save the goal and mark the session as completed"
        ),
        data_to_be_collected = listOf(
            "current_weight",
            "target_weight",
            "target_date",
            "goal_type",
            "user_overridden",
            "user_daily_calories"
        ),
        data_to_be_calculated = listOf(
            "bmr",
            "tdee",
            "daily_calories",
            "weekly_weight_change"
        ),
        initial_message = "I'd like to set a weight_loss goal",
        notes = listOf(
            "CRITICAL: Call get_profile FIRST before asking any questions — profile data drives all calculations.",
            "CRITICAL: Never show placeholders like [BMR] or [TDEE] — always call the calculation functions first and use their returned values.",
            "CRITICAL: Function call sequence is MANDATORY and must not be skipped: get_profile → calculate_bmr → calculate_tdee → calculate_daily_calories → validate_goal → save_goal.",
            "CRITICAL: Do NOT generate any JSON between consecutive function calls in the sequence.",
            "Parameter names for calculate_bmr: weight_kg, height_cm, age, gender.",
            "Parameter names for calculate_tdee: bmr (from calculate_bmr result), activity_level (from get_profile).",
            "Parameter names for calculate_daily_calories: goal_type, tdee, current_weight_kg, target_weight_kg, target_date.",
            "Parameter names for validate_goal: goal_type, daily_calories, gender, current_weight_kg, target_weight_kg, target_date, tdee.",
            "user_overridden must be true ONLY if user explicitly chose the override option.",
            "When user_overridden=true, user_daily_calories is MANDATORY in save_goal."
        )
    )
}

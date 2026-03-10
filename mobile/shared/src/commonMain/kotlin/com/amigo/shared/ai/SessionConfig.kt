package com.amigo.shared.ai

import kotlinx.serialization.Serializable

/**
 * Configuration for an AI conversation session.
 * Loaded from YAML files in resources/session-configs/
 */
@Serializable
data class SessionConfig(
    val cap: String,
    val responsibilities: List<String>,
    val collectData: List<String>,
    val collectMetrics: List<String> = emptyList(),
    val initialMessage: String,
    val notes: List<String> = emptyList()
)

/**
 * Predefined session configurations.
 * These match the YAML files in resources/session-configs/
 * TODO: Load these from YAML files dynamically when multiplatform YAML support is available
 */
object SessionConfigs {
    
    /**
     * Onboarding session for new users.
     * Collects basic profile information and health goals.
     */
    val ONBOARDING = SessionConfig(
        cap = "onboarding",
        responsibilities = listOf(
            "Check if x_amigo_auth is present and valid (not empty, not a template)",
            "If authenticated, call get_profile() as the VERY FIRST ACTION before generating any JSON response",
            "If NOT authenticated, skip profile retrieval and proceed to greeting",
            "If authentication error occurs from ANY function call, skip ALL data_operations functions",
            "Generate a warm greeting and introduction as Amigo (after get_profile returns, or if not authenticated)",
            "Identify missing fields from collect_data that are still null",
            "Ask for missing fields one at a time in a natural conversational order",
            "When all required fields are collected AND user IS authenticated, call save_onboarding_data() to persist data",
            "If user is NOT authenticated, skip save_onboarding_data() (data will be saved after signup)",
            "Set aimofchat.status to \"completed\" ONLY after save_onboarding_data() returns success (or if user is not authenticated and all fields are collected)"
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
        initialMessage = "Let's get to know you better so I can provide personalized health coaching.",
        notes = listOf(
            "Name handling: Ask for full name first, parse into first_name/last_name",
            "If only one name part provided, store as first_name and ask for last_name",
            "Do not ask for name if first_name and last_name already exist in profile",
            "Order of questions is flexible - agent chooses natural flow",
            "All responsibilities must be completed before marking as \"completed\""
        )
    )
    
    /**
     * Goal setting session for defining health goals.
     * Calculates metrics and saves the goal.
     */
    val GOAL_SETTING = SessionConfig(
        cap = "goal_setting",
        responsibilities = listOf(
            "Call get_profile() to retrieve current metrics (weight, height, age, gender, activity_level)",
            "If goal_type not provided in initial message, ask for it (weight_loss/muscle_gain/maintenance)",
            "Ask for target_weight (must be different from current weight based on goal type)",
            "Ask for target_date (must be future date in yyyy-MM-dd format)",
            "Do not acknowledge that you need to calculate, just invoke functions directly",
            "Call calculate_bmr(x_amigo_auth, weight, height, age, gender)",
            "Call calculate_tdee(x_amigo_auth, weight, height, age, gender, activity_level)",
            "Calculate daily calories based on goal (e.g., for weight loss = TDEE - (weight_difference * 7700 / days_until_target))",
            "Call validate_goal(x_amigo_auth, goal_type, daily_calories)",
            "Present summary with all metrics and ask for confirmation (use message_with_summary)",
            "When user confirms, IMMEDIATELY call save_goal() with all parameters",
            "After save_goal returns success, set aimofchat.status to \"completed\""
        ),
        collectData = listOf(
            "goal_type",
            "target_weight",
            "target_date"
        ),
        collectMetrics = listOf(
            "current_weight",
            "current_height",
            "bmr",
            "tdee",
            "daily_calories",
            "weekly_weight_change"
        ),
        initialMessage = "Let's set up your health goal together.",
        notes = listOf(
            "All calculations must be done before presenting summary",
            "Do not claim goal is saved without actually calling save_goal()",
            "Wait for save_goal() success response before marking complete",
            "Summary must include: current weight, target weight, target date, BMR, TDEE, daily calories, weekly change rate"
        )
    )
    
    /**
     * Get a session config by cap name.
     * Loads from YAML files dynamically.
     */
    fun getConfig(cap: String): SessionConfig? {
        // Try loading from YAML first
        val yamlConfig = SessionConfigLoader.loadConfig(cap)
        if (yamlConfig != null) {
            return yamlConfig
        }
        
        // Fall back to hardcoded configs if YAML loading fails
        println("⚠️ Falling back to hardcoded config for '$cap'")
        return when (cap) {
            "onboarding" -> ONBOARDING
            "goal_setting" -> GOAL_SETTING
            else -> null
        }
    }
    
    /**
     * Get all available session configs.
     */
    fun getAllConfigs(): List<SessionConfig> {
        return listOf(ONBOARDING, GOAL_SETTING)
    }
}

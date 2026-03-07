package com.amigo.shared.ai.models

import kotlinx.serialization.Serializable

/**
 * Structured response from AI for onboarding
 */
@Serializable
data class OnboardingAIResponse(
    val message: String,
    val quickReplies: List<String>? = null,
    val feature: FeatureIntroduction? = null,
    val dataCollected: Map<String, String>? = null,
    val nextAction: String? = null // "continue", "complete", "request_permissions"
)

/**
 * Feature introduction with icon
 */
@Serializable
data class FeatureIntroduction(
    val name: String,
    val description: String,
    val icon: String // SF Symbol name or emoji
)

/**
 * Data collection schema for onboarding
 */
object OnboardingSchema {
    val requiredFields = listOf(
        DataField("primaryGoal", "string", "User's primary health goal", 
            options = listOf("Weight Loss", "Muscle Gain", "Maintenance", "Improved Energy", "Better Sleep")),
        DataField("name", "string", "User's first name"),
        DataField("age", "number", "User's age in years", validation = "13-120"),
        DataField("height", "string", "User's height (can be in feet/inches or cm)"),
        DataField("weight", "string", "User's weight (can be in lbs or kg)"),
        DataField("activityLevel", "string", "User's activity level",
            options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extremely Active")),
        DataField("dietaryPreferences", "string", "User's dietary preferences or restrictions",
            options = listOf("No Restrictions", "Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo"))
    )
    
    val features = listOf(
        Feature("Meal Logging", "Log meals with photos, voice, or text", "📸"),
        Feature("Water Tracking", "Smart reminders to stay hydrated", "💧"),
        Feature("Fasting Timer", "Track intermittent fasting periods", "⏱️"),
        Feature("AI Insights", "Personalized coaching and insights", "🤖"),
        Feature("Health Metrics", "Track your progress with detailed metrics", "📊")
    )
}

@Serializable
data class DataField(
    val name: String,
    val type: String,
    val description: String,
    val validation: String? = null,
    val options: List<String>? = null
)

@Serializable
data class Feature(
    val name: String,
    val description: String,
    val icon: String
)

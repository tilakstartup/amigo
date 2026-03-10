package com.amigo.shared.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val age: Int? = null,
    @SerialName("height_cm")
    val heightCm: Double? = null,
    @SerialName("weight_kg")
    val weightKg: Double? = null,
    val gender: String? = null,
    @SerialName("goal_type")
    val goalType: GoalType? = null,
    @SerialName("goal_by_when")
    val goalByWhen: String? = null,
    @SerialName("activity_level")
    val activityLevel: ActivityLevel? = null,
    @SerialName("dietary_preferences")
    val dietaryPreferences: List<String>? = null,
    @SerialName("onboarding_completed")
    val onboardingCompleted: Boolean = false,
    @SerialName("onboarding_completed_at")
    val onboardingCompletedAt: String? = null,
    @SerialName("unit_preference")
    val unitPreference: UnitPreference = UnitPreference.METRIC,
    val language: String = "en",
    val theme: Theme = Theme.AUTO,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

@Serializable
enum class UnitPreference {
    @SerialName("metric")
    METRIC,
    @SerialName("imperial")
    IMPERIAL
}

@Serializable
enum class ActivityLevel {
    @SerialName("sedentary")
    SEDENTARY,
    @SerialName("lightly_active")
    LIGHTLY_ACTIVE,
    @SerialName("moderately_active")
    MODERATELY_ACTIVE,
    @SerialName("very_active")
    VERY_ACTIVE,
    @SerialName("extremely_active")
    EXTREMELY_ACTIVE;
    
    companion object {
        fun fromString(value: String): ActivityLevel? {
            return when (value.lowercase()) {
                "sedentary", "not active", "inactive" -> SEDENTARY
                "lightly_active", "lightly active", "light", "1-2 days/week" -> LIGHTLY_ACTIVE
                "moderately_active", "moderately active", "moderate", "3-5 days/week" -> MODERATELY_ACTIVE
                "very_active", "very active", "active", "6-7 days/week" -> VERY_ACTIVE
                "extremely_active", "extremely active", "very intense", "athlete" -> EXTREMELY_ACTIVE
                else -> null
            }
        }
    }
}

@Serializable
enum class Theme {
    @SerialName("light")
    LIGHT,
    @SerialName("dark")
    DARK,
    @SerialName("auto")
    AUTO
}

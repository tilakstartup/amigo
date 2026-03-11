package com.amigo.shared.profile

import com.amigo.shared.data.models.UnitPreference
import com.amigo.shared.data.models.Theme
import com.amigo.shared.data.models.GoalType
import com.amigo.shared.data.models.ActivityLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

data class ProfileUpdate(
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val gender: String? = null,
    val goalType: GoalType? = null,
    val goalByWhen: String? = null,
    val activityLevel: ActivityLevel? = null,
    val dietaryPreferences: List<String>? = null,
    val onboardingCompleted: Boolean? = null,
    val onboardingCompletedAt: String? = null,
    val unitPreference: UnitPreference? = null,
    val language: String? = null,
    val theme: Theme? = null
) {
    fun toMap(): JsonObject {
        return buildJsonObject {
            firstName?.let { put("first_name", it) }
            lastName?.let { put("last_name", it) }
            displayName?.let { put("display_name", it) }
            avatarUrl?.let { put("avatar_url", it) }
            age?.let { put("age", it) }
            heightCm?.let { put("height_cm", it) }
            weightKg?.let { put("weight_kg", it) }
            gender?.let { put("gender", it) }
            goalType?.let { put("goal_type", it.name.lowercase()) }
            goalByWhen?.let { put("goal_by_when", it) }
            activityLevel?.let { put("activity_level", it.name.lowercase()) }
            dietaryPreferences?.let { prefs ->
                putJsonArray("dietary_preferences") {
                    prefs.forEach { add(it) }
                }
            }
            onboardingCompleted?.let { put("onboarding_completed", it) }
            onboardingCompletedAt?.let { put("onboarding_completed_at", it) }
            unitPreference?.let { put("unit_preference", it.name.lowercase()) }
            language?.let { put("language", it) }
            theme?.let { put("theme", it.name.lowercase()) }
        }
    }
    
    /**
     * Validate profile data
     */
    fun validate(): ProfileValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate age
        age?.let {
            if (it < 13 || it > 120) {
                errors.add("Age must be between 13 and 120")
            }
        }
        
        // Validate height (in cm)
        heightCm?.let {
            if (it < 50 || it > 300) {
                errors.add("Height must be between 50cm and 300cm")
            }
        }
        
        // Validate weight (in kg)
        weightKg?.let {
            if (it < 20 || it > 500) {
                errors.add("Weight must be between 20kg and 500kg")
            }
        }
        
        return if (errors.isEmpty()) {
            ProfileValidationResult.Valid
        } else {
            ProfileValidationResult.Invalid(errors)
        }
    }
}

sealed class ProfileValidationResult {
    object Valid : ProfileValidationResult()
    data class Invalid(val errors: List<String>) : ProfileValidationResult()
}

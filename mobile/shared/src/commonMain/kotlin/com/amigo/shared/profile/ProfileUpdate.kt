package com.amigo.shared.profile

import com.amigo.shared.data.models.UnitPreference
import com.amigo.shared.data.models.Theme

data class ProfileUpdate(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val unitPreference: UnitPreference? = null,
    val language: String? = null,
    val theme: Theme? = null
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        displayName?.let { map["display_name"] = it }
        avatarUrl?.let { map["avatar_url"] = it }
        age?.let { map["age"] = it }
        heightCm?.let { map["height_cm"] = it }
        weightKg?.let { map["weight_kg"] = it }
        unitPreference?.let { map["unit_preference"] = it.name.lowercase() }
        language?.let { map["language"] = it }
        theme?.let { map["theme"] = it.name.lowercase() }
        return map
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

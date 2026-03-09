package com.amigo.shared.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HealthGoal(
    val id: String,
    val userId: String,
    val goalType: GoalType,
    val startDate: String,
    val endDate: String? = null,
    val isActive: Boolean = true,
    val goalContext: GoalContext? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class GoalType {
    @SerialName("weight_loss")
    WEIGHT_LOSS,
    @SerialName("muscle_gain")
    MUSCLE_GAIN,
    @SerialName("maintenance")
    MAINTENANCE,
    @SerialName("improved_energy")
    IMPROVED_ENERGY,
    @SerialName("better_sleep")
    BETTER_SLEEP;
    
    companion object {
        fun fromString(value: String): GoalType? {
            return when (value.lowercase()) {
                "weight_loss", "weight loss", "lose weight" -> WEIGHT_LOSS
                "muscle_gain", "muscle gain", "build muscle", "gain muscle" -> MUSCLE_GAIN
                "maintenance", "maintain weight", "maintain" -> MAINTENANCE
                "improved_energy", "improved energy", "more energy", "energy" -> IMPROVED_ENERGY
                "better_sleep", "better sleep", "improve sleep", "sleep" -> BETTER_SLEEP
                else -> null
            }
        }
    }
}

@Serializable
data class GoalContext(
    val targetMetrics: Map<String, Double>? = null,
    val progressMetrics: Map<String, Double>? = null,
    val patterns: Map<String, String>? = null,
    val notes: String? = null
)

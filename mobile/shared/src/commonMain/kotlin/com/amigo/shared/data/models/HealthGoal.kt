package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

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
    WEIGHT_LOSS,
    MUSCLE_GAIN,
    MAINTENANCE,
    IMPROVED_ENERGY,
    BETTER_SLEEP
}

@Serializable
data class GoalContext(
    val targetMetrics: Map<String, Double>? = null,
    val progressMetrics: Map<String, Double>? = null,
    val patterns: Map<String, String>? = null,
    val notes: String? = null
)

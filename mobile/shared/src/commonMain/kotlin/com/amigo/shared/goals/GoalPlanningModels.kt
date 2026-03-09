package com.amigo.shared.goals

import com.amigo.shared.utils.TimeProvider
import kotlinx.serialization.Serializable

/**
 * Complete goal plan with all calculations
 */
@Serializable
data class GoalPlan(
    val goalType: String,  // weight_loss, muscle_gain, maintenance, etc.
    val currentMetrics: CurrentMetrics,
    val targetMetrics: TargetMetrics,
    val calculations: GoalCalculations,
    val validation: GoalValidationResult,
    val milestones: List<WeeklyMilestone>,
    val createdAt: Long = TimeProvider.currentTimeMillis()
)

/**
 * Current user metrics
 */
@Serializable
data class CurrentMetrics(
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val gender: String,  // "male" or "female"
    val activityLevel: String,  // sedentary, light, moderate, active, very_active
    val bmi: Double
)

/**
 * Target metrics for the goal
 */
@Serializable
data class TargetMetrics(
    val weightKg: Double,
    val targetDate: String,  // ISO date string
    val targetDays: Int,
    val targetBMI: Double
)

/**
 * All calculated values for the goal
 */
@Serializable
data class GoalCalculations(
    val bmr: Double,
    val tdee: Double,
    val dailyCalories: Double,
    val weeklyWeightLossRate: Double,
    val totalWeightToLose: Double,
    val calorieDeficitPerDay: Double
)

/**
 * Activity level option for UI
 */
data class ActivityLevelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val multiplier: Double
) {
    companion object {
        fun getAllOptions(): List<ActivityLevelOption> = listOf(
            ActivityLevelOption(
                id = "sedentary",
                displayName = "Sedentary",
                description = "Little or no exercise",
                multiplier = 1.2
            ),
            ActivityLevelOption(
                id = "light",
                displayName = "Lightly Active",
                description = "Light exercise 1-3 days/week",
                multiplier = 1.375
            ),
            ActivityLevelOption(
                id = "moderate",
                displayName = "Moderately Active",
                description = "Moderate exercise 3-5 days/week",
                multiplier = 1.55
            ),
            ActivityLevelOption(
                id = "active",
                displayName = "Active",
                description = "Hard exercise 6-7 days/week",
                multiplier = 1.725
            ),
            ActivityLevelOption(
                id = "very_active",
                displayName = "Very Active",
                description = "Very hard exercise, physical job",
                multiplier = 1.9
            )
        )
    }
}

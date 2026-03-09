package com.amigo.shared.goals

import kotlin.math.pow
import kotlin.math.ceil

/**
 * Engine for calculating health goal metrics including BMI, BMR, TDEE, and calorie requirements.
 * Implements Mifflin-St Jeor equation for BMR and validates goals against USDA guidelines.
 */
class GoalCalculationEngine {
    
    companion object {
        // USDA minimum daily calorie requirements
        const val USDA_MIN_CALORIES_WOMEN = 1200.0
        const val USDA_MIN_CALORIES_MEN = 1500.0
        
        // Safe weight loss rates (kg per week)
        const val MIN_WEIGHT_LOSS_RATE_KG = 0.5
        const val MAX_WEIGHT_LOSS_RATE_KG = 1.0
        
        // Safe weight loss rates (lbs per week)
        const val MIN_WEIGHT_LOSS_RATE_LBS = 1.0
        const val MAX_WEIGHT_LOSS_RATE_LBS = 2.0
        
        // Calories per kg/lb of body weight
        const val CALORIES_PER_KG = 7700.0  // Approximate calories in 1 kg of body fat
        const val CALORIES_PER_LB = 3500.0  // Approximate calories in 1 lb of body fat
        
        // Activity level multipliers for TDEE calculation
        val ACTIVITY_MULTIPLIERS = mapOf(
            "sedentary" to 1.2,        // Little or no exercise
            "light" to 1.375,          // Light exercise 1-3 days/week
            "moderate" to 1.55,        // Moderate exercise 3-5 days/week
            "active" to 1.725,         // Hard exercise 6-7 days/week
            "very_active" to 1.9       // Very hard exercise, physical job
        )
    }
    
    /**
     * Calculate Body Mass Index (BMI)
     * Formula: weight (kg) / height (m)^2
     */
    fun calculateBMI(weightKg: Double, heightCm: Double): Double {
        val heightM = heightCm / 100.0
        return weightKg / (heightM.pow(2))
    }
    
    /**
     * Calculate Basal Metabolic Rate (BMR) using Mifflin-St Jeor equation
     * Men: BMR = 10 × weight(kg) + 6.25 × height(cm) - 5 × age(years) + 5
     * Women: BMR = 10 × weight(kg) + 6.25 × height(cm) - 5 × age(years) - 161
     */
    fun calculateBMR(weightKg: Double, heightCm: Double, age: Int, gender: Gender): Double {
        val baseBMR = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (gender) {
            Gender.MALE -> baseBMR + 5
            Gender.FEMALE -> baseBMR - 161
        }
    }
    
    /**
     * Calculate Total Daily Energy Expenditure (TDEE)
     * Formula: BMR × activity multiplier
     */
    fun calculateTDEE(bmr: Double, activityLevel: String): Double {
        val multiplier = ACTIVITY_MULTIPLIERS[activityLevel.lowercase()] ?: ACTIVITY_MULTIPLIERS["sedentary"]!!
        return bmr * multiplier
    }
    
    /**
     * Calculate daily calorie requirement for weight loss goal
     * Returns the target daily calories to achieve the weight loss goal
     */
    fun calculateDailyCaloriesForWeightLoss(
        tdee: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        targetDays: Int,
        useMetric: Boolean = true
    ): Double {
        val weightDifference = currentWeightKg - targetWeightKg
        val caloriesPerUnit = if (useMetric) CALORIES_PER_KG else CALORIES_PER_LB
        val totalCalorieDeficit = weightDifference * caloriesPerUnit
        val dailyDeficit = totalCalorieDeficit / targetDays
        
        return tdee - dailyDeficit
    }
    
    /**
     * Validate if target date is realistic for safe weight loss
     * Returns validation result with recommendations
     */
    fun validateWeightLossGoal(
        currentWeightKg: Double,
        targetWeightKg: Double,
        targetDays: Int,
        tdee: Double,
        gender: Gender,
        useMetric: Boolean = true
    ): GoalValidationResult {
        val weightDifference = currentWeightKg - targetWeightKg
        
        if (weightDifference <= 0) {
            return GoalValidationResult(
                isRealistic = false,
                reason = "Target weight must be less than current weight for weight loss goals",
                recommendedTargetDays = null,
                calculatedDailyCalories = null,
                weeklyWeightLossRate = null
            )
        }
        
        // Calculate weekly weight loss rate
        val weeks = targetDays / 7.0
        val weeklyRate = weightDifference / weeks
        
        // Check if rate is within safe range
        val minRate = if (useMetric) MIN_WEIGHT_LOSS_RATE_KG else MIN_WEIGHT_LOSS_RATE_LBS
        val maxRate = if (useMetric) MAX_WEIGHT_LOSS_RATE_KG else MAX_WEIGHT_LOSS_RATE_LBS
        
        val dailyCalories = calculateDailyCaloriesForWeightLoss(
            tdee, currentWeightKg, targetWeightKg, targetDays, useMetric
        )
        
        // Check USDA minimum calories
        val minCalories = if (gender == Gender.FEMALE) USDA_MIN_CALORIES_WOMEN else USDA_MIN_CALORIES_MEN
        
        return when {
            dailyCalories < minCalories -> {
                // Calculate realistic target date that meets minimum calorie requirement
                val maxDailyDeficit = tdee - minCalories
                val caloriesPerUnit = if (useMetric) CALORIES_PER_KG else CALORIES_PER_LB
                val totalCalorieDeficit = weightDifference * caloriesPerUnit
                val recommendedDays = ceil(totalCalorieDeficit / maxDailyDeficit).toInt()
                
                GoalValidationResult(
                    isRealistic = false,
                    reason = "Target date would require daily calories below USDA minimum ($minCalories cal). This is unsafe.",
                    recommendedTargetDays = recommendedDays,
                    calculatedDailyCalories = minCalories,
                    weeklyWeightLossRate = weeklyRate
                )
            }
            weeklyRate > maxRate -> {
                // Too fast - calculate safer target date
                val caloriesPerUnit = if (useMetric) CALORIES_PER_KG else CALORIES_PER_LB
                val totalCalorieDeficit = weightDifference * caloriesPerUnit
                val safeWeeklyDeficit = maxRate * caloriesPerUnit
                val safeDailyDeficit = safeWeeklyDeficit / 7.0
                val recommendedDays = ceil(totalCalorieDeficit / safeDailyDeficit).toInt()
                
                val formattedRate = "%.2f".replace("%", weeklyRate.toString().take(4))
                GoalValidationResult(
                    isRealistic = false,
                    reason = "Weight loss rate (${weeklyRate.toString().take(4)} ${if (useMetric) "kg" else "lbs"}/week) is too fast. Safe maximum is $maxRate ${if (useMetric) "kg" else "lbs"}/week.",
                    recommendedTargetDays = recommendedDays,
                    calculatedDailyCalories = tdee - safeDailyDeficit,
                    weeklyWeightLossRate = weeklyRate
                )
            }
            weeklyRate < minRate -> {
                GoalValidationResult(
                    isRealistic = true,
                    reason = "Weight loss rate is slower than recommended minimum, but safe. Consider a shorter timeline for better results.",
                    recommendedTargetDays = null,
                    calculatedDailyCalories = dailyCalories,
                    weeklyWeightLossRate = weeklyRate,
                    warning = "Very slow progress - consider adjusting target date"
                )
            }
            else -> {
                GoalValidationResult(
                    isRealistic = true,
                    reason = "Goal is realistic and safe",
                    recommendedTargetDays = null,
                    calculatedDailyCalories = dailyCalories,
                    weeklyWeightLossRate = weeklyRate
                )
            }
        }
    }
    
    /**
     * Generate weekly milestone projections for weight loss goal
     */
    fun generateWeeklyMilestones(
        currentWeightKg: Double,
        targetWeightKg: Double,
        targetDays: Int,
        useMetric: Boolean = true
    ): List<WeeklyMilestone> {
        val milestones = mutableListOf<WeeklyMilestone>()
        val totalWeeks = ceil(targetDays / 7.0).toInt()
        val weeklyWeightLoss = (currentWeightKg - targetWeightKg) / totalWeeks
        
        for (week in 0..totalWeeks) {
            val projectedWeight = currentWeightKg - (weeklyWeightLoss * week)
            val weightToDisplay = if (projectedWeight < targetWeightKg) targetWeightKg else projectedWeight
            
            milestones.add(
                WeeklyMilestone(
                    week = week,
                    projectedWeightKg = weightToDisplay,
                    cumulativeWeightLossKg = currentWeightKg - weightToDisplay,
                    percentComplete = ((currentWeightKg - weightToDisplay) / (currentWeightKg - targetWeightKg) * 100).coerceIn(0.0, 100.0)
                )
            )
        }
        
        return milestones
    }
}

/**
 * Gender enum for BMR calculation
 */
enum class Gender {
    MALE,
    FEMALE
}

/**
 * Result of goal validation with recommendations
 */
@kotlinx.serialization.Serializable
data class GoalValidationResult(
    val isRealistic: Boolean,
    val reason: String,
    val recommendedTargetDays: Int?,
    val calculatedDailyCalories: Double?,
    val weeklyWeightLossRate: Double?,
    val warning: String? = null
)

/**
 * Weekly milestone for progress projection
 */
@kotlinx.serialization.Serializable
data class WeeklyMilestone(
    val week: Int,
    val projectedWeightKg: Double,
    val cumulativeWeightLossKg: Double,
    val percentComplete: Double
)

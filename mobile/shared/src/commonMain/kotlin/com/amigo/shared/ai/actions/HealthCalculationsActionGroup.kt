package com.amigo.shared.ai.actions

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.pow

/**
 * HealthCalculations action group
 * Handles BMR, TDEE, and goal validation calculations
 */
object HealthCalculationsActionGroup : ActionGroup {
    override val name = "health_calculations"
    override val description = "BMR, TDEE, and goal validation calculations with RETURN_CONTROL"
    
    override val functions = listOf(
        ActionFunction(
            name = "calculate_bmr",
            description = "Calculate Basal Metabolic Rate",
            parameters = listOf(
                ActionParameter("weight_kg", "number", "Weight in kilograms", required = true),
                ActionParameter("height_cm", "number", "Height in centimeters", required = true),
                ActionParameter("age", "number", "Age in years", required = true),
                ActionParameter("gender", "string", "Gender (male/female)", required = true)
            )
        ),
        ActionFunction(
            name = "calculate_tdee",
            description = "Calculate Total Daily Energy Expenditure",
            parameters = listOf(
                ActionParameter("weight_kg", "number", "Weight in kilograms", required = true),
                ActionParameter("height_cm", "number", "Height in centimeters", required = true),
                ActionParameter("age", "number", "Age in years", required = true),
                ActionParameter("gender", "string", "Gender (male/female)", required = true),
                ActionParameter("activity_level", "string", "Activity level", required = true)
            )
        ),
        ActionFunction(
            name = "validate_goal",
            description = "Validate if goal is realistic",
            parameters = listOf(
                ActionParameter("goal_type", "string", "Type of goal", required = true),
                ActionParameter("daily_calories", "number", "Daily calorie target", required = true)
            )
        )
    )
    
    override suspend fun executeFunction(
        functionName: String,
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        return when (functionName) {
            "calculate_bmr" -> calculateBMR(params)
            "calculate_tdee" -> calculateTDEE(params)
            "validate_goal" -> validateGoal(params)
            else -> Result.failure(
                IllegalArgumentException("Unknown function: $functionName")
            )
        }
    }
    
    private fun calculateBMR(params: Map<String, String>): Result<JsonObject> {
        return try {
            val weightKg = params["weight_kg"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid weight_kg"))
            val heightCm = params["height_cm"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid height_cm"))
            val age = params["age"]?.toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid age"))
            val gender = params["gender"]?.lowercase()
                ?: return Result.failure(IllegalArgumentException("Missing gender"))
            
            // Mifflin-St Jeor Equation
            val bmr = when (gender) {
                "male" -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
                "female" -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
                else -> return Result.failure(IllegalArgumentException("Invalid gender: $gender"))
            }
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("bmr", bmr)
                put("unit", "kcal/day")
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun calculateTDEE(params: Map<String, String>): Result<JsonObject> {
        return try {
            // First calculate BMR if not provided
            val bmr = if (params.containsKey("bmr")) {
                params["bmr"]?.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid bmr"))
            } else {
                // Calculate BMR from weight, height, age, gender
                val weightKg = params["weight_kg"]?.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid weight_kg"))
                val heightCm = params["height_cm"]?.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid height_cm"))
                val age = params["age"]?.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid age"))
                val gender = params["gender"]?.lowercase()
                    ?: return Result.failure(IllegalArgumentException("Missing gender"))
                
                // Mifflin-St Jeor Equation
                when (gender) {
                    "male" -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
                    "female" -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
                    else -> return Result.failure(IllegalArgumentException("Invalid gender: $gender"))
                }
            }
            
            val activityLevel = params["activity_level"]?.lowercase()
                ?: return Result.failure(IllegalArgumentException("Missing activity_level"))
            
            val multiplier = when (activityLevel) {
                "sedentary" -> 1.2
                "lightly_active" -> 1.375
                "moderately_active" -> 1.55
                "very_active" -> 1.725
                "extra_active" -> 1.9
                else -> return Result.failure(
                    IllegalArgumentException("Invalid activity_level: $activityLevel")
                )
            }
            
            val tdee = bmr * multiplier
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("tdee", tdee)
                put("bmr", bmr)
                put("activity_level", activityLevel)
                put("multiplier", multiplier)
                put("unit", "kcal/day")
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun validateGoal(params: Map<String, String>): Result<JsonObject> {
        return try {
            val goalType = params["goal_type"]?.lowercase()
                ?: return Result.failure(IllegalArgumentException("Missing goal_type"))
            val dailyCalories = params["daily_calories"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid daily_calories"))
            
            // Basic validation rules
            val isValid = when (goalType) {
                "weight_loss" -> dailyCalories >= 1200 && dailyCalories <= 2500
                "weight_gain" -> dailyCalories >= 2000 && dailyCalories <= 4000
                "maintenance" -> dailyCalories >= 1500 && dailyCalories <= 3500
                else -> false
            }
            
            val message = if (isValid) {
                "Goal is realistic and achievable"
            } else {
                when (goalType) {
                    "weight_loss" -> "Daily calories should be between 1200-2500 for safe weight loss"
                    "weight_gain" -> "Daily calories should be between 2000-4000 for healthy weight gain"
                    "maintenance" -> "Daily calories should be between 1500-3500 for maintenance"
                    else -> "Invalid goal type"
                }
            }
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("is_valid", isValid)
                put("message", message)
                put("goal_type", goalType)
                put("daily_calories", dailyCalories)
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

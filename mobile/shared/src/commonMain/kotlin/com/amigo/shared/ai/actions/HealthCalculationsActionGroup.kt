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
            name = "calculate_daily_calories",
            description = "Calculate daily calorie target for goal",
            parameters = listOf(
                ActionParameter("goal_type", "string", "Type of goal (weight_loss, muscle_gain, maintenance)", required = true),
                ActionParameter("tdee", "number", "Total Daily Energy Expenditure", required = true),
                ActionParameter("current_weight_kg", "number", "Current weight in kg", required = true),
                ActionParameter("target_weight_kg", "number", "Target weight in kg", required = true),
                ActionParameter("target_date", "string", "Target date in yyyy-MM-dd format", required = true)
            )
        ),
        ActionFunction(
            name = "validate_goal",
            description = "Validate if goal is realistic and provide suggestions if unsafe",
            parameters = listOf(
                ActionParameter("goal_type", "string", "Type of goal", required = true),
                ActionParameter("daily_calories", "number", "Daily calorie target", required = true),
                ActionParameter("gender", "string", "Gender (male/female)", required = true),
                ActionParameter("current_weight_kg", "number", "Current weight in kg", required = true),
                ActionParameter("target_weight_kg", "number", "Target weight in kg", required = true),
                ActionParameter("target_date", "string", "Target date in yyyy-MM-dd format", required = true),
                ActionParameter("tdee", "number", "Total Daily Energy Expenditure", required = true)
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
            "calculate_daily_calories" -> calculateDailyCalories(params)
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
    
    
    private fun calculateDailyCalories(params: Map<String, String>): Result<JsonObject> {
        return try {
            val goalType = params["goal_type"]?.lowercase()
                ?: return Result.failure(IllegalArgumentException("Missing goal_type"))
            val tdee = params["tdee"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid tdee"))
            val currentWeight = params["current_weight_kg"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid current_weight_kg"))
            val targetWeight = params["target_weight_kg"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid target_weight_kg"))
            val targetDate = params["target_date"]
                ?: return Result.failure(IllegalArgumentException("Missing target_date"))
            
            // Parse target date and calculate days until target
            val dateParts = targetDate.split("-")
            if (dateParts.size != 3) {
                return Result.failure(IllegalArgumentException("Invalid date format. Use yyyy-MM-dd"))
            }
            
            val targetYear = dateParts[0].toInt()
            val targetMonth = dateParts[1].toInt()
            val targetDay = dateParts[2].toInt()
            
            // Get current date using Clock.System
            val now = kotlinx.datetime.Clock.System.now()
            val currentEpochDays = (now.toEpochMilliseconds() / (24 * 60 * 60 * 1000)).toInt()
            
            val targetLocalDate = kotlinx.datetime.LocalDate(targetYear, targetMonth, targetDay)
            val daysUntilTarget = (targetLocalDate.toEpochDays() - currentEpochDays)
            
            if (daysUntilTarget <= 0) {
                return Result.failure(IllegalArgumentException("Target date must be in the future"))
            }
            
            // Calculate weight difference
            val weightDifference = kotlin.math.abs(targetWeight - currentWeight)
            
            // Calories per kg of body weight (approximately 7700 kcal per kg)
            val caloriesPerKg = 7700.0
            
            // Total calorie deficit/surplus needed
            val totalCalorieChange = weightDifference * caloriesPerKg
            
            // Daily calorie change needed
            val dailyCalorieChange = totalCalorieChange / daysUntilTarget.toDouble()
            
            // Calculate daily calories based on goal type - NO SAFETY FLOORS HERE
            val dailyCalories = when (goalType) {
                "weight_loss" -> tdee - dailyCalorieChange
                "muscle_gain", "weight_gain" -> tdee + dailyCalorieChange
                "maintenance" -> tdee
                else -> return Result.failure(IllegalArgumentException("Invalid goal_type: $goalType"))
            }
            
            // Calculate weekly weight change
            val weeklyWeightChange = (weightDifference / daysUntilTarget.toDouble()) * 7.0
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("daily_calories", dailyCalories)
                put("tdee", tdee)
                put("current_weight_kg", currentWeight)
                put("target_weight_kg", targetWeight)
                put("target_date", targetDate)
                put("days_until_target", daysUntilTarget)
                put("weekly_weight_change_kg", weeklyWeightChange)
                put("goal_type", goalType)
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
            val gender = params["gender"]?.lowercase()
                ?: return Result.failure(IllegalArgumentException("Missing gender"))
            val currentWeight = params["current_weight_kg"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid current_weight_kg"))
            val targetWeight = params["target_weight_kg"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid target_weight_kg"))
            val targetDate = params["target_date"]
                ?: return Result.failure(IllegalArgumentException("Missing target_date"))
            val tdee = params["tdee"]?.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid tdee"))
            
            // USDA minimum calorie guidelines
            val minimumCalories = when (gender) {
                "male" -> 1500.0
                "female" -> 1200.0
                else -> return Result.failure(IllegalArgumentException("Invalid gender: $gender"))
            }
            
            // Parse target date
            val dateParts = targetDate.split("-")
            val targetYear = dateParts[0].toInt()
            val targetMonth = dateParts[1].toInt()
            val targetDay = dateParts[2].toInt()
            val now = kotlinx.datetime.Clock.System.now()
            val currentEpochDays = (now.toEpochMilliseconds() / (24 * 60 * 60 * 1000)).toInt()
            val targetLocalDate = kotlinx.datetime.LocalDate(targetYear, targetMonth, targetDay)
            val daysUntilTarget = (targetLocalDate.toEpochDays() - currentEpochDays)
            
            // Check if goal is safe
            val isValid = when (goalType) {
                "weight_loss" -> dailyCalories >= minimumCalories
                "weight_gain", "muscle_gain" -> dailyCalories >= minimumCalories && dailyCalories <= tdee + 500
                "maintenance" -> dailyCalories >= minimumCalories
                else -> false
            }
            
            val message: String
            val suggestionsJson = buildList {
                if (!isValid) {
                    // Calculate suggestions
                    val weightDifference = kotlin.math.abs(targetWeight - currentWeight)
                    val caloriesPerKg = 7700.0
                    
                    when (goalType) {
                        "weight_loss" -> {
                            // Suggestion 1: Extend timeline to meet minimum calories
                            val safeDeficit = tdee - minimumCalories
                            val totalCaloriesNeeded = weightDifference * caloriesPerKg
                            val safeDays = (totalCaloriesNeeded / safeDeficit).toInt()
                            val safeDate = kotlinx.datetime.LocalDate.fromEpochDays(currentEpochDays + safeDays)
                            
                            add(buildJsonObject {
                                put("type", "extend_timeline")
                                put("suggested_date", "${safeDate.year}-${safeDate.monthNumber.toString().padStart(2, '0')}-${safeDate.dayOfMonth.toString().padStart(2, '0')}")
                                put("daily_calories", minimumCalories)
                                put("message", "Extend your goal to ${safeDate.year}-${safeDate.monthNumber.toString().padStart(2, '0')}-${safeDate.dayOfMonth.toString().padStart(2, '0')} to safely reach ${targetWeight} kg at ${minimumCalories.toInt()} kcal/day")
                            })
                            
                            // Suggestion 2: Adjust target weight for current timeline
                            val maxWeightLoss = (safeDeficit * daysUntilTarget) / caloriesPerKg
                            val safeTargetWeight = currentWeight - maxWeightLoss
                            
                            add(buildJsonObject {
                                put("type", "adjust_target")
                                put("suggested_target_weight", safeTargetWeight)
                                put("daily_calories", minimumCalories)
                                put("message", "Adjust your target to ${safeTargetWeight.toString().take(4)} kg by $targetDate at ${minimumCalories.toInt()} kcal/day")
                            })
                            
                            // Suggestion 3: User override (take the risk)
                            add(buildJsonObject {
                                put("type", "user_override")
                                put("daily_calories", minimumCalories)
                                put("original_target", targetWeight)
                                put("original_date", targetDate)
                                put("message", "Set goal as requested with minimum safe calories (${minimumCalories.toInt()} kcal/day). Progress may be slower than planned.")
                            })
                        }
                        "weight_gain", "muscle_gain" -> {
                            // No suggestions for weight gain yet
                        }
                    }
                }
            }
            
            message = if (isValid) {
                "Goal is realistic and achievable"
            } else {
                when (goalType) {
                    "weight_loss" -> "This goal requires ${dailyCalories.toInt()} kcal/day, which is below the USDA minimum of ${minimumCalories.toInt()} kcal/day for ${gender}s"
                    "weight_gain", "muscle_gain" -> {
                        if (dailyCalories < minimumCalories) {
                            "Daily calories are below minimum safe intake"
                        } else {
                            "Calorie surplus is too high (max 500 kcal/day recommended)"
                        }
                    }
                    else -> "Invalid goal type"
                }
            }
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("is_valid", isValid)
                put("message", message)
                put("goal_type", goalType)
                put("daily_calories", dailyCalories)
                put("minimum_calories", minimumCalories)
                put("gender", gender)
                put("suggestions", kotlinx.serialization.json.JsonArray(suggestionsJson))
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.goals.Gender
import com.amigo.shared.goals.GoalCalculationEngine

/**
 * Tool to validate if a weight loss/gain goal is realistic and safe
 */
class ValidateGoalTool(
    private val calculationEngine: GoalCalculationEngine
) : AmigoTool {
    override val name = "validate_goal"
    override val description = "Validates if a weight loss or gain goal is realistic, safe, and achievable within the given timeframe"
    override val parameters = mapOf(
        "current_weight_kg" to ToolParameter(
            name = "current_weight_kg",
            type = ParameterType.NUMBER,
            description = "Current weight in kilograms",
            required = true
        ),
        "target_weight_kg" to ToolParameter(
            name = "target_weight_kg",
            type = ParameterType.NUMBER,
            description = "Target weight in kilograms",
            required = true
        ),
        "target_days" to ToolParameter(
            name = "target_days",
            type = ParameterType.NUMBER,
            description = "Number of days to reach the target",
            required = true
        ),
        "tdee" to ToolParameter(
            name = "tdee",
            type = ParameterType.NUMBER,
            description = "Total Daily Energy Expenditure in calories",
            required = true
        ),
        "gender" to ToolParameter(
            name = "gender",
            type = ParameterType.STRING,
            description = "Gender: 'male' or 'female'",
            required = true
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val currentWeight = (params["current_weight_kg"] as? Number)?.toDouble()
                ?: return ToolResult.Error("current_weight_kg parameter is required", "MISSING_PARAMETER")
            val targetWeight = (params["target_weight_kg"] as? Number)?.toDouble()
                ?: return ToolResult.Error("target_weight_kg parameter is required", "MISSING_PARAMETER")
            val targetDays = (params["target_days"] as? Number)?.toInt()
                ?: return ToolResult.Error("target_days parameter is required", "MISSING_PARAMETER")
            val tdee = (params["tdee"] as? Number)?.toDouble()
                ?: return ToolResult.Error("tdee parameter is required", "MISSING_PARAMETER")
            val genderStr = (params["gender"] as? String)?.lowercase()
                ?: return ToolResult.Error("gender parameter is required", "MISSING_PARAMETER")
            
            val gender = when (genderStr) {
                "male" -> Gender.MALE
                "female" -> Gender.FEMALE
                else -> return ToolResult.Error("gender must be 'male' or 'female'", "INVALID_PARAMETER")
            }
            
            val isWeightLoss = targetWeight < currentWeight
            val validation = calculationEngine.validateWeightLossGoal(
                currentWeight, targetWeight, targetDays, tdee, gender, isWeightLoss
            )
            
            ToolResult.Success(
                mapOf(
                    "is_realistic" to validation.isRealistic,
                    "reason" to (validation.reason ?: "Goal is realistic and safe"),
                    "calculated_daily_calories" to (validation.calculatedDailyCalories ?: tdee),
                    "weekly_weight_loss_rate" to (validation.weeklyWeightLossRate ?: 0.0),
                    "recommended_target_days" to (validation.recommendedTargetDays ?: targetDays),
                    "current_weight_kg" to currentWeight,
                    "target_weight_kg" to targetWeight,
                    "target_days" to targetDays,
                    "is_weight_loss" to isWeightLoss
                )
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to validate goal: ${e.message}", "VALIDATION_ERROR")
        }
    }
}

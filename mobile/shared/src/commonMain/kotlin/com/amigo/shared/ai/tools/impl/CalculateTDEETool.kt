package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.goals.GoalCalculationEngine

/**
 * Tool to calculate Total Daily Energy Expenditure (TDEE)
 */
class CalculateTDEETool(
    private val calculationEngine: GoalCalculationEngine
) : AmigoTool {
    override val name = "calculate_tdee"
    override val description = "Calculates Total Daily Energy Expenditure (TDEE) from BMR and activity level"
    override val parameters = mapOf(
        "bmr" to ToolParameter(
            name = "bmr",
            type = ParameterType.NUMBER,
            description = "Basal Metabolic Rate in calories per day",
            required = true
        ),
        "activity_level" to ToolParameter(
            name = "activity_level",
            type = ParameterType.STRING,
            description = "Activity level: 'sedentary', 'lightly_active', 'moderately_active', 'very_active', or 'extremely_active'",
            required = true
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val bmr = (params["bmr"] as? Number)?.toDouble()
                ?: return ToolResult.Error("bmr parameter is required", "MISSING_PARAMETER")
            val activityLevel = (params["activity_level"] as? String)?.lowercase()
                ?: return ToolResult.Error("activity_level parameter is required", "MISSING_PARAMETER")
            
            val tdee = calculationEngine.calculateTDEE(bmr, activityLevel)
            val multiplier = when (activityLevel) {
                "sedentary" -> 1.2
                "lightly_active", "lightly active" -> 1.375
                "moderately_active", "moderately active" -> 1.55
                "very_active", "very active" -> 1.725
                "extremely_active", "extremely active" -> 1.9
                else -> 1.2
            }
            
            ToolResult.Success(
                mapOf(
                    "tdee" to tdee,
                    "bmr" to bmr,
                    "activity_level" to activityLevel,
                    "activity_multiplier" to multiplier,
                    "description" to "Total Daily Energy Expenditure - total calories burned per day including activity"
                )
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to calculate TDEE: ${e.message}", "CALCULATION_ERROR")
        }
    }
}

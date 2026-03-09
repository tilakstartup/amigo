package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.goals.Gender
import com.amigo.shared.goals.GoalCalculationEngine

/**
 * Tool to calculate Basal Metabolic Rate (BMR)
 */
class CalculateBMRTool(
    private val calculationEngine: GoalCalculationEngine
) : AmigoTool {
    override val name = "calculate_bmr"
    override val description = "Calculates Basal Metabolic Rate (BMR) using the Mifflin-St Jeor equation"
    override val parameters = mapOf(
        "weight_kg" to ToolParameter(
            name = "weight_kg",
            type = ParameterType.NUMBER,
            description = "Weight in kilograms",
            required = true
        ),
        "height_cm" to ToolParameter(
            name = "height_cm",
            type = ParameterType.NUMBER,
            description = "Height in centimeters",
            required = true
        ),
        "age" to ToolParameter(
            name = "age",
            type = ParameterType.NUMBER,
            description = "Age in years",
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
            val weightKg = (params["weight_kg"] as? Number)?.toDouble()
                ?: return ToolResult.Error("weight_kg parameter is required", "MISSING_PARAMETER")
            val heightCm = (params["height_cm"] as? Number)?.toDouble()
                ?: return ToolResult.Error("height_cm parameter is required", "MISSING_PARAMETER")
            val age = (params["age"] as? Number)?.toInt()
                ?: return ToolResult.Error("age parameter is required", "MISSING_PARAMETER")
            val genderStr = (params["gender"] as? String)?.lowercase()
                ?: return ToolResult.Error("gender parameter is required", "MISSING_PARAMETER")
            
            val gender = when (genderStr) {
                "male" -> Gender.MALE
                "female" -> Gender.FEMALE
                else -> return ToolResult.Error("gender must be 'male' or 'female'", "INVALID_PARAMETER")
            }
            
            val bmr = calculationEngine.calculateBMR(weightKg, heightCm, age, gender)
            
            ToolResult.Success(
                mapOf(
                    "bmr" to bmr,
                    "weight_kg" to weightKg,
                    "height_cm" to heightCm,
                    "age" to age,
                    "gender" to genderStr,
                    "description" to "Basal Metabolic Rate - calories burned at rest per day"
                )
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to calculate BMR: ${e.message}", "CALCULATION_ERROR")
        }
    }
}

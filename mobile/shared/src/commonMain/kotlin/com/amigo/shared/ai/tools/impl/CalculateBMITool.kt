package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.goals.GoalCalculationEngine

/**
 * Tool to calculate BMI from weight and height
 */
class CalculateBMITool(
    private val calculationEngine: GoalCalculationEngine
) : AmigoTool {
    override val name = "calculate_bmi"
    override val description = "Calculates Body Mass Index (BMI) from weight in kg and height in cm"
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
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val weightKg = (params["weight_kg"] as? Number)?.toDouble()
                ?: return ToolResult.Error("weight_kg parameter is required", "MISSING_PARAMETER")
            val heightCm = (params["height_cm"] as? Number)?.toDouble()
                ?: return ToolResult.Error("height_cm parameter is required", "MISSING_PARAMETER")
            
            val bmi = calculationEngine.calculateBMI(weightKg, heightCm)
            val category = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25.0 -> "Normal weight"
                bmi < 30.0 -> "Overweight"
                else -> "Obese"
            }
            
            ToolResult.Success(
                mapOf(
                    "bmi" to bmi,
                    "category" to category,
                    "weight_kg" to weightKg,
                    "height_cm" to heightCm
                )
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to calculate BMI: ${e.message}", "CALCULATION_ERROR")
        }
    }
}

package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.data.models.GoalType
import com.amigo.shared.profile.ProfileManager
import com.amigo.shared.utils.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool to save or update user's health goal
 */
class SaveGoalTool(
    private val profileManager: ProfileManager,
    private val userId: String
) : AmigoTool {
    private val json = Json { ignoreUnknownKeys = true }

    override val name: String = "save_goal"
    
    override val description: String = 
        "Save or update the user's health goal with target weight and date"
    
    override val parameters: Map<String, ToolParameter> = mapOf(
        "goalType" to ToolParameter(
            name = "goalType",
            type = ParameterType.STRING,
            description = "Goal type: WEIGHT_LOSS, MUSCLE_GAIN, MAINTENANCE, IMPROVED_ENERGY, or BETTER_SLEEP",
            required = true
        ),
        "targetWeight" to ToolParameter(
            name = "targetWeight",
            type = ParameterType.NUMBER,
            description = "Target weight in kilograms",
            required = false
        ),
        "targetDate" to ToolParameter(
            name = "targetDate",
            type = ParameterType.DATE,
            description = "Target date in YYYY-MM-DD format",
            required = false
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val goalTypeStr = readStringParam(params, "goalType")
                ?: return ToolResult.Error("Missing goalType parameter", "MISSING_PARAMETER")
            val goalType = parseGoalType(goalTypeStr)
                ?: return ToolResult.Error("Invalid goalType: $goalTypeStr", "INVALID_PARAMETER")

            val targetWeight = readNumberParam(params, "targetWeight")
            val targetDate = readStringParam(params, "targetDate")

            val currentWeight = readNumberParam(params, "currentWeight")
                ?: readNumberParam(params, "currentWeightKg")
            val currentHeight = readNumberParam(params, "heightCm")
                ?: readNumberParam(params, "currentHeight")
            val activityLevel = readStringParam(params, "activityLevel")
            val calculatedBmr = readNumberParam(params, "bmr")
            val calculatedTdee = readNumberParam(params, "tdee")
            val dailyCalories = readNumberParam(params, "dailyCalories")
                ?: readNumberParam(params, "calculatedDailyCalories")
            val startBmi = readNumberParam(params, "startBmi")
                ?: readNumberParam(params, "bmi")
            val targetBmi = readNumberParam(params, "targetBmi")
            val isRealistic = readBooleanParam(params, "isRealistic")
            val recommendedTargetDate = readStringParam(params, "recommendedTargetDate")
            val validationReason = readStringParam(params, "validationReason")
            val weeklyMilestones = readMilestones(params)

            val goalContext = buildMap<String, Any> {
                readNumberParam(params, "weeklyWeightLossRate")?.let { put("weeklyWeightLossRate", it) }
                readStringParam(params, "timeline")?.let { put("timeline", it) }
                if (weeklyMilestones != null) {
                    put("milestones", weeklyMilestones)
                }
            }.ifEmpty { null }
            
            // Update profile with goal
            val updateResult = profileManager.updateGoal(
                userId = userId,
                goalType = goalType,
                targetWeightKg = targetWeight,
                targetDate = targetDate,
                currentWeightKg = currentWeight,
                currentHeightCm = currentHeight,
                activityLevel = activityLevel,
                calculatedBmr = calculatedBmr,
                calculatedTdee = calculatedTdee,
                calculatedDailyCalories = dailyCalories,
                calculatedBmiStart = startBmi,
                calculatedBmiTarget = targetBmi,
                weeklyMilestones = weeklyMilestones,
                isRealistic = isRealistic,
                recommendedTargetDate = recommendedTargetDate,
                validationReason = validationReason,
                goalContext = goalContext
            )
            
            if (updateResult.isSuccess) {
                Logger.d("SaveGoalTool", "Goal saved for user $userId: $goalType")
                ToolResult.Success(mapOf(
                    "success" to true,
                    "goalType" to goalType.name,
                    "message" to "Goal saved successfully"
                ))
            } else {
                ToolResult.Error(
                    "Failed to save goal: ${updateResult.exceptionOrNull()?.message}",
                    "SAVE_FAILED"
                )
            }
        } catch (e: Exception) {
            Logger.e("SaveGoalTool", "Failed to save goal: ${e.message}")
            ToolResult.Error("Failed to save goal: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun parseGoalType(raw: String): GoalType? {
        return GoalType.fromString(raw)
            ?: GoalType.values().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }

    private fun readStringParam(params: Map<String, Any>, key: String): String? {
        val value = params[key] ?: return null
        return when (value) {
            is String -> value.trim().takeIf { it.isNotBlank() }
            else -> value.toString().trim().takeIf { it.isNotBlank() }
        }
    }

    private fun readNumberParam(params: Map<String, Any>, key: String): Double? {
        val value = params[key] ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> value.toString().toDoubleOrNull()
        }
    }

    private fun readBooleanParam(params: Map<String, Any>, key: String): Boolean? {
        val value = params[key] ?: return null
        return when (value) {
            is Boolean -> value
            is String -> when (value.trim().lowercase()) {
                "true", "yes", "1" -> true
                "false", "no", "0" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun readMilestones(params: Map<String, Any>): List<Map<String, Any>>? {
        val value = params["weeklyMilestones"] ?: return null

        return when (value) {
            is List<*> -> value.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                item as? Map<String, Any>
            }.ifEmpty { null }
            is String -> parseMilestonesFromJson(value)
            else -> null
        }
    }

    private fun parseMilestonesFromJson(raw: String): List<Map<String, Any>>? {
        return try {
            val array = json.parseToJsonElement(raw).jsonArray
            array.mapNotNull { item ->
                val obj = item.jsonObject
                val week = obj["week"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                val projectedWeight = obj["projectedWeightKg"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["projected_weight_kg"]?.jsonPrimitive?.doubleOrNull
                    ?: return@mapNotNull null
                val cumulativeWeightLoss = obj["cumulativeWeightLossKg"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["cumulative_weight_loss_kg"]?.jsonPrimitive?.doubleOrNull
                    ?: 0.0
                val percentComplete = obj["percentComplete"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["percent_complete"]?.jsonPrimitive?.doubleOrNull
                    ?: 0.0

                mapOf(
                    "week" to week,
                    "projectedWeightKg" to projectedWeight,
                    "cumulativeWeightLossKg" to cumulativeWeightLoss,
                    "percentComplete" to percentComplete,
                    "isCheckpoint" to (obj["isCheckpoint"]?.jsonPrimitive?.booleanOrNull ?: false)
                )
            }.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}

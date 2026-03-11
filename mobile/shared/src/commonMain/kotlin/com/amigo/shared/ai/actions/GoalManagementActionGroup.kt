package com.amigo.shared.ai.actions

import com.amigo.shared.profile.ProfileManager
import com.amigo.shared.data.models.GoalType
import com.amigo.shared.utils.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * GoalManagement action group
 * Handles saving and managing user health goals
 */
object GoalManagementActionGroup : ActionGroup {
    override val name = "goal_management"
    override val description = "Save and manage user health goals"
    
    override val functions = listOf(
        ActionFunction(
            name = "save_goal",
            description = "Save a health goal to the database",
            parameters = listOf(
                ActionParameter("goal_type", "string", "Type of goal (weight_loss, muscle_gain, maintenance)", required = true),
                ActionParameter("current_weight", "number", "Current weight in kg", required = true),
                ActionParameter("target_weight", "number", "Target weight in kg", required = true),
                ActionParameter("target_date", "string", "Target date (YYYY-MM-DD)", required = true),
                ActionParameter("current_height", "number", "Current height in cm", required = false),
                ActionParameter("activity_level", "string", "Activity level", required = false),
                ActionParameter("calculated_bmr", "number", "Calculated BMR", required = false),
                ActionParameter("calculated_tdee", "number", "Calculated TDEE", required = false),
                ActionParameter("calculated_daily_calories", "number", "Calculated daily calories", required = false),
                ActionParameter("is_realistic", "boolean", "Whether goal is realistic", required = false),
                ActionParameter("validation_reason", "string", "Validation reason/message", required = false),
                ActionParameter("user_overridden", "boolean", "Whether user chose to override safety recommendations", required = false)
            )
        )
    )
    
    override suspend fun executeFunction(
        functionName: String,
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        Logger.i("GoalManagement", "🎯 executeFunction called")
        Logger.i("GoalManagement", "🎯 Function: $functionName")
        Logger.i("GoalManagement", "🎯 Parameters: $params")
        Logger.i("GoalManagement", "🎯 Context userId: ${context.userId}")
        Logger.i("GoalManagement", "🎯 Context isAuthenticated: ${context.isAuthenticated}")
        
        // Verify authentication
        if (!context.isAuthenticated) {
            Logger.e("GoalManagement", "❌ Authentication required")
            return Result.failure(
                IllegalStateException("Authentication required for goal management")
            )
        }
        
        return when (functionName) {
            "save_goal" -> {
                Logger.i("GoalManagement", "✅ Calling saveGoal")
                saveGoal(params, context)
            }
            else -> {
                Logger.e("GoalManagement", "❌ Unknown function: $functionName")
                Result.failure(
                    IllegalArgumentException("Unknown function: $functionName")
                )
            }
        }
    }
    
    private suspend fun saveGoal(
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        Logger.i("GoalManagement", "🎯 saveGoal started")
        val userId = context.userId
        if (userId.isNullOrEmpty()) {
            Logger.e("GoalManagement", "❌ User ID is null or empty")
            return Result.failure(IllegalStateException("User ID required for saving goal"))
        }
        Logger.i("GoalManagement", "✅ User ID: $userId")
        
        val supabase = context.supabaseClient
        if (supabase == null) {
            Logger.e("GoalManagement", "❌ Supabase client not available")
            return Result.failure(IllegalStateException("Supabase client not available"))
        }
        Logger.i("GoalManagement", "✅ Supabase client available")
        
        return try {
            val goalTypeStr = params["goal_type"]?.lowercase()
            Logger.i("GoalManagement", "🎯 Goal type: $goalTypeStr")
            if (goalTypeStr == null) {
                Logger.e("GoalManagement", "❌ Missing goal_type")
                return Result.failure(IllegalArgumentException("Missing goal_type"))
            }
            
            val goalType = when (goalTypeStr) {
                "weight_loss" -> GoalType.WEIGHT_LOSS
                "muscle_gain" -> GoalType.MUSCLE_GAIN
                "maintenance" -> GoalType.MAINTENANCE
                else -> {
                    Logger.e("GoalManagement", "❌ Invalid goal_type: $goalTypeStr")
                    return Result.failure(IllegalArgumentException("Invalid goal_type: $goalTypeStr"))
                }
            }
            Logger.i("GoalManagement", "✅ Goal type enum: $goalType")
            
            val currentWeight = params["current_weight"]?.toDoubleOrNull()
            Logger.i("GoalManagement", "🎯 Current weight: $currentWeight")
            if (currentWeight == null) {
                Logger.e("GoalManagement", "❌ Invalid current_weight")
                return Result.failure(IllegalArgumentException("Invalid current_weight"))
            }
            
            val targetWeight = params["target_weight"]?.toDoubleOrNull()
            Logger.i("GoalManagement", "🎯 Target weight: $targetWeight")
            if (targetWeight == null) {
                Logger.e("GoalManagement", "❌ Invalid target_weight")
                return Result.failure(IllegalArgumentException("Invalid target_weight"))
            }
            
            val targetDate = params["target_date"]
            Logger.i("GoalManagement", "🎯 Target date: $targetDate")
            if (targetDate == null) {
                Logger.e("GoalManagement", "❌ Missing target_date")
                return Result.failure(IllegalArgumentException("Missing target_date"))
            }
            
            // Optional parameters
            val currentHeight = params["current_height"]?.toDoubleOrNull()
            val activityLevel = params["activity_level"]
            val calculatedBmr = params["calculated_bmr"]?.toDoubleOrNull()
            val calculatedTdee = params["calculated_tdee"]?.toDoubleOrNull()
            val calculatedDailyCalories = params["calculated_daily_calories"]?.toDoubleOrNull()
            val isRealistic = params["is_realistic"]?.toBooleanStrictOrNull()
            val validationReason = params["validation_reason"]
            val userOverridden = params["user_overridden"]?.toBooleanStrictOrNull() ?: false
            
            Logger.i("GoalManagement", "🎯 Optional params - height: $currentHeight, activity: $activityLevel")
            Logger.i("GoalManagement", "🎯 Calculations - BMR: $calculatedBmr, TDEE: $calculatedTdee, calories: $calculatedDailyCalories")
            Logger.i("GoalManagement", "🎯 User overridden: $userOverridden")
            
            val profileManager = ProfileManager(supabase)
            Logger.i("GoalManagement", "🎯 Calling ProfileManager.updateGoal")
            
            // Save goal using ProfileManager and propagate result status
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
                calculatedDailyCalories = calculatedDailyCalories,
                isRealistic = isRealistic,
                recommendedTargetDate = null,
                validationReason = validationReason,
                goalContext = null,
                userOverridden = userOverridden
            )
            
            if (updateResult.isFailure) {
                val error = updateResult.exceptionOrNull()
                Logger.e("GoalManagement", "❌ ProfileManager.updateGoal failed: ${error?.message}")
                return Result.failure(error ?: IllegalStateException("Failed to update goal"))
            }
            
            Logger.i("GoalManagement", "✅ Goal saved successfully!")
            Result.success(buildJsonObject {
                put("status", "success")
                put("message", "Goal saved successfully")
                put("userId", userId)
                put("goal_type", goalTypeStr)
                put("target_weight", targetWeight)
                put("target_date", targetDate)
            })
        } catch (e: Exception) {
            Logger.e("GoalManagement", "❌ Exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.profile.ProfileManager
import com.amigo.shared.utils.Logger

/**
 * Tool to get user's current health goal
 */
class GetCurrentGoalTool(
    private val profileManager: ProfileManager,
    private val userId: String
) : AmigoTool {
    override val name: String = "get_current_goal"
    
    override val description: String = 
        "Get the user's currently active health goal including type, target, and progress"
    
    override val parameters: Map<String, ToolParameter> = emptyMap()
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val profile = profileManager.getUserProfile(userId).getOrNull()
            
            if (profile == null) {
                return ToolResult.Error("User profile not found", "PROFILE_NOT_FOUND")
            }
            
            // Get current goal from profile
            val goalType = profile.goalType?.name ?: "NONE"
            val currentWeight = profile.weightKg
            val targetDate = profile.goalByWhen
            
            val result = mutableMapOf<String, Any>(
                "hasGoal" to (profile.goalType != null),
                "goalType" to goalType
            )
            
            if (currentWeight != null) {
                result["currentWeight"] = currentWeight
            }
            if (targetDate != null) {
                result["targetDate"] = targetDate
            }
            
            Logger.d("GetCurrentGoalTool", "Retrieved goal for user $userId: $goalType")
            ToolResult.Success(result)
        } catch (e: Exception) {
            Logger.e("GetCurrentGoalTool", "Failed to get current goal: ${e.message}")
            ToolResult.Error("Failed to retrieve current goal: ${e.message}", "EXECUTION_ERROR")
        }
    }
}

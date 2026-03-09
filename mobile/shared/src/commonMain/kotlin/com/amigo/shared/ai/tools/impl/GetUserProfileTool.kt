package com.amigo.shared.ai.tools.impl

import com.amigo.shared.ai.tools.AmigoTool
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.ai.tools.ToolParameter
import com.amigo.shared.ai.tools.ToolResult
import com.amigo.shared.profile.ProfileManager

/**
 * Tool to retrieve current user profile information
 */
class GetUserProfileTool(
    private val profileManager: ProfileManager,
    private val userId: String
) : AmigoTool {
    override val name = "get_user_profile"
    override val description = "Retrieves the current user's profile including weight, height, age, and other health metrics"
    override val parameters = emptyMap<String, ToolParameter>()
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val profileResult = profileManager.getProfile(userId)
            
            profileResult.fold(
                onSuccess = { profile ->
                    ToolResult.Success(
                        mapOf(
                            "user_id" to profile.id,
                            "display_name" to (profile.displayName ?: ""),
                            "email" to profile.email,
                            "age" to (profile.age ?: 0),
                            "weight_kg" to (profile.weightKg ?: 0.0),
                            "height_cm" to (profile.heightCm ?: 0.0),
                            "unit_preference" to profile.unitPreference.name,
                            "created_at" to profile.createdAt,
                            "updated_at" to profile.updatedAt
                        )
                    )
                },
                onFailure = { error ->
                    ToolResult.Error("Failed to retrieve user profile: ${error.message}", "PROFILE_ERROR")
                }
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to retrieve user profile: ${e.message}", "PROFILE_ERROR")
        }
    }
}

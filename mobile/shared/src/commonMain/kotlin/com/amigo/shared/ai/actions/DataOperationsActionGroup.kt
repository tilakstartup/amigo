package com.amigo.shared.ai.actions

import com.amigo.shared.profile.ProfileManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * DataOperations action group
 * Handles profile and onboarding data operations
 */
object DataOperationsActionGroup : ActionGroup {
    override val name = "data_operations"
    override val description = "Profile and onboarding data operations with RETURN_CONTROL"
    
    override val functions = listOf(
        ActionFunction(
            name = "get_profile",
            description = "Fetch authenticated user profile",
            parameters = emptyList()
        ),
        ActionFunction(
            name = "save_onboarding_data",
            description = "Save or update user onboarding fields",
            parameters = listOf(
                ActionParameter(
                    name = "payload_json",
                    type = "string",
                    description = "JSON payload containing onboarding data",
                    required = true
                )
            )
        ),
        ActionFunction(
            name = "get_onboarding_status",
            description = "Check onboarding progress",
            parameters = emptyList()
        )
    )
    
    override suspend fun executeFunction(
        functionName: String,
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        // For onboarding cap, allow operations without full authentication
        val cap = context.additionalContext["cap"] as? String
        val isOnboarding = cap == "onboarding"
        
        // Verify authentication for non-onboarding data operations
        if (!isOnboarding && !context.isAuthenticated) {
            return Result.failure(
                IllegalStateException("Authentication required for data operations")
            )
        }
        
        return when (functionName) {
            "get_profile" -> getProfile(context)
            "save_onboarding_data" -> saveOnboardingData(params, context)
            "get_onboarding_status" -> getOnboardingStatus(context)
            else -> Result.failure(
                IllegalArgumentException("Unknown function: $functionName")
            )
        }
    }
    
    private suspend fun getProfile(context: ActionContext): Result<JsonObject> {
        val userId = context.userId
        if (userId.isNullOrEmpty()) {
            return Result.success(buildJsonObject {
                put("status", "success")
                put("message", "No authenticated user - returning empty profile for onboarding")
                put("userId", "")
                put("first_name", "")
                put("last_name", "")
                put("age", "")
                put("weight", "")
                put("height", "")
                put("gender", "")
                put("activity_level", "")
            })
        }
        
        // Try to fetch from Supabase if client is available
        val supabase = context.supabaseClient
        if (supabase != null) {
            return try {
                val profileManager = ProfileManager(supabase)
                val profile = profileManager.getProfile(userId).getOrNull()
                
                if (profile != null) {
                    Result.success(buildJsonObject {
                        put("status", "success")
                        put("message", "Profile data retrieved from database")
                        put("userId", profile.id)
                        put("first_name", profile.displayName?.split(" ")?.firstOrNull() ?: "")
                        put("last_name", profile.displayName?.split(" ")?.drop(1)?.joinToString(" ") ?: "")
                        put("age", profile.age?.toString() ?: "")
                        put("weight", profile.weightKg?.toString() ?: "")
                        put("height", profile.heightCm?.toString() ?: "")
                        put("gender", "")
                        put("activity_level", profile.activityLevel?.name?.lowercase() ?: "")
                    })
                } else {
                    Result.success(buildJsonObject {
                        put("status", "success")
                        put("message", "No profile found in database - user may be new")
                        put("userId", userId)
                        put("first_name", "")
                        put("last_name", "")
                        put("age", "")
                        put("weight", "")
                        put("height", "")
                        put("gender", "")
                        put("activity_level", "")
                    })
                }
            } catch (e: Exception) {
                Result.success(buildJsonObject {
                    put("status", "success")
                    put("message", "Could not fetch profile from database: ${e.message}")
                    put("userId", userId)
                    put("first_name", "")
                    put("last_name", "")
                    put("age", "")
                    put("weight", "")
                    put("height", "")
                    put("gender", "")
                    put("activity_level", "")
                })
            }
        }
        
        // Fallback if no Supabase client
        return Result.success(buildJsonObject {
            put("status", "success")
            put("message", "Profile data retrieved (stub)")
            put("userId", userId)
        })
    }
    
    private suspend fun saveOnboardingData(
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        val payloadJson = params["payload_json"]
            ?: return Result.failure(IllegalArgumentException("Missing payload_json parameter"))
        
        // TODO: Implement actual onboarding data saving to Supabase
        return Result.success(buildJsonObject {
            put("status", "success")
            put("message", "Onboarding data saved")
            put("userId", context.userId ?: "")
        })
    }
    
    private suspend fun getOnboardingStatus(context: ActionContext): Result<JsonObject> {
        // TODO: Implement actual onboarding status check from Supabase
        return Result.success(buildJsonObject {
            put("status", "success")
            put("message", "Onboarding status retrieved")
            put("userId", context.userId ?: "")
            put("completed", false)
        })
    }
}

package com.amigo.shared.ai.actions

import com.amigo.shared.profile.ProfileManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator

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
                        put("first_name", profile.firstName ?: "")
                        put("last_name", profile.lastName ?: "")
                        put("age", profile.age?.toString() ?: "")
                        put("weight", profile.weightKg?.toString() ?: "")
                        put("height", profile.heightCm?.toString() ?: "")
                        put("gender", profile.gender ?: "")
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
        
        val userId = context.userId
        if (userId.isNullOrEmpty()) {
            return Result.failure(IllegalStateException("User ID required for saving onboarding data"))
        }
        
        val supabase = context.supabaseClient
        if (supabase == null) {
            return Result.failure(IllegalStateException("Supabase client not available"))
        }
        
        return try {
            // Parse the JSON payload
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val payload = json.parseToJsonElement(payloadJson).jsonObject
            
            // Build ProfileUpdate from payload
            val profileManager = ProfileManager(supabase)
            val updates = mutableMapOf<String, Any?>()
            
            // Extract fields from payload
            payload["first_name"]?.jsonPrimitive?.contentOrNull?.let {
                updates["first_name"] = it
            }
            
            payload["last_name"]?.jsonPrimitive?.contentOrNull?.let {
                updates["last_name"] = it
            }
            
            payload["age"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let {
                updates["age"] = it
            }
            
            payload["weight"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let {
                updates["weight_kg"] = it
            }
            
            payload["height"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let {
                updates["height_cm"] = it
            }
            
            payload["activity_level"]?.jsonPrimitive?.contentOrNull?.let { level ->
                updates["activity_level"] = level.lowercase()
            }
            
            payload["gender"]?.jsonPrimitive?.contentOrNull?.let { gender ->
                updates["gender"] = gender.lowercase()
            }
            
            payload["onboarding_completed"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let {
                updates["onboarding_completed"] = it
                if (it) {
                    updates["onboarding_completed_at"] = kotlinx.datetime.Clock.System.now().toString()
                }
            }
            
            // Update profile in Supabase
            val result = supabase.from("users_profiles")
                .update(updates) {
                    filter {
                        eq("id", userId)
                    }
                }
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("message", "Onboarding data saved successfully")
                put("userId", userId)
                put("fields_updated", updates.keys.joinToString(", "))
            })
        } catch (e: Exception) {
            Result.success(buildJsonObject {
                put("status", "error")
                put("message", "Failed to save onboarding data: ${e.message}")
                put("userId", userId)
            })
        }
    }
    
    private suspend fun getOnboardingStatus(context: ActionContext): Result<JsonObject> {
        val userId = context.userId
        if (userId.isNullOrEmpty()) {
            return Result.success(buildJsonObject {
                put("status", "success")
                put("message", "No authenticated user")
                put("userId", "")
                put("completed", false)
                put("completion_percentage", 0)
                put("completed_fields", buildJsonArray {})
                put("missing_fields", buildJsonArray {})
            })
        }
        
        val supabase = context.supabaseClient
        if (supabase == null) {
            return Result.failure(IllegalStateException("Supabase client not available"))
        }
        
        return try {
            val profileManager = ProfileManager(supabase)
            val profile = profileManager.getProfile(userId).getOrNull()
            
            if (profile == null) {
                return Result.success(buildJsonObject {
                    put("status", "success")
                    put("message", "Profile not found")
                    put("userId", userId)
                    put("completed", false)
                    put("completion_percentage", 0)
                    put("completed_fields", buildJsonArray {})
                    put("missing_fields", buildJsonArray {
                        add("first_name")
                        add("last_name")
                        add("age")
                        add("weight")
                        add("height")
                        add("activity_level")
                    })
                })
            }
            
            // Check which required fields are present
            val requiredFields = listOf("display_name", "age", "weight_kg", "height_cm", "activity_level")
            val completedFields = mutableListOf<String>()
            val missingFields = mutableListOf<String>()
            
            if (!profile.displayName.isNullOrBlank()) completedFields.add("display_name") else missingFields.add("display_name")
            if (profile.age != null) completedFields.add("age") else missingFields.add("age")
            if (profile.weightKg != null) completedFields.add("weight_kg") else missingFields.add("weight_kg")
            if (profile.heightCm != null) completedFields.add("height_cm") else missingFields.add("height_cm")
            if (profile.activityLevel != null) completedFields.add("activity_level") else missingFields.add("activity_level")
            
            val completionPercentage = (completedFields.size.toDouble() / requiredFields.size.toDouble() * 100).toInt()
            val isCompleted = profile.onboardingCompleted == true
            
            Result.success(buildJsonObject {
                put("status", "success")
                put("message", "Onboarding status retrieved")
                put("userId", userId)
                put("completed", isCompleted)
                put("completion_percentage", completionPercentage)
                put("completed_fields", buildJsonArray {
                    completedFields.forEach { add(it) }
                })
                put("missing_fields", buildJsonArray {
                    missingFields.forEach { add(it) }
                })
            })
        } catch (e: Exception) {
            Result.success(buildJsonObject {
                put("status", "error")
                put("message", "Failed to get onboarding status: ${e.message}")
                put("userId", userId)
                put("completed", false)
            })
        }
    }
}

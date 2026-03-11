package com.amigo.shared.profile

import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.data.models.UnitPreference
import com.amigo.shared.data.models.Theme
import com.amigo.shared.data.models.GoalType
import com.amigo.shared.data.models.ActivityLevel
import com.amigo.shared.utils.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import com.amigo.shared.utils.CurrentTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ProfileManager(private val supabase: SupabaseClient) {

    suspend fun getProfileOrThrow(userId: String): UserProfile {
        return getProfile(userId).getOrThrow()
    }

    suspend fun createProfileOrThrow(profile: UserProfile): UserProfile {
        return createProfile(profile).getOrThrow()
    }

    suspend fun updateProfileOrThrow(userId: String, updates: ProfileUpdate): UserProfile {
        return updateProfile(userId, updates).getOrThrow()
    }

    suspend fun getProfileOrNull(userId: String): UserProfile? {
        return getProfile(userId).getOrNull()
    }

    suspend fun createProfileOrNull(profile: UserProfile): UserProfile? {
        return createProfile(profile).getOrNull()
    }

    suspend fun updateProfileOrNull(userId: String, updates: ProfileUpdate): UserProfile? {
        return updateProfile(userId, updates).getOrNull()
    }
    
    /**
     * Get user profile by user ID
     */
    suspend fun getProfile(userId: String): Result<UserProfile> {
        return try {
            val profile = supabase.from("users_profiles")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<UserProfile>()
            
            Result.success(profile)
        } catch (e: Exception) {
            Logger.e("ProfileManager", "❌ getProfile failed for userId=$userId: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Create user profile
     */
    suspend fun createProfile(profile: UserProfile): Result<UserProfile> {
        return try {
            val created = supabase.from("users_profiles")
                .insert(profile)
                .decodeSingle<UserProfile>()
            
            Result.success(created)
        } catch (e: Exception) {
            Logger.e("ProfileManager", "❌ createProfile failed for userId=${profile.id}, email=${profile.email}: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Update user profile
     */
    suspend fun updateProfile(userId: String, updates: ProfileUpdate): Result<UserProfile> {
        return try {
            val updated = supabase.from("users_profiles")
                .update(updates.toMap()) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<UserProfile>()
            
            Result.success(updated)
        } catch (e: Exception) {
            Logger.e("ProfileManager", "❌ updateProfile failed for userId=$userId: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Delete user profile
     */
    suspend fun deleteProfile(userId: String): Result<Unit> {
        return try {
            supabase.from("users_profiles")
                .delete {
                    filter {
                        eq("id", userId)
                    }
                }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get user profile (alias for getProfile)
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile> {
        return getProfile(userId)
    }
    
    /**
     * Update user's health goal
     */
    @Suppress("DEPRECATION")
    suspend fun updateGoal(
        userId: String,
        goalType: GoalType,
        targetWeightKg: Double? = null,
        targetDate: String? = null,
        currentWeightKg: Double? = null,
        currentHeightCm: Double? = null,
        activityLevel: String? = null,
        calculatedBmr: Double? = null,
        calculatedTdee: Double? = null,
        calculatedDailyCalories: Double? = null,
        userDailyCalories: Double? = null,
        calculatedBmiStart: Double? = null,
        calculatedBmiTarget: Double? = null,
        weeklyMilestones: String? = null,  // JSON string
        isRealistic: Boolean? = null,
        recommendedTargetDate: String? = null,
        validationReason: String? = null,
        goalContext: String? = null,  // JSON string
        userOverridden: Boolean? = null
    ): Result<UserProfile> {
        return try {
            // Get current session - Supabase SDK manages session state
            val session = supabase.auth.currentSessionOrNull()
            if (session == null) {
                Logger.e("ProfileManager", "❌ No active Supabase session found!")
                return Result.failure(Exception("No active session. Please log in again."))
            }
            
            Logger.i("ProfileManager", "✅ Active session retrieved")
            Logger.i("ProfileManager", "🔍 Session token (first 50 chars): ${session.accessToken.take(50)}")
            Logger.i("ProfileManager", "🔍 Session expiresAt: ${session.expiresAt}")
            Logger.i("ProfileManager", "🔍 Using provided userId: $userId")
            
            // Use the provided userId parameter instead of trying to get it from session
            // This works around the Supabase SDK 3.x issue where session.user is null after importSession()
            if (userId.isBlank()) {
                Logger.e("ProfileManager", "❌ Provided user ID is blank")
                return Result.failure(Exception("Invalid user ID"))
            }
            
            val goalTypeValue = goalType.name.lowercase()
            // Try to update users_profiles, but don't fail if it doesn't work
            // The goal_type and goal_by_when fields may not exist in the table
            val updates = buildJsonObject {
                put("goal_type", goalTypeValue)
                targetDate?.let { put("goal_by_when", it) }
            }
            
            Logger.i("ProfileManager", "🔵 Attempting to update users_profiles for user: $userId")
            Logger.i("ProfileManager", "🔵 Update payload: $updates")
            
            var updated: UserProfile? = null
            try {
                updated = supabase.from("users_profiles")
                    .update(updates) {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingleOrNull<UserProfile>()
                    
                if (updated != null) {
                    Logger.i("ProfileManager", "✅ users_profiles updated successfully")
                } else {
                    Logger.w("ProfileManager", "⚠️ users_profiles update returned null - columns may not exist, continuing anyway")
                }
            } catch (e: Exception) {
                Logger.w("ProfileManager", "⚠️ users_profiles update failed (non-critical): ${e.message}")
                Logger.w("ProfileManager", "⚠️ Continuing with goal save anyway")
                // Don't throw - this update is not critical for goal saving
            }
            
            // If users_profiles update failed, fetch the current profile to return later
            if (updated == null) {
                Logger.i("ProfileManager", "🔵 Fetching current profile since update was skipped")
                updated = try {
                    supabase.from("users_profiles")
                        .select(columns = Columns.ALL) {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingle<UserProfile>()
                } catch (e: Exception) {
                    Logger.e("ProfileManager", "❌ Failed to fetch profile: ${e.message}")
                    throw Exception("User profile not found")
                }
            }

            Logger.i("ProfileManager", "🔵 Deactivating old goals for user: $userId")
            supabase.from("health_goals")
                .update(buildJsonObject {
                    put("is_active", false)
                }) {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true)
                    }
                }
            Logger.i("ProfileManager", "✅ Old goals deactivated")

            val healthGoalPayload = buildJsonObject {
                put("user_id", userId)
                put("goal_type", goalTypeValue)
                put("is_active", true)
                put("start_date", CurrentTime.nowIso8601())
                
                targetDate?.let {
                    put("target_date", it)
                    put("end_date", "${it}T00:00:00Z")
                }
                targetWeightKg?.let { put("target_weight", it) }
                currentWeightKg?.let { put("current_weight", it) }
                currentHeightCm?.let { put("current_height", it) }
                normalizeHealthGoalActivityLevel(activityLevel)?.let { put("activity_level", it) }
                calculatedBmr?.let { put("calculated_bmr", it) }
                calculatedTdee?.let { put("calculated_tdee", it) }
                calculatedDailyCalories?.let { put("calculated_daily_calories", it) }
                userDailyCalories?.let { put("user_daily_calories", it) }
                calculatedBmiStart?.let { put("calculated_bmi_start", it) }
                calculatedBmiTarget?.let { put("calculated_bmi_target", it) }
                isRealistic?.let { put("is_realistic", it) }
                recommendedTargetDate?.let { put("recommended_target_date", it) }
                validationReason?.let { put("validation_reason", it) }
                userOverridden?.let { put("user_overridden", it) }
                // Note: weeklyMilestones and goalContext are skipped for now as they need special handling
            }

            Logger.i("ProfileManager", "🔵 Inserting health goal")
            Logger.i("ProfileManager", "🔵 user_id=$userId, goal_type=$goalTypeValue")
            Logger.i("ProfileManager", "🔵 Payload keys: ${healthGoalPayload.keys}")
            Logger.i("ProfileManager", "🔵 Session user: ${session.user?.id}")
            Logger.i("ProfileManager", "🔵 Session access token present: ${session.accessToken.isNotEmpty()}")
            Logger.i("ProfileManager", "🔵 Session access token (first 50 chars): ${session.accessToken.take(50)}")
            
            // CRITICAL: Must select() after insert to detect RLS violations
            // Without select(), RLS policy failures are silent
            try {
                Logger.i("ProfileManager", "🔵 Calling insert with select...")
                Logger.i("ProfileManager", "🔵 healthGoalPayload = $healthGoalPayload")
                
                val insertResponse = try {
                    supabase.from("health_goals")
                        .insert(healthGoalPayload) {
                            select()
                        }
                        .decodeSingleOrNull<kotlinx.serialization.json.JsonObject>()
                } catch (decodeError: Exception) {
                    Logger.e("ProfileManager", "❌ Decode error (likely empty response from RLS block): ${decodeError.message}")
                    null
                }
                
                if (insertResponse != null) {
                    Logger.i("ProfileManager", "✅ Health goal inserted successfully!")
                    Logger.i("ProfileManager", "✅ Inserted goal id: ${insertResponse["id"]}")
                } else {
                    Logger.e("ProfileManager", "❌ Insert returned null - RLS policy blocked it")
                    Logger.e("ProfileManager", "❌ This means auth.uid() doesn't match user_id: $userId")
                    Logger.e("ProfileManager", "❌ Session user ID: ${session.user?.id}")
                    Logger.e("ProfileManager", "❌ Target user ID: $userId")
                    throw Exception("Failed to insert health goal - RLS policy violation. Session user: ${session.user?.id}, Target user: $userId")
                }
            } catch (insertError: Exception) {
                Logger.e("ProfileManager", "❌ Health goal insert FAILED: ${insertError.message}")
                Logger.e("ProfileManager", "❌ Error type: ${insertError::class.simpleName}")
                Logger.e("ProfileManager", "❌ Full error: $insertError")
                Logger.e("ProfileManager", "❌ This is likely an RLS policy violation")
                Logger.e("ProfileManager", "❌ Check that auth.uid() matches user_id: $userId")
                Logger.e("ProfileManager", "❌ Session user ID: ${session.user?.id}")
                insertError.printStackTrace()
                throw insertError
            }
            
            Result.success(updated!!)  // updated is guaranteed to be non-null here
        } catch (e: Exception) {
            Logger.e("ProfileManager", "❌ updateGoal failed for userId=$userId: ${e.message}")
            Result.failure(e)
        }
    }

    private fun normalizeHealthGoalActivityLevel(activityLevel: String?): String? {
        if (activityLevel.isNullOrBlank()) {
            return null
        }

        return when (activityLevel.trim().lowercase()) {
            "sedentary" -> "sedentary"
            "light", "lightly_active", "lightly active" -> "light"
            "moderate", "moderately_active", "moderately active" -> "moderate"
            "active", "very_active", "very active" -> "active"
            "very_active_plus", "extremely_active", "extremely active", "athlete" -> "very_active"
            else -> ActivityLevel.fromString(activityLevel)?.let {
                when (it) {
                    ActivityLevel.SEDENTARY -> "sedentary"
                    ActivityLevel.LIGHTLY_ACTIVE -> "light"
                    ActivityLevel.MODERATELY_ACTIVE -> "moderate"
                    ActivityLevel.VERY_ACTIVE -> "active"
                    ActivityLevel.EXTREMELY_ACTIVE -> "very_active"
                }
            }
        }
    }
    
    /**
     * Save onboarding data and mark onboarding as complete
     */
    @Suppress("DEPRECATION")
    suspend fun completeOnboarding(
        userId: String,
        onboardingData: ProfileUpdate
    ): Result<UserProfile> {
        return try {
            // Add onboarding completion timestamp
            val updates = buildJsonObject {
                // Copy all fields from onboardingData
                onboardingData.toMap().forEach { (key, value) ->
                    put(key, value)
                }
                // Add onboarding completion
                put("onboarding_completed", true)
                put("onboarding_completed_at", CurrentTime.nowIso8601())
            }
            
            val updated = supabase.from("users_profiles")
                .update(updates) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<UserProfile>()
            
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

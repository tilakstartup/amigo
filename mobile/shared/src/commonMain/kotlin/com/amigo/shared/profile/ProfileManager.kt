package com.amigo.shared.profile

import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.data.models.UnitPreference
import com.amigo.shared.data.models.Theme
import com.amigo.shared.data.models.GoalType
import com.amigo.shared.data.models.ActivityLevel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Clock
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
            println("❌ ProfileManager.getProfile failed for userId=$userId: ${e.message}")
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
            println("❌ ProfileManager.createProfile failed for userId=${profile.id}, email=${profile.email}: ${e.message}")
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
            println("❌ ProfileManager.updateProfile failed for userId=$userId: ${e.message}")
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
        calculatedBmiStart: Double? = null,
        calculatedBmiTarget: Double? = null,
        weeklyMilestones: List<Map<String, Any>>? = null,
        isRealistic: Boolean? = null,
        recommendedTargetDate: String? = null,
        validationReason: String? = null,
        goalContext: Map<String, Any>? = null
    ): Result<UserProfile> {
        return try {
            // Verify Supabase has an active session
            val currentSession = supabase.auth.currentSessionOrNull()
            if (currentSession == null) {
                println("❌ ProfileManager: No active Supabase session found!")
                return Result.failure(Exception("No active session. Please log in again."))
            }
            println("✅ ProfileManager: Active session found for user: ${currentSession.user?.id}")
            
            // Verify the session user matches the userId parameter
            val sessionUserId = currentSession.user?.id
            if (sessionUserId != userId) {
                println("⚠️ ProfileManager: Session user ID ($sessionUserId) doesn't match target user ID ($userId)")
            }
            
            val goalTypeValue = goalType.name.lowercase()
            val updates = mutableMapOf<String, Any>(
                "goal_type" to goalTypeValue
            )
            
            if (targetDate != null) {
                updates["goal_by_when"] = targetDate
            }
            
            println("🔵 ProfileManager: Updating users_profiles for user: $userId")
            val updated = supabase.from("users_profiles")
                .update(updates) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<UserProfile>()
            println("✅ ProfileManager: users_profiles updated successfully")

            println("🔵 ProfileManager: Deactivating old goals for user: $userId")
            supabase.from("health_goals")
                .update(mapOf("is_active" to false)) {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true)
                    }
                }
            println("✅ ProfileManager: Old goals deactivated")

            val healthGoalPayload = buildJsonObject {
                put("user_id", userId)
                put("goal_type", goalTypeValue)
                put("is_active", true)
                put("start_date", Clock.System.now().toString())
                
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
                calculatedBmiStart?.let { put("calculated_bmi_start", it) }
                calculatedBmiTarget?.let { put("calculated_bmi_target", it) }
                isRealistic?.let { put("is_realistic", it) }
                recommendedTargetDate?.let { put("recommended_target_date", it) }
                validationReason?.let { put("validation_reason", it) }
                // Note: weeklyMilestones and goalContext are skipped for now as they need special handling
            }

            println("🔵 ProfileManager: Inserting health goal")
            println("🔵 ProfileManager: user_id=$userId, goal_type=$goalTypeValue")
            println("🔵 ProfileManager: Session access token present: ${currentSession.accessToken.isNotEmpty()}")
            try {
                supabase.from("health_goals").insert(healthGoalPayload)
                println("✅ ProfileManager: Health goal insert completed successfully")
            } catch (insertError: Exception) {
                println("❌ ProfileManager: Health goal insert FAILED: ${insertError.message}")
                insertError.printStackTrace()
                throw insertError
            }
            
            Result.success(updated)
        } catch (e: Exception) {
            println("❌ ProfileManager.updateGoal failed for userId=$userId: ${e.message}")
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
    suspend fun completeOnboarding(
        userId: String,
        onboardingData: ProfileUpdate
    ): Result<UserProfile> {
        return try {
            // Add onboarding completion timestamp
            val updates = onboardingData.toMap().toMutableMap()
            updates["onboarding_completed"] = true
            updates["onboarding_completed_at"] = Clock.System.now().toString()
            
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

package com.amigo.shared.profile

import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.data.models.UnitPreference
import com.amigo.shared.data.models.Theme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class ProfileManager(private val supabase: SupabaseClient) {
    
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
}

package com.amigo.shared.subscription

import com.amigo.shared.data.models.Subscription
import com.amigo.shared.data.models.SubscriptionTier
import com.amigo.shared.data.models.SubscriptionStatus
import com.amigo.shared.data.models.Platform
import com.amigo.shared.data.models.UsageQuotas
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Clock

class SubscriptionManager(private val supabase: SupabaseClient) {
    
    /**
     * Get user's current subscription
     */
    suspend fun getSubscription(userId: String): Result<Subscription> {
        return try {
            val subscription = supabase.from("subscriptions")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                }
                .decodeSingle<Subscription>()
            
            Result.success(subscription)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create a new subscription for a user
     */
    suspend fun createSubscription(
        userId: String,
        tier: SubscriptionTier,
        platform: Platform? = null,
        platformSubscriptionId: String? = null
    ): Result<Subscription> {
        return try {
            val now = Clock.System.now().toString()
            val subscription = mapOf(
                "user_id" to userId,
                "tier" to tier.name.lowercase(),
                "status" to SubscriptionStatus.ACTIVE.name.lowercase(),
                "start_date" to now,
                "platform" to platform?.name?.lowercase(),
                "platform_subscription_id" to platformSubscriptionId,
                "monthly_ai_requests_used" to 0,
                "monthly_ai_requests_limit" to getQuotaLimit(tier)
            )
            
            val created = supabase.from("subscriptions")
                .insert(subscription)
                .decodeSingle<Subscription>()
            
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update subscription tier (upgrade/downgrade)
     */
    suspend fun updateTier(
        userId: String,
        newTier: SubscriptionTier
    ): Result<Subscription> {
        return try {
            val updates = mapOf(
                "tier" to newTier.name.lowercase(),
                "monthly_ai_requests_limit" to getQuotaLimit(newTier)
            )
            
            val updated = supabase.from("subscriptions")
                .update(updates) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                }
                .decodeSingle<Subscription>()
            
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Cancel subscription
     */
    suspend fun cancelSubscription(userId: String): Result<Subscription> {
        return try {
            val updates = mapOf(
                "status" to SubscriptionStatus.CANCELLED.name.lowercase(),
                "auto_renew" to false
            )
            
            val updated = supabase.from("subscriptions")
                .update(updates) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                }
                .decodeSingle<Subscription>()
            
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Increment AI request usage
     */
    suspend fun incrementAiUsage(userId: String): Result<Subscription> {
        return try {
            // Get current subscription
            val currentResult = getSubscription(userId)
            if (currentResult.isFailure) {
                return Result.failure(currentResult.exceptionOrNull()!!)
            }
            
            val current = currentResult.getOrNull()!!
            val newUsage = current.usageQuotas.monthlyAiRequestsUsed + 1
            
            val updates = mapOf(
                "monthly_ai_requests_used" to newUsage
            )
            
            val updated = supabase.from("subscriptions")
                .update(updates) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                }
                .decodeSingle<Subscription>()
            
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if user has reached their quota limit
     */
    suspend fun hasReachedQuota(userId: String): Boolean {
        return try {
            val result = getSubscription(userId)
            if (result.isFailure) return true // Assume quota reached on error
            
            val subscription = result.getOrNull()!!
            subscription.usageQuotas.monthlyAiRequestsUsed >= subscription.usageQuotas.monthlyAiRequestsLimit
        } catch (e: Exception) {
            true // Assume quota reached on error
        }
    }
    
    /**
     * Reset monthly usage (should be called at the start of each billing cycle)
     */
    suspend fun resetMonthlyUsage(userId: String): Result<Subscription> {
        return try {
            val updates = mapOf(
                "monthly_ai_requests_used" to 0
            )
            
            val updated = supabase.from("subscriptions")
                .update(updates) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                }
                .decodeSingle<Subscription>()
            
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get quota limit for a tier
     */
    private fun getQuotaLimit(tier: SubscriptionTier): Int {
        return when (tier) {
            SubscriptionTier.FREE -> 50
            SubscriptionTier.PRO -> Int.MAX_VALUE // Unlimited for Pro
        }
    }
}

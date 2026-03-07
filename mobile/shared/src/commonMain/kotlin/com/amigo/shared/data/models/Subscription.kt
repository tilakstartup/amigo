package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val userId: String,
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val startDate: String,
    val endDate: String? = null,
    val trialEndDate: String? = null,
    val autoRenew: Boolean = true,
    val platform: Platform? = null,
    val platformSubscriptionId: String? = null,
    val usageQuotas: UsageQuotas,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class SubscriptionTier {
    FREE,
    PRO
}

@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED,
    TRIAL
}

@Serializable
enum class Platform {
    IOS,
    ANDROID,
    WEB
}

@Serializable
data class UsageQuotas(
    val monthlyAiRequestsUsed: Int = 0,
    val monthlyAiRequestsLimit: Int = 50
)

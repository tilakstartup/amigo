package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserAIContext(
    val id: String,
    val userId: String,
    val patternProfile: PatternProfile? = null,
    val preferences: Map<String, String>? = null,
    val coachingStyle: CoachingStyle? = null,
    val contextSummary: String? = null,
    val activeGoalId: String? = null,
    val lastUpdated: String,
    val createdAt: String
)

@Serializable
data class PatternProfile(
    val mealPatterns: Map<String, String>? = null,
    val fastingPatterns: Map<String, String>? = null,
    val hydrationPatterns: Map<String, String>? = null,
    val goalSpecificPatterns: Map<String, Map<String, String>>? = null
)

@Serializable
data class CoachingStyle(
    val tone: String? = null,  // supportive, challenging, balanced
    val frequency: String? = null,  // high, medium, low
    val focusAreas: List<String>? = null
)

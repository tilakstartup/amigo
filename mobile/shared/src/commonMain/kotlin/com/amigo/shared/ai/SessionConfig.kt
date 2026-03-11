package com.amigo.shared.ai

import com.amigo.shared.ai.sessions.OnboardingSessionConfig
import com.amigo.shared.ai.sessions.GoalSettingSessionConfig
import kotlinx.serialization.Serializable

/**
 * Configuration for an AI conversation session.
 */
@Serializable
data class SessionConfig(
    val cap: String,
    val responsibilities: List<String>,
    val collectData: List<String>,
    val collectMetrics: List<String> = emptyList(),
    val initialMessage: String,
    val notes: List<String> = emptyList()
)

/**
 * Registry of all session configurations.
 * Each session is defined in its own file under ai/sessions/ for easy maintenance.
 */
object SessionConfigs {
    
    /**
     * Onboarding session for new users.
     * See: OnboardingSessionConfig.kt
     */
    val ONBOARDING = OnboardingSessionConfig.config
    
    /**
     * Goal setting session for defining health goals.
     * See: GoalSettingSessionConfig.kt
     */
    val GOAL_SETTING = GoalSettingSessionConfig.config
    
    /**
     * Get a session config by cap name.
     */
    fun getConfig(cap: String): SessionConfig? {
        val config = when (cap.lowercase()) {
            "onboarding" -> ONBOARDING
            "goal_setting" -> GOAL_SETTING
            else -> null
        }
        
        if (config != null) {
            println("✅ Successfully loaded session config '$cap' with ${config.responsibilities.size} responsibilities")
        } else {
            println("❌ No config found for '$cap'")
        }
        
        return config
    }
    
    /**
     * Get all available session configs.
     */
    fun getAllConfigs(): List<SessionConfig> {
        return listOf(ONBOARDING, GOAL_SETTING)
    }
}

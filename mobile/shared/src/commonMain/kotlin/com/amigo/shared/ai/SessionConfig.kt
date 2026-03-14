package com.amigo.shared.ai

import com.amigo.shared.ai.sessions.OnboardingSessionConfig
import com.amigo.shared.ai.sessions.GoalSettingSessionConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Configuration for an AI conversation session.
 */
@Serializable
data class SessionConfig(
    val hat: String,
    val responsibilities: List<String>,
    val data_to_be_collected: List<String>,
    val data_to_be_calculated: List<String> = emptyList(),
    @Transient
    val initial_message: String = "",
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
     * Get a session config by hat name.
     */
    fun getConfig(hat: String): SessionConfig? {
        val config = when (hat.lowercase()) {
            "onboarding" -> ONBOARDING
            "goal_setting" -> GOAL_SETTING
            else -> null
        }
        
        if (config != null) {
            println("✅ Successfully loaded session config '$hat' with ${config.responsibilities.size} responsibilities")
        } else {
            println("❌ No config found for '$hat'")
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

package com.amigo.shared.ai

/**
 * Loads session configurations from hardcoded Kotlin objects.
 * This is more reliable than YAML files and works consistently across all platforms.
 */
object SessionConfigLoader {
    
    /**
     * Load a session config by hat name.
     * Now uses hardcoded SessionConfigs object instead of YAML files.
     */
    fun loadConfig(hat: String): SessionConfig? {
        return try {
            println("📦 Loading session config '$hat' from SessionConfigs object...")
            val config = SessionConfigs.getConfig(hat)
            
            if (config != null) {
                println("✅ Successfully loaded config for '$hat': ${config.responsibilities.size} responsibilities, ${config.data_to_be_collected.size} data_to_be_collected, ${config.data_to_be_calculated.size} data_to_be_calculated")
            } else {
                println("❌ No config found for '$hat'")
            }
            
            config
        } catch (e: Exception) {
            println("❌ FAILED to load session config for '$hat': ${e.message}")
            println("❌ Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }
}

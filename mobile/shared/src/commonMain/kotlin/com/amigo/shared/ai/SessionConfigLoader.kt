package com.amigo.shared.ai

/**
 * Loads session configurations from hardcoded Kotlin objects.
 * This is more reliable than YAML files and works consistently across all platforms.
 */
object SessionConfigLoader {
    
    /**
     * Load a session config by cap name.
     * Now uses hardcoded SessionConfigs object instead of YAML files.
     */
    fun loadConfig(cap: String): SessionConfig? {
        return try {
            println("📦 Loading session config '$cap' from SessionConfigs object...")
            val config = SessionConfigs.getConfig(cap)
            
            if (config != null) {
                println("✅ Successfully loaded config for '$cap': ${config.responsibilities.size} responsibilities, ${config.collectData.size} collect_data, ${config.collectMetrics.size} collect_metrics")
            } else {
                println("❌ No config found for '$cap'")
            }
            
            config
        } catch (e: Exception) {
            println("❌ FAILED to load session config for '$cap': ${e.message}")
            println("❌ Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }
}

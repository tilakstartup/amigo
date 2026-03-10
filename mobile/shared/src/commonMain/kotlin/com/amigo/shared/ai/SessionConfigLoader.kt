package com.amigo.shared.ai

/**
 * Loads session configurations from YAML files.
 * Uses a simple custom YAML parser since multiplatform YAML libraries don't support iOS.
 */
object SessionConfigLoader {
    
    private val configCache = mutableMapOf<String, SessionConfig>()
    
    /**
     * Load a session config from YAML file.
     */
    fun loadConfig(cap: String): SessionConfig? {
        // Check cache first
        if (configCache.containsKey(cap)) {
            return configCache[cap]
        }
        
        return try {
            // Load YAML file from resources
            val yamlContent = readResourceFile("session-configs/$cap.yaml")
            val config = parseYamlConfig(yamlContent, cap)
            
            // Cache the config
            configCache[cap] = config
            config
        } catch (e: Exception) {
            println("⚠️ Failed to load session config for '$cap': ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Simple YAML parser for SessionConfig format.
     * This is a minimal parser that handles our specific YAML structure.
     */
    private fun parseYamlConfig(yaml: String, cap: String): SessionConfig {
        val lines = yaml.lines()
        val responsibilities = mutableListOf<String>()
        val collectData = mutableListOf<String>()
        val collectMetrics = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var initialMessage = ""
        
        var currentSection: String? = null
        var currentIndent = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            
            // Detect section headers
            when {
                trimmed.startsWith("responsibilities:") -> {
                    currentSection = "responsibilities"
                    currentIndent = line.indexOf("responsibilities:")
                }
                trimmed.startsWith("collect_data:") -> {
                    currentSection = "collect_data"
                    currentIndent = line.indexOf("collect_data:")
                }
                trimmed.startsWith("collect_metrics:") -> {
                    currentSection = "collect_metrics"
                    currentIndent = line.indexOf("collect_metrics:")
                }
                trimmed.startsWith("notes:") -> {
                    currentSection = "notes"
                    currentIndent = line.indexOf("notes:")
                }
                trimmed.startsWith("initial_message:") -> {
                    initialMessage = trimmed.substringAfter("initial_message:").trim().removeSurrounding("\"")
                    currentSection = null
                }
                trimmed.startsWith("cap:") -> {
                    // Already have cap from parameter
                    currentSection = null
                }
                trimmed.startsWith("-") && currentSection != null -> {
                    // List item
                    val value = trimmed.substring(1).trim().removeSurrounding("\"")
                    when (currentSection) {
                        "responsibilities" -> responsibilities.add(value)
                        "collect_data" -> collectData.add(value)
                        "collect_metrics" -> collectMetrics.add(value)
                        "notes" -> notes.add(value)
                    }
                }
            }
        }
        
        return SessionConfig(
            cap = cap,
            responsibilities = responsibilities,
            collectData = collectData,
            collectMetrics = collectMetrics,
            initialMessage = initialMessage,
            notes = notes
        )
    }
    
    /**
     * Clear the config cache (useful for testing or hot-reloading).
     */
    fun clearCache() {
        configCache.clear()
    }
}

/**
 * Platform-specific function to read resource files.
 * Implemented in platform-specific source sets.
 */
expect fun readResourceFile(path: String): String

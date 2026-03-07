package com.amigo.shared.ai

/**
 * Factory for creating BedrockClient instances
 */
object BedrockClientFactory {
    private var instance: BedrockClient? = null
    
    /**
     * Get or create a singleton BedrockClient instance
     */
    fun getInstance(
        apiEndpoint: String,
        getAuthToken: suspend () -> String?
    ): BedrockClient {
        return instance ?: synchronized(this) {
            instance ?: BedrockClient(
                apiEndpoint = apiEndpoint,
                getAuthToken = getAuthToken
            ).also { instance = it }
        }
    }
    
    /**
     * Create a new BedrockClient instance (for testing or multiple configurations)
     */
    fun create(
        apiEndpoint: String,
        getAuthToken: suspend () -> String?,
        maxRetries: Int = 3
    ): BedrockClient {
        return BedrockClient(
            apiEndpoint = apiEndpoint,
            getAuthToken = getAuthToken,
            maxRetries = maxRetries
        )
    }
    
    /**
     * Clear the singleton instance (useful for testing)
     */
    fun clearInstance() {
        instance?.close()
        instance = null
    }
}

// Synchronized function for KMP
expect fun <R> synchronized(lock: Any, block: () -> R): R

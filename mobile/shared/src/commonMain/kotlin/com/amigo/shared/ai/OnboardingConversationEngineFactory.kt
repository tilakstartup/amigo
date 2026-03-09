package com.amigo.shared.ai

import com.amigo.shared.auth.SessionManager

/**
 * Factory for creating OnboardingConversationEngine instances
 */
object OnboardingConversationEngineFactory {
    
    /**
     * Create a new OnboardingConversationEngine instance
     * 
     * @param apiEndpoint The Bedrock Lambda API endpoint
     * @param sessionManager The session manager for authentication
     * @return A new OnboardingConversationEngine instance
     */
    fun create(
        apiEndpoint: String,
        sessionManager: SessionManager
    ): OnboardingConversationEngine {
        val bedrockClient = BedrockClientFactory.create(
            apiEndpoint = apiEndpoint,
            getAuthToken = {
                sessionManager.getAccessToken() ?: ""
            }
        )
        
        return OnboardingConversationEngine(
            bedrockClient = bedrockClient,
            sessionManager = sessionManager
        )
    }
}

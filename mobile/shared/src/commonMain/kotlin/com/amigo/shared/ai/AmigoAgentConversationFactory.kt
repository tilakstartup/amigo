package com.amigo.shared.ai

import com.amigo.shared.auth.SessionManager

object AmigoAgentConversationFactory {
    fun create(
        apiEndpoint: String,
        sessionManager: SessionManager
    ): AmigoAgentConversation {
        val bedrockClient = BedrockClientFactory.create(
            apiEndpoint = apiEndpoint,
            getAuthToken = {
                sessionManager.getAccessToken() ?: ""
            }
        )

        return AmigoAgentConversation(
            bedrockClient = bedrockClient,
            sessionManager = sessionManager
        )
    }
}

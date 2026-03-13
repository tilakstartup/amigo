package com.amigo.shared.ai

import com.amigo.shared.auth.SessionManager
import com.amigo.shared.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient

object AmigoAgentConversationFactory {
    fun create(
        apiEndpoint: String,
        sessionManager: SessionManager,
        supabaseClient: SupabaseClient? = null
    ): AmigoAgentConversation {
        val bedrockClient = BedrockClientFactory.create(
            apiEndpoint = apiEndpoint,
            getAuthToken = {
                val token = sessionManager.getAccessToken()
                println("🔑 [Factory] getAccessToken() returned: ${if (token.isNullOrEmpty()) "EMPTY/NULL" else "Present (${token.length} chars)"}")
                token ?: ""
            }
        )

        return AmigoAgentConversation(
            bedrockClient = bedrockClient,
            sessionManager = sessionManager,
            supabaseClient = supabaseClient
        )
    }
    
    /**
     * Create AmigoAgentConversation with default SupabaseClient from provider.
     * This method doesn't expose SupabaseClient in the signature, making it safe
     * to call from platform-specific code (Android/iOS).
     */
    fun createWithDefaultClient(
        apiEndpoint: String,
        sessionManager: SessionManager
    ): AmigoAgentConversation {
        val supabaseClient = SupabaseClientProvider.getClient()
        return create(apiEndpoint, sessionManager, supabaseClient)
    }
}

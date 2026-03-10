package com.amigo.shared.ai

import com.amigo.shared.auth.SessionManager
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
}

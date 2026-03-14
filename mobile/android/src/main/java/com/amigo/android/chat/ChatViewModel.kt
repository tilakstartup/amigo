package com.amigo.android.chat

import androidx.lifecycle.viewModelScope
import com.amigo.android.onboarding.AgentConversationViewModel
import com.amigo.shared.auth.SessionManager
import kotlinx.coroutines.launch

class ChatViewModel(sessionManager: SessionManager) : AgentConversationViewModel(sessionManager) {

    init {
        viewModelScope.launch {
            try {
                engine.startSessionByName("general_chat")
            } catch (e: Exception) {
                println("Error starting general chat: ${e.message}")
            }
        }
    }

    // Override startOnboarding so AgentConversationScreen's LaunchedEffect is a no-op
    override fun startOnboarding() {
        // session already started in init
    }
}

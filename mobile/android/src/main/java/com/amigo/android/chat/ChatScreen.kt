package com.amigo.android.chat

import androidx.compose.runtime.Composable
import com.amigo.android.onboarding.AgentConversationScreen

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    AgentConversationScreen(
        viewModel = viewModel,
        onComplete = { /* no-op: general chat has no completion flow */ }
    )
}

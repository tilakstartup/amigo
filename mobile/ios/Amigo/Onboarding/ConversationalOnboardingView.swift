import SwiftUI
import shared

struct ConversationalOnboardingView: View {
    let sessionManager: SessionManager
    let onComplete: ([String: String]) -> Void

    private var chatConfig: ChatSessionConfig {
        // Use the predefined onboarding config from SessionConfigs
        ChatSessionConfig.onboarding
    }

    var body: some View {
        AgentConversationView(
            sessionManager: sessionManager,
            chatConfig: chatConfig,
            onComplete: onComplete
        )
    }
}

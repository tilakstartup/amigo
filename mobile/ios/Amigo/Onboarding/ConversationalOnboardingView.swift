import SwiftUI
import shared

struct ConversationalOnboardingView: View {
    let sessionManager: SessionManager
    let onComplete: ([String: String]) -> Void

    var body: some View {
        AgentConversationView(sessionManager: sessionManager, onComplete: onComplete)
    }
}

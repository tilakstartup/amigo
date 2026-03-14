import SwiftUI
import shared

struct ChatView: View {
    let sessionManager: SessionManager
    @StateObject private var viewModel: AgentConversationViewModel

    init(sessionManager: SessionManager) {
        self.sessionManager = sessionManager
        _viewModel = StateObject(wrappedValue: AgentConversationViewModel(
            sessionManager: sessionManager,
            chatConfig: ChatSessionConfig.generalChat
        ))
    }

    var body: some View {
        AgentConversationView(
            existingViewModel: viewModel,
            onComplete: { _ in }
        )
    }
}

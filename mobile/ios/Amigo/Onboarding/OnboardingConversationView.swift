import SwiftUI
import shared

struct OnboardingConversationView: View {
    let sessionManager: SessionManager
    let onComplete: ([String: String]) -> Void
    
    var body: some View {
        AgentConversationView(sessionManager: sessionManager, onComplete: onComplete)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
    }
}

// MARK: - Onboarding-specific extensions can be added here
// For example:
// - Progress indicators
// - Skip functionality
// - Custom onboarding header
// - Welcome messages
// - Help/tooltip overlays

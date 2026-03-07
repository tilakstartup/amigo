import SwiftUI
import shared

enum OnboardingStep {
    case conversational
    case completed
}

struct OnboardingCoordinator: View {
    @Binding var isOnboardingComplete: Bool
    @State private var currentStep: OnboardingStep = .conversational
    let sessionManager: SessionManager
    
    var body: some View {
        Group {
            switch currentStep {
            case .conversational:
                ConversationalOnboardingView(sessionManager: sessionManager) {
                    currentStep = .completed
                    completeOnboarding()
                }
            case .completed:
                EmptyView()
            }
        }
    }
    
    private func completeOnboarding() {
        // Save onboarding completion status per user
        if let user = sessionManager.getCurrentUser() {
            let userKey = "hasCompletedOnboarding_\(user.id)"
            UserDefaults.standard.set(true, forKey: userKey)
        }
        
        isOnboardingComplete = true
    }
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    return OnboardingCoordinator(isOnboardingComplete: .constant(false), sessionManager: sessionManager)
}

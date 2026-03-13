import SwiftUI
import shared

/// Coordinates session initialization and routing based on user state.
/// Handles loading, error states, and navigation to onboarding or main app.
struct SessionCoordinatorView: View {
    @ObservedObject var authViewModel: AuthViewModel
    let sessionManager: SessionManager
    @StateObject private var viewModel: SessionInitializationViewModel
    
    init(authViewModel: AuthViewModel, sessionManager: SessionManager) {
        self.authViewModel = authViewModel
        self.sessionManager = sessionManager
        
        // Create SessionInitializer from factory
        let sessionInitializer = SessionInitializerFactory.shared.create(sessionManager: sessionManager)
        _viewModel = StateObject(wrappedValue: SessionInitializationViewModel(sessionInitializer: sessionInitializer))
    }
    
    var body: some View {
        Group {
            switch viewModel.uiState {
            case .idle, .loading:
                LoadingView()
                
            case .error(let message):
                ErrorView(message: message) {
                    await retryInitialization()
                }
                
            case .navigateToOnboarding:
                ConversationalOnboardingView(
                    sessionManager: sessionManager,
                    onComplete: { profileData in
                        Task {
                            await handleOnboardingComplete(profileData)
                        }
                    }
                )
                
            case .navigateToMain:
                MainTabView(viewModel: authViewModel)
            }
        }
        .task {
            await initializeSession()
        }
    }
    
    private func initializeSession() async {
        guard let user = try? await authViewModel.getCurrentUser() else {
            return
        }
        viewModel.initialize(userId: user.id)
    }
    
    private func retryInitialization() async {
        guard let user = try? await authViewModel.getCurrentUser() else {
            return
        }
        viewModel.retry(userId: user.id)
    }
    
    private func handleOnboardingComplete(_ profileData: [String: String]) async {
        // Refresh session state after onboarding
        guard let user = try? await authViewModel.getCurrentUser() else {
            return
        }
        viewModel.initialize(userId: user.id)
    }
}

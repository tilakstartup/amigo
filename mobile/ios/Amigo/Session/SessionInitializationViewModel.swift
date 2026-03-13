import SwiftUI
import shared
import Combine

@MainActor
class SessionInitializationViewModel: ObservableObject {
    @Published var uiState: SessionUiState = .idle
    
    private let sessionInitializer: SessionInitializer
    
    init(sessionInitializer: SessionInitializer) {
        self.sessionInitializer = sessionInitializer
        observeState()
    }
    
    private func observeState() {
        // Observe Kotlin StateFlow from Swift
        Task {
            while !Task.isCancelled {
                let state = sessionInitializer.state.value
                
                switch state {
                case is InitializationState.Idle:
                    self.uiState = .idle
                case is InitializationState.Loading:
                    self.uiState = .loading
                case let success as InitializationState.Success:
                    handleSuccess(success.decision)
                case let error as InitializationState.Error:
                    self.uiState = .error(error.error.message ?? "Unknown error")
                default:
                    break
                }
                
                try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
            }
        }
    }
    
    private func handleSuccess(_ decision: RouteDecision) {
        switch decision {
        case is RouteDecision.MainApp:
            uiState = .navigateToMain
        case let onboarding as RouteDecision.Onboarding:
            uiState = .navigateToOnboarding(onboarding.config)
        default:
            break
        }
    }
    
    func initialize(userId: String) {
        Task {
            do {
                _ = try await sessionInitializer.initialize(userId: userId)
            } catch {
                await MainActor.run {
                    uiState = .error(error.localizedDescription)
                }
            }
        }
    }
    
    func retry(userId: String) {
        Task {
            do {
                _ = try await sessionInitializer.retry(userId: userId)
            } catch {
                await MainActor.run {
                    uiState = .error(error.localizedDescription)
                }
            }
        }
    }
}

enum SessionUiState {
    case idle
    case loading
    case navigateToMain
    case navigateToOnboarding(SessionConfig?)
    case error(String)
}

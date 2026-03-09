import SwiftUI
import shared
import Combine

@MainActor
class AuthViewModel: ObservableObject {
    @Published var email: String = ""
    @Published var password: String = ""
    @Published var confirmPassword: String = ""
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    @Published var isAuthenticated: Bool = false
    
    private let emailAuthenticator: EmailAuthenticator
    private let oauthAuthenticator: OAuthAuthenticator
    internal let sessionManager: SessionManager
    
    init(emailAuthenticator: EmailAuthenticator, oauthAuthenticator: OAuthAuthenticator, sessionManager: SessionManager) {
        self.emailAuthenticator = emailAuthenticator
        self.oauthAuthenticator = oauthAuthenticator
        self.sessionManager = sessionManager
        
        // Initialize session manager
        Task {
            await initializeSession()
        }
    }
    
    func initializeSession() async {
        do {
            try await sessionManager.initialize()
            isAuthenticated = sessionManager.isAuthenticated.value as! Bool
        } catch {
            print("Failed to initialize session: \(error)")
        }
    }
    
    func signIn() async {
        guard !email.isEmpty && !password.isEmpty else {
            errorMessage = "Please enter email and password"
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            let result = try await emailAuthenticator.signIn(email: email, password: password)
            
            if let success = result as? AuthResult.Success {
                try await sessionManager.saveSession(session: success.session)
                isAuthenticated = true
            } else if let error = result as? AuthResult.Error {
                // Provide user-friendly message for email not confirmed
                if error.code == .emailNotConfirmed {
                    errorMessage = "Please confirm your email address before signing in. Check your inbox for the confirmation link."
                } else {
                    errorMessage = error.message
                }
            }
        } catch {
            errorMessage = "Sign in failed: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func signUp() async {
        guard !email.isEmpty && !password.isEmpty else {
            errorMessage = "Please enter email and password"
            return
        }
        
        guard password == confirmPassword else {
            errorMessage = "Passwords do not match"
            return
        }
        
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        do {
            let result = try await emailAuthenticator.signUp(email: email, password: password)
            
            if let success = result as? AuthResult.Success {
                try await sessionManager.saveSession(session: success.session)
                isAuthenticated = true
            } else if let confirmation = result as? AuthResult.EmailConfirmationRequired {
                successMessage = "Account created! Please check your email (\(confirmation.email)) to confirm your account."
            } else if let error = result as? AuthResult.Error {
                errorMessage = error.message
            }
        } catch {
            errorMessage = "Sign up failed: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func signInWithGoogle() async {
        isLoading = true
        errorMessage = nil
        
        do {
            let result = try await oauthAuthenticator.signInWithGoogle()
            
            if let success = result as? AuthResult.Success {
                try await sessionManager.saveSession(session: success.session)
                isAuthenticated = true
            } else if let error = result as? AuthResult.Error {
                errorMessage = error.message
            }
        } catch {
            errorMessage = "Google sign in failed: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func signInWithApple() async {
        isLoading = true
        errorMessage = nil
        
        do {
            let result = try await oauthAuthenticator.signInWithApple()
            
            if let success = result as? AuthResult.Success {
                try await sessionManager.saveSession(session: success.session)
                isAuthenticated = true
            } else if let error = result as? AuthResult.Error {
                errorMessage = error.message
            }
        } catch {
            errorMessage = "Apple sign in failed: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func signOut() async {
        do {
            try await sessionManager.signOut()
            isAuthenticated = false
            email = ""
            password = ""
            confirmPassword = ""
        } catch {
            errorMessage = "Sign out failed: \(error.localizedDescription)"
        }
    }
    
    func clearError() {
        errorMessage = nil
    }
    
    func clearSuccess() {
        successMessage = nil
    }
    
    func getCurrentUser() -> User? {
        return sessionManager.getCurrentUser()
    }
    
    func handleDeepLinkSession(accessToken: String, refreshToken: String) async {
        isLoading = true
        errorMessage = nil
        
        do {
            let result = try await sessionManager.handleDeepLinkSession(accessToken: accessToken, refreshToken: refreshToken)
            
            if let success = result as? AuthResult.Success {
                isAuthenticated = true
            } else if let error = result as? AuthResult.Error {
                errorMessage = error.message
            }
        } catch {
            errorMessage = "Failed to handle session: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
}

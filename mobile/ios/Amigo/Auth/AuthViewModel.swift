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
        print("🔵 [iOS AuthViewModel] initializeSession() START")
        do {
            print("🔵 [iOS AuthViewModel] Calling sessionManager.initialize()...")
            try await sessionManager.initialize()
            let authStatus = sessionManager.isAuthenticated.value as! Bool
            print("🔵 [iOS AuthViewModel] SessionManager initialized, isAuthenticated=\(authStatus)")
            isAuthenticated = authStatus
            
            if authStatus {
                let user = try await sessionManager.getCurrentUser()
                print("✅ [iOS AuthViewModel] User logged in: id=\(user?.id ?? "nil"), email=\(user?.email ?? "nil")")
            } else {
                print("⚠️ [iOS AuthViewModel] No authenticated user")
            }
        } catch {
            print("❌ [iOS AuthViewModel] Failed to initialize session: \(error)")
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
                // SDK 3.x automatically saves the session
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
                // SDK 3.x automatically saves the session
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
        print("🔐 [iOS AuthViewModel] signInWithGoogle() START")
        isLoading = true
        errorMessage = nil
        
        do {
            print("🔐 [iOS AuthViewModel] Calling oauthAuthenticator.signInWithGoogle()...")
            let result = try await oauthAuthenticator.signInWithGoogle()
            
            if let success = result as? AuthResult.Success {
                print("✅ [iOS AuthViewModel] Google sign-in SUCCESS")
                print("✅ [iOS AuthViewModel] Session user.id: \(success.session.user.id)")
                print("✅ [iOS AuthViewModel] Session email: \(success.session.user.email)")
                print("✅ [iOS AuthViewModel] Access token length: \(success.session.accessToken.count)")
                print("✅ [iOS AuthViewModel] SDK 3.x automatically saves session")
                isAuthenticated = true
            } else if let error = result as? AuthResult.Error {
                print("❌ [iOS AuthViewModel] Google sign-in ERROR: \(error.message)")
                errorMessage = error.message
            }
        } catch {
            print("❌ [iOS AuthViewModel] Google sign-in EXCEPTION: \(error.localizedDescription)")
            errorMessage = "Google sign in failed: \(error.localizedDescription)"
        }
        
        isLoading = false
        print("🔐 [iOS AuthViewModel] signInWithGoogle() END")
    }
    
    func signInWithApple() async {
        print("🔐 [iOS AuthViewModel] signInWithApple() START")
        isLoading = true
        errorMessage = nil
        
        do {
            print("🔐 [iOS AuthViewModel] Calling oauthAuthenticator.signInWithApple()...")
            let result = try await oauthAuthenticator.signInWithApple()
            
            if let success = result as? AuthResult.Success {
                print("✅ [iOS AuthViewModel] Apple sign-in SUCCESS")
                print("✅ [iOS AuthViewModel] Session user.id: \(success.session.user.id)")
                print("✅ [iOS AuthViewModel] Session email: \(success.session.user.email)")
                print("✅ [iOS AuthViewModel] Access token length: \(success.session.accessToken.count)")
                print("✅ [iOS AuthViewModel] SDK 3.x automatically saves session")
                isAuthenticated = true
            } else if let error = result as? AuthResult.Error {
                print("❌ [iOS AuthViewModel] Apple sign-in ERROR: \(error.message)")
                errorMessage = error.message
            }
        } catch {
            print("❌ [iOS AuthViewModel] Apple sign-in EXCEPTION: \(error.localizedDescription)")
            errorMessage = "Apple sign in failed: \(error.localizedDescription)"
        }
        
        isLoading = false
        print("🔐 [iOS AuthViewModel] signInWithApple() END")
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
    
    func getCurrentUser() async throws -> User? {
        return try await sessionManager.getCurrentUser()
    }
    
    func handleDeepLinkSession(accessToken: String, refreshToken: String, expiresIn: Int64 = 3600) async {
        print("🔗 [iOS AuthViewModel] handleDeepLinkSession() START")
        print("🔗 [iOS AuthViewModel] Access token length: \(accessToken.count)")
        print("🔗 [iOS AuthViewModel] Refresh token length: \(refreshToken.count)")
        print("🔗 [iOS AuthViewModel] Expires in: \(expiresIn) seconds")
        isLoading = true
        errorMessage = nil
        
        do {
            print("🔗 [iOS AuthViewModel] Setting session from tokens...")
            try await sessionManager.setSessionFromTokens(accessToken: accessToken, refreshToken: refreshToken, expiresIn: expiresIn)
            
            print("✅ [iOS AuthViewModel] Session set successfully")
            // Trust that the session is valid - don't check for user
            // The user field may be null initially but the session is authenticated
            isAuthenticated = true
            print("✅ [iOS AuthViewModel] User authenticated via deep link")
        } catch {
            print("❌ [iOS AuthViewModel] Deep link session EXCEPTION: \(error.localizedDescription)")
            errorMessage = "Failed to handle session: \(error.localizedDescription)"
        }
        
        isLoading = false
        print("🔗 [iOS AuthViewModel] handleDeepLinkSession() END")
    }
}

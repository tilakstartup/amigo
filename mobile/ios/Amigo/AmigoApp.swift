import SwiftUI
import shared

@main
struct AmigoApp: App {
    @StateObject private var authViewModel: AuthViewModel
    @State private var hasCompletedWelcome: Bool
    
    // Shared session manager instance
    private let sessionManager: SessionManager
    
    init() {
        // Initialize Supabase client
        let supabaseUrl = AppConfig.shared.SUPABASE_URL
        let supabaseKey = AppConfig.shared.SUPABASE_ANON_KEY
        AuthFactory.shared.initializeSupabase(supabaseUrl: supabaseUrl, supabaseKey: supabaseKey)
        
        // Create authentication components
        let emailAuthenticator = AuthFactory.shared.createEmailAuthenticator()
        let oauthAuthenticator = AuthFactory.shared.createOAuthAuthenticator()
        let secureStorage = SecureStorage()
        let sharedSessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
        
        // Store session manager for use in onboarding
        self.sessionManager = sharedSessionManager
        
        // Initialize view model
        _authViewModel = StateObject(wrappedValue: AuthViewModel(
            emailAuthenticator: emailAuthenticator,
            oauthAuthenticator: oauthAuthenticator,
            sessionManager: sharedSessionManager
        ))
        
        // Check onboarding status (device-level for welcome)
        _hasCompletedWelcome = State(initialValue: UserDefaults.standard.bool(forKey: "hasCompletedWelcome"))
    }
    
    var body: some Scene {
        WindowGroup {
            Group {
                if !hasCompletedWelcome {
                    // Show pre-auth welcome screens
                    WelcomeView {
                        UserDefaults.standard.set(true, forKey: "hasCompletedWelcome")
                        hasCompletedWelcome = true
                    }
                } else if !authViewModel.isAuthenticated {
                    // Show authentication screens
                    LoginView(viewModel: authViewModel)
                } else {
                    // Use SessionCoordinatorView for authenticated users
                    SessionCoordinatorView(
                        authViewModel: authViewModel,
                        sessionManager: sessionManager
                    )
                }
            }
            .onOpenURL { url in
                handleDeepLink(url)
            }
        }
    }
    
    private func handleDeepLink(_ url: URL) {
        NSLog("🔗 Deep link received: \(url)")
        
        // Check if this is an auth callback
        if url.scheme == "amigo" && url.host == "auth" {
            NSLog("🔗 OAuth callback detected")
            
            // Parse tokens from URL fragment
            // URL format: amigo://auth#access_token=xxx&refresh_token=yyy&...
            Task {
                do {
                    let fragment = url.fragment ?? ""
                    NSLog("🔗 URL fragment: \(fragment)")
                    
                    // Parse query parameters from fragment
                    var params: [String: String] = [:]
                    for param in fragment.components(separatedBy: "&") {
                        let parts = param.components(separatedBy: "=")
                        if parts.count == 2 {
                            params[parts[0]] = parts[1]
                        }
                    }
                    
                    guard let accessToken = params["access_token"],
                          let refreshToken = params["refresh_token"] else {
                        NSLog("❌ Missing tokens in OAuth callback")
                        await MainActor.run {
                            authViewModel.errorMessage = "Missing authentication tokens"
                        }
                        return
                    }
                    
                    // Parse expires_in from URL (default to 3600 if not present)
                    let expiresIn = Int64(params["expires_in"] ?? "3600") ?? 3600
                    
                    NSLog("🔗 Extracted tokens from URL")
                    NSLog("🔗 Access token length: \(accessToken.count)")
                    NSLog("🔗 Refresh token length: \(refreshToken.count)")
                    NSLog("🔗 Expires in: \(expiresIn) seconds")
                    
                    // Use the AuthViewModel's deep link handler
                    await authViewModel.handleDeepLinkSession(accessToken: accessToken, refreshToken: refreshToken, expiresIn: expiresIn)
                    
                } catch {
                    NSLog("❌ Error handling OAuth callback: \(error.localizedDescription)")
                    await MainActor.run {
                        authViewModel.errorMessage = "Failed to handle OAuth callback: \(error.localizedDescription)"
                    }
                }
            }
        } else {
            NSLog("🔗 Non-auth deep link: \(url)")
        }
    }
}

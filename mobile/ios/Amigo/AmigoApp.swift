import SwiftUI
import shared

@main
struct AmigoApp: App {
    @StateObject private var authViewModel: AuthViewModel
    @State private var hasCompletedOnboarding: Bool
    @State private var hasCompletedWelcome: Bool
    
    // Shared session manager instance
    private let sessionManager: SessionManager
    
    init() {
        // Initialize Supabase client
        let supabaseUrl = "https://hibbnohfwvbglyxgyaav.supabase.co"
        let supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpYmJub2hmd3ZiZ2x5eGd5YWF2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI4NjQwNDMsImV4cCI6MjA4ODQ0MDA0M30.8acSzRLPqFFOf1WF-k5BECV8Vfdx1bVlaKTxM_s26Rc"
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
        
        // Check onboarding status (device-level for welcome, will check user-level for onboarding)
        _hasCompletedWelcome = State(initialValue: UserDefaults.standard.bool(forKey: "hasCompletedWelcome"))
        _hasCompletedOnboarding = State(initialValue: false) // Will be checked per-user
    }
    
    var body: some Scene {
        WindowGroup {
            Group {
                if !hasCompletedWelcome {
                    // Show pre-auth welcome screens
                    WelcomeView(currentPage: .constant(0)) {
                        UserDefaults.standard.set(true, forKey: "hasCompletedWelcome")
                        hasCompletedWelcome = true
                    }
                } else if !authViewModel.isAuthenticated {
                    // Show authentication screens
                    LoginView(viewModel: authViewModel)
                } else if !hasCompletedOnboarding {
                    // Show post-auth onboarding
                    OnboardingCoordinator(
                        isOnboardingComplete: $hasCompletedOnboarding,
                        sessionManager: sessionManager
                    )
                } else {
                    // Show main app
                    ContentView(viewModel: authViewModel)
                }
            }
            .onOpenURL { url in
                handleDeepLink(url)
            }
            .onChange(of: authViewModel.isAuthenticated) { isAuthenticated in
                if isAuthenticated {
                    // Check user-specific onboarding status when user logs in
                    checkUserOnboardingStatus()
                } else {
                    // Reset onboarding status when user logs out
                    hasCompletedOnboarding = false
                }
            }
            .onAppear {
                // Check onboarding status on app launch if already authenticated
                if authViewModel.isAuthenticated {
                    checkUserOnboardingStatus()
                }
            }
        }
    }
    
    private func checkUserOnboardingStatus() {
        // Check if current user has completed onboarding
        if let user = authViewModel.getCurrentUser() {
            let userKey = "hasCompletedOnboarding_\(user.id)"
            hasCompletedOnboarding = UserDefaults.standard.bool(forKey: userKey)
        }
    }
    
    private func handleDeepLink(_ url: URL) {
        print("Deep link received: \(url)")
        
        // Check if this is an auth callback
        if url.scheme == "amigo" && url.host == "auth" {
            // Extract tokens from URL fragment
            if let fragment = url.fragment {
                print("Fragment: \(fragment)")
                
                // Parse fragment parameters
                let params = fragment.components(separatedBy: "&").reduce(into: [String: String]()) { result, param in
                    let parts = param.components(separatedBy: "=")
                    if parts.count == 2 {
                        result[parts[0]] = parts[1]
                    }
                }
                
                if let accessToken = params["access_token"],
                   let refreshToken = params["refresh_token"] {
                    print("Tokens found, handling session")
                    
                    Task {
                        await authViewModel.handleDeepLinkSession(accessToken: accessToken, refreshToken: refreshToken)
                    }
                }
            }
        }
    }
}

import SwiftUI
import shared

@main
struct AmigoApp: App {
    @StateObject private var authViewModel: AuthViewModel
    
    init() {
        // Initialize Supabase client
        let supabaseUrl = "https://hibbnohfwvbglyxgyaav.supabase.co"
        let supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpYmJub2hmd3ZiZ2x5eGd5YWF2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI4NjQwNDMsImV4cCI6MjA4ODQ0MDA0M30.8acSzRLPqFFOf1WF-k5BECV8Vfdx1bVlaKTxM_s26Rc"
        AuthFactory.shared.initializeSupabase(supabaseUrl: supabaseUrl, supabaseKey: supabaseKey)
        
        // Create authentication components
        let emailAuthenticator = AuthFactory.shared.createEmailAuthenticator()
        let oauthAuthenticator = AuthFactory.shared.createOAuthAuthenticator()
        let secureStorage = SecureStorage()
        let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
        
        // Initialize view model
        _authViewModel = StateObject(wrappedValue: AuthViewModel(
            emailAuthenticator: emailAuthenticator,
            oauthAuthenticator: oauthAuthenticator,
            sessionManager: sessionManager
        ))
    }
    
    var body: some Scene {
        WindowGroup {
            Group {
                if authViewModel.isAuthenticated {
                    ContentView(viewModel: authViewModel)
                } else {
                    LoginView(viewModel: authViewModel)
                }
            }
            .onOpenURL { url in
                handleDeepLink(url)
            }
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

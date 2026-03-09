import SwiftUI
import shared

struct ProfileView: View {
    @ObservedObject var viewModel: AuthViewModel
    
    var body: some View {
        NavigationView {
            List {
                Section {
                    if let user = viewModel.getCurrentUser() {
                        HStack {
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 50))
                                .foregroundColor(.pink)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text(user.email)
                                    .font(.headline)
                                Text("Signed in")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }
                
                Section("Health Goals") {
                    NavigationLink(destination: GoalManagementView(sessionManager: viewModel.sessionManager)) {
                        HStack {
                            Image(systemName: "target")
                                .foregroundColor(.pink)
                            Text("My Goal")
                        }
                    }
                }
                
                Section("Settings") {
                    Button(action: {
                        resetOnboarding()
                    }) {
                        HStack {
                            Image(systemName: "arrow.counterclockwise")
                                .foregroundColor(.orange)
                            Text("Reset Onboarding")
                                .foregroundColor(.primary)
                        }
                    }
                }
                
                Section {
                    Button(action: {
                        Task {
                            await viewModel.signOut()
                        }
                    }) {
                        HStack {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                .foregroundColor(.red)
                            Text("Sign Out")
                                .foregroundColor(.red)
                        }
                    }
                }
            }
            .navigationTitle("Profile")
        }
    }
    
    private func resetOnboarding() {
        if let user = viewModel.getCurrentUser() {
            let userKey = "hasCompletedOnboarding_\(user.id)"
            UserDefaults.standard.removeObject(forKey: userKey)
            exit(0)
        }
    }
}

#Preview {
    let supabaseUrl = "https://your-project.supabase.co"
    let supabaseKey = "your-anon-key"
    AuthFactory.shared.initializeSupabase(supabaseUrl: supabaseUrl, supabaseKey: supabaseKey)
    let emailAuthenticator = AuthFactory.shared.createEmailAuthenticator()
    let oauthAuthenticator = AuthFactory.shared.createOAuthAuthenticator()
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    let viewModel = AuthViewModel(
        emailAuthenticator: emailAuthenticator,
        oauthAuthenticator: oauthAuthenticator,
        sessionManager: sessionManager
    )
    
    return ProfileView(viewModel: viewModel)
}

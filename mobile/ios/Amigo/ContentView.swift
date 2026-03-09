import SwiftUI
import shared

struct ContentView: View {
    @ObservedObject var viewModel: AuthViewModel
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Image(systemName: "heart.fill")
                    .imageScale(.large)
                    .font(.system(size: 60))
                    .foregroundStyle(.pink)
                
                Text("Welcome to Amigo!")
                    .font(.title)
                    .fontWeight(.bold)
                
                if let user = viewModel.getCurrentUser() {
                    Text("Signed in as:")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    
                    Text(user.email)
                        .font(.body)
                        .fontWeight(.medium)
                }
                
                Text("Your AI health coach is ready to help you achieve your goals.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                Spacer()
            }
            .padding()
            .navigationTitle("Dashboard")
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
    
    return ContentView(viewModel: viewModel)
}

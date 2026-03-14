import SwiftUI
import shared

struct MainTabView: View {
    @ObservedObject var viewModel: AuthViewModel
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            ContentView(viewModel: viewModel)
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }
                .tag(0)

            ChatView(sessionManager: viewModel.sessionManager)
                .tabItem {
                    Label("Chat", systemImage: "message.fill")
                }
                .tag(1)

            ProfileView(viewModel: viewModel)
                .tabItem {
                    Label("Profile", systemImage: "person.fill")
                }
                .tag(2)
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

    return MainTabView(viewModel: viewModel)
}

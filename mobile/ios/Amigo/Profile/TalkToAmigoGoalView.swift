import SwiftUI
import shared

struct TalkToAmigoGoalView: View {
    let sessionManager: SessionManager
    let goalType: String
    @State private var conversationEngine: AmigoConversationEngine?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        Group {
            if isLoading {
                ProgressView("Setting up Amigo...")
            } else if let engine = conversationEngine {
                AmigoConversationView(
                    conversationEngine: engine,
                    title: "Set Your Goal",
                    onComplete: { data in
                        // Goal has been saved by the SaveGoalTool
                        // Just dismiss
                        dismiss()
                    }
                )
            } else {
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 60))
                    .foregroundColor(.orange)
                
                Text("Unable to Start Conversation")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text(userFacingDescription(for: errorMessage))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                
                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                }
                
                Button("Go to Profile") {
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            }
        }
        .task {
            await setupConversationEngine()
        }
    }
    
    private func setupConversationEngine() async {
        NSLog("🔧 TalkToAmigoGoalView: Starting setup...")
        do {
            try await sessionManager.initialize()
            _ = try await sessionManager.validateSession()

            // Get Bedrock client
            let apiEndpoint = ProcessInfo.processInfo.environment["BEDROCK_API_ENDPOINT"] 
                ?? "https://n96755fzqk.execute-api.us-east-1.amazonaws.com/dev/invoke"
            
            NSLog("🔧 TalkToAmigoGoalView: Creating Bedrock client...")
            let bedrockClient = BedrockClientFactory.shared.create(
                apiEndpoint: apiEndpoint,
                getAuthToken: KotlinSuspendFunction0Wrapper(block: { [weak sessionManager] in
                    do {
                        return try await sessionManager?.getAccessToken() ?? ""
                    } catch {
                        return ""
                    }
                }) as KotlinSuspendFunction0,
                maxRetries: 3
            )
            
            // Resolve user ID from hydrated user or JWT fallback
            guard let userId = try await resolveUserId() else {
                throw NSError(domain: "TalkToAmigo", code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "No valid user session - please sign out and sign in again"])
            }
            
            let profileManager = ProfileManagerFactory.shared.create()
            NSLog("🔧 TalkToAmigoGoalView: Fetching profile...")

            let currentUser = sessionManager.getCurrentUser()
            let userProfile = try await profileManager.getProfileOrNull(userId: userId) ?? UserProfile(
                id: userId,
                email: currentUser?.email ?? "\(userId)@amigo.local",
                displayName: currentUser?.displayName,
                avatarUrl: currentUser?.avatarUrl,
                age: nil,
                heightCm: nil,
                weightKg: nil,
                goalType: nil,
                goalByWhen: nil,
                activityLevel: nil,
                dietaryPreferences: nil,
                onboardingCompleted: true,
                onboardingCompletedAt: ISO8601DateFormatter().string(from: Date()),
                unitPreference: .metric,
                language: "en",
                theme: .auto_,
                createdAt: ISO8601DateFormatter().string(from: Date()),
                updatedAt: ISO8601DateFormatter().string(from: Date())
            )

            NSLog("🔧 TalkToAmigoGoalView: Creating tool registry...")
            let calculationEngine = GoalCalculationEngine()
            let toolRegistry = AmigoToolFactory.shared.createGoalPlanningRegistry(
                profileManager: profileManager,
                userId: userId,
                calculationEngine: calculationEngine
            )

            NSLog("🔧 TalkToAmigoGoalView: Creating GoalSettingContext...")
            let context = GoalSettingContext(
                userProfile: userProfile,
                toolRegistry: toolRegistry
            )

            NSLog("🔧 TalkToAmigoGoalView: Creating AmigoConversationEngine...")
            let engine = AmigoConversationEngine(
                bedrockClient: bedrockClient,
                context: context
            )

            NSLog("✅ TalkToAmigoGoalView: Setup complete!")
            await MainActor.run {
                self.conversationEngine = engine
                self.isLoading = false
            }
            return
        } catch {
            let errorMsg = "Error: \(error.localizedDescription)"
            NSLog("❌ TalkToAmigoGoalView: \(errorMsg)")
            NSLog("❌ TalkToAmigoGoalView: Full error: \(error)")
            await MainActor.run {
                self.errorMessage = errorMsg
                self.isLoading = false
            }
        }
    }

    private func resolveUserId() async throws -> String? {
        if let currentUser = sessionManager.getCurrentUser(), !currentUser.id.isEmpty {
            return currentUser.id
        }

        guard let accessToken = try await sessionManager.getAccessToken(), !accessToken.isEmpty else {
            return nil
        }

        return extractSubFromJWT(accessToken)
    }

    private func extractSubFromJWT(_ token: String) -> String? {
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return nil }

        var payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")

        let remainder = payload.count % 4
        if remainder > 0 {
            payload += String(repeating: "=", count: 4 - remainder)
        }

        guard let data = Data(base64Encoded: payload),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sub = json["sub"] as? String,
              !sub.isEmpty else {
            return nil
        }

        return sub
    }

    private func userFacingDescription(for error: String?) -> String {
        guard let error = error?.lowercased() else {
            return "Please try again."
        }

        if error.contains("session") || error.contains("token") {
            return "Your session seems invalid. Please sign out and sign in again."
        }

        if error.contains("profile") {
            return "We couldn't load your profile. Please try again."
        }

        return "Something went wrong while setting up Talk to Amigo. Please try again."
    }
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    return TalkToAmigoGoalView(sessionManager: sessionManager, goalType: "weight_loss")
}

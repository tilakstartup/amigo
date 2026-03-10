import SwiftUI
import shared

enum OnboardingStep {
    case conversational
    case saving
    case completed
}

struct OnboardingCoordinator: View {
    @Binding var isOnboardingComplete: Bool
    @State private var currentStep: OnboardingStep = .conversational
    @State private var errorMessage: String?
    let sessionManager: SessionManager
    
    var body: some View {
        Group {
            switch currentStep {
            case .conversational:
                ConversationalOnboardingView(sessionManager: sessionManager) { profileData in
                    currentStep = .saving
                    completeOnboarding(profileData: profileData)
                }            case .saving:
                VStack(spacing: 20) {
                    ProgressView()
                        .scaleEffect(1.5)
                    Text("Creating your profile...")
                        .font(.headline)
                    
                    if let error = errorMessage {
                        Text(error)
                            .foregroundColor(.red)
                            .padding()
                    }
                }
            case .completed:
                EmptyView()
            }
        }
    }
    
    private func completeOnboarding(profileData: [String: String]) {
        // Save profile data to Supabase FIRST, then mark onboarding complete
        Task {
            NSLog("🔧 OnboardingCoordinator: Starting to save profile...")
            do {
                try await sessionManager.initialize()
                _ = try await sessionManager.validateSession()
                guard let userId = try await resolveUserId() else {
                    throw NSError(domain: "OnboardingCoordinator", code: -1,
                                userInfo: [NSLocalizedDescriptionKey: "Session expired. Please sign in again."])
                }

                try await saveProfileData(userId: userId, profileData: profileData)
                
                // Only mark as complete after profile is saved
                await MainActor.run {
                    let userKey = "hasCompletedOnboarding_\(userId)"
                    UserDefaults.standard.set(true, forKey: userKey)
                    NSLog("✅ OnboardingCoordinator: Marked onboarding as complete")
                    isOnboardingComplete = true
                }
            } catch {
                NSLog("❌ OnboardingCoordinator: Failed to save profile: \(error)")
                await MainActor.run {
                    if let nsError = error as NSError?,
                       nsError.domain == "OnboardingCoordinator",
                       nsError.code == -1 {
                        errorMessage = "Session expired. Please sign in again."
                    } else {
                        errorMessage = "Failed to save profile. Please try again."
                    }
                    // Go back to conversational step to retry
                    DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                        currentStep = .conversational
                    }
                }
            }
        }
    }
    
    private func saveProfileData(userId: String, profileData: [String: String]) async throws {
        let profileManager = ProfileManagerFactory.shared.create()
        let currentUser = sessionManager.getCurrentUser()
        let currentUserEmail = currentUser?.email ?? ""
        let currentAvatarUrl = currentUser?.avatarUrl
        
        NSLog("🔧 OnboardingCoordinator: Saving profile for user \(userId)")
        NSLog("🔧 OnboardingCoordinator: Profile data: \(profileData)")

        let normalizedEmail: String
        if currentUserEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            normalizedEmail = "\(userId)@amigo.local"
            NSLog("⚠️ OnboardingCoordinator: User email missing, using fallback email \(normalizedEmail)")
        } else {
            normalizedEmail = currentUserEmail
        }
        
        // Convert profile data to proper types
        var goalType: GoalType? = nil
        if let goalTypeStr = profileData["goalType"] {
            goalType = GoalType.companion.fromString(value: goalTypeStr)
        }
        
        var activityLevel: ActivityLevel? = nil
        if let activityLevelStr = profileData["activityLevel"] {
            activityLevel = ActivityLevel.companion.fromString(value: activityLevelStr)
        }
        
        let dietaryPrefs = profileData["dietaryPreferences"]?
            .components(separatedBy: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
        
        let age: KotlinInt? = profileData["age"].flatMap { Int32($0) }.map { KotlinInt(value: $0) }
        let height: KotlinDouble? = profileData["height"].flatMap { Double($0) }.map { KotlinDouble(value: $0) }
        let weight: KotlinDouble? = profileData["weight"].flatMap { Double($0) }.map { KotlinDouble(value: $0) }
        
        let profileUpdate = ProfileUpdate(
            displayName: profileData["name"],
            avatarUrl: nil,
            age: age,
            heightCm: height,
            weightKg: weight,
            goalType: goalType,
            goalByWhen: profileData["goalByWhen"],
            activityLevel: activityLevel,
            dietaryPreferences: dietaryPrefs,
            onboardingCompleted: true,
            onboardingCompletedAt: ISO8601DateFormatter().string(from: Date()),
            unitPreference: nil,
            language: nil,
            theme: nil
        )

        let now = ISO8601DateFormatter().string(from: Date())
        let newProfile = UserProfile(
            id: userId,
            email: normalizedEmail,
            displayName: profileData["name"],
            avatarUrl: currentAvatarUrl,
            age: age,
            heightCm: height,
            weightKg: weight,
            goalType: goalType,
            goalByWhen: profileData["goalByWhen"],
            activityLevel: activityLevel,
            dietaryPreferences: dietaryPrefs,
            onboardingCompleted: true,
            onboardingCompletedAt: ISO8601DateFormatter().string(from: Date()),
            unitPreference: .metric,
            language: "en",
            theme: .auto_,
            createdAt: now,
            updatedAt: now
        )

        // Try update first (works when profile already exists)
        if try await profileManager.updateProfileOrNull(userId: userId, updates: profileUpdate) != nil {
            NSLog("✅ OnboardingCoordinator: Profile updated successfully")
            return
        }

        NSLog("🔧 OnboardingCoordinator: Update failed, trying create...")
        if try await profileManager.createProfileOrNull(profile: newProfile) != nil {
            NSLog("✅ OnboardingCoordinator: Profile created successfully")
            return
        }

        // Force one session resync and retry once
        NSLog("🔧 OnboardingCoordinator: Save failed, re-syncing session and retrying...")
        try await sessionManager.initialize()
        _ = try await sessionManager.validateSession()

        if try await profileManager.updateProfileOrNull(userId: userId, updates: profileUpdate) != nil {
            NSLog("✅ OnboardingCoordinator: Profile updated successfully after retry")
            return
        }

        if try await profileManager.createProfileOrNull(profile: newProfile) != nil {
            NSLog("✅ OnboardingCoordinator: Profile created successfully after retry")
            return
        }

        NSLog("🔧 OnboardingCoordinator: KMP save failed, trying REST upsert fallback...")
        try await saveProfileViaRest(
            userId: userId,
            email: normalizedEmail,
            displayName: profileData["name"],
            avatarUrl: currentAvatarUrl,
            age: age?.int32Value,
            heightCm: height?.doubleValue,
            weightKg: weight?.doubleValue,
            goalType: goalType,
            goalByWhen: profileData["goalByWhen"],
            activityLevel: activityLevel,
            dietaryPreferences: dietaryPrefs,
            onboardingCompletedAt: ISO8601DateFormatter().string(from: Date())
        )
        NSLog("✅ OnboardingCoordinator: Profile saved via REST fallback")
        return

        throw NSError(domain: "OnboardingCoordinator", code: -5,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to create profile"])
    }

    private func saveProfileViaRest(
        userId: String,
        email: String,
        displayName: String?,
        avatarUrl: String?,
        age: Int32?,
        heightCm: Double?,
        weightKg: Double?,
        goalType: GoalType?,
        goalByWhen: String?,
        activityLevel: ActivityLevel?,
        dietaryPreferences: [String]?,
        onboardingCompletedAt: String
    ) async throws {
        guard let accessToken = try await sessionManager.getAccessToken(), !accessToken.isEmpty else {
            throw NSError(domain: "OnboardingCoordinator", code: -6,
                        userInfo: [NSLocalizedDescriptionKey: "Missing access token"])
        }

        guard let url = URL(string: "\(AppConfig.shared.SUPABASE_URL)/rest/v1/users_profiles") else {
            throw NSError(domain: "OnboardingCoordinator", code: -7,
                        userInfo: [NSLocalizedDescriptionKey: "Invalid Supabase URL"])
        }

        let now = ISO8601DateFormatter().string(from: Date())
        var payload: [String: Any] = [
            "id": userId,
            "email": email,
            "onboarding_completed": true,
            "onboarding_completed_at": onboardingCompletedAt,
            "updated_at": now
        ]
        if displayName != nil { payload["display_name"] = displayName }
        if avatarUrl != nil { payload["avatar_url"] = avatarUrl }
        if let age = age { payload["age"] = age }
        if let heightCm = heightCm { payload["height_cm"] = heightCm }
        if let weightKg = weightKg { payload["weight_kg"] = weightKg }
        if let goalType = goalType { payload["goal_type"] = String(describing: goalType).replacingOccurrences(of: "_", with: "-").lowercased().replacingOccurrences(of: "-", with: "_") }
        if goalByWhen != nil { payload["goal_by_when"] = goalByWhen }
        if let activityLevel = activityLevel { payload["activity_level"] = String(describing: activityLevel).replacingOccurrences(of: "_", with: "-").lowercased().replacingOccurrences(of: "-", with: "_") }
        if dietaryPreferences != nil { payload["dietary_preferences"] = dietaryPreferences }
        payload["created_at"] = now

        let bodyData = try JSONSerialization.data(withJSONObject: payload)

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = bodyData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=representation,resolution=merge-duplicates", forHTTPHeaderField: "Prefer")
        request.setValue("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpYmJub2hmd3ZiZ2x5eGd5YWF2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI4NjQwNDMsImV4cCI6MjA4ODQ0MDA0M30.8acSzRLPqFFOf1WF-k5BECV8Vfdx1bVlaKTxM_s26Rc", forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let responseText = String(data: data, encoding: .utf8) ?? "Unknown"
            NSLog("❌ OnboardingCoordinator REST upsert failed: \(responseText)")
            throw NSError(domain: "OnboardingCoordinator", code: -8,
                        userInfo: [NSLocalizedDescriptionKey: "REST upsert failed"])
        }
    }

    private func resolveUserId() async throws -> String? {
        if let user = sessionManager.getCurrentUser(), !user.id.isEmpty {
            return user.id
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
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    return OnboardingCoordinator(isOnboardingComplete: .constant(false), sessionManager: sessionManager)
}

import SwiftUI
import shared

struct ProfileSetupView: View {
    @StateObject private var viewModel = ProfileSetupViewModel()
    let onComplete: () -> Void
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Personal Information")) {
                    TextField("Name", text: $viewModel.name)
                    
                    Stepper("Age: \(viewModel.age)", value: $viewModel.age, in: 13...120)
                }
                
                Section(header: Text("Measurements")) {
                    Picker("Unit System", selection: $viewModel.unitSystem) {
                        Text("Metric").tag(UnitSystem.metric)
                        Text("Imperial").tag(UnitSystem.imperial)
                    }
                    .pickerStyle(.segmented)
                    
                    if viewModel.unitSystem == .metric {
                        HStack {
                            Text("Height")
                            Spacer()
                            TextField("cm", value: $viewModel.heightCm, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                            Text("cm")
                                .foregroundColor(.secondary)
                        }
                        
                        HStack {
                            Text("Weight")
                            Spacer()
                            TextField("kg", value: $viewModel.weightKg, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                            Text("kg")
                                .foregroundColor(.secondary)
                        }
                    } else {
                        HStack {
                            Text("Height")
                            Spacer()
                            TextField("ft", value: $viewModel.heightFeet, format: .number)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 50)
                            Text("ft")
                                .foregroundColor(.secondary)
                            TextField("in", value: $viewModel.heightInches, format: .number)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 50)
                            Text("in")
                                .foregroundColor(.secondary)
                        }
                        
                        HStack {
                            Text("Weight")
                            Spacer()
                            TextField("lbs", value: $viewModel.weightLbs, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                            Text("lbs")
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                if let errorMessage = viewModel.errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("Set Up Your Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Continue") {
                        Task {
                            await viewModel.saveProfile()
                            if viewModel.isProfileSaved {
                                onComplete()
                            }
                        }
                    }
                    .disabled(!viewModel.isValid || viewModel.isSaving)
                }
            }
            .overlay {
                if viewModel.isSaving {
                    ProgressView()
                        .scaleEffect(1.5)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black.opacity(0.2))
                }
            }
        }
    }
}

enum UnitSystem {
    case metric
    case imperial
}

@MainActor
class ProfileSetupViewModel: ObservableObject {
    @Published var name: String = ""
    @Published var age: Int = 25
    @Published var unitSystem: UnitSystem = .metric
    
    // Metric
    @Published var heightCm: Double = 170.0
    @Published var weightKg: Double = 70.0
    
    // Imperial
    @Published var heightFeet: Int = 5
    @Published var heightInches: Int = 7
    @Published var weightLbs: Double = 154.0
    
    @Published var errorMessage: String?
    @Published var isSaving: Bool = false
    @Published var isProfileSaved: Bool = false
    
    private let profileManager: ProfileManager
    private let sessionManager: SessionManager
    
    init() {
        // Initialize Supabase client
        let supabaseUrl = AppConfig.shared.SUPABASE_URL
        let supabaseKey = AppConfig.shared.SUPABASE_ANON_KEY
        AuthFactory.shared.initializeSupabase(supabaseUrl: supabaseUrl, supabaseKey: supabaseKey)
        
        let supabaseClient = SupabaseClientProvider.shared.getClient()
        self.profileManager = ProfileManager(supabase: supabaseClient)
        let secureStorage = SecureStorage()
        self.sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    }
    
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        age >= 13 && age <= 120 &&
        (unitSystem == .metric ? (heightCm >= 50 && heightCm <= 300 && weightKg >= 20 && weightKg <= 500) :
         (heightFeet >= 1 && heightInches >= 0 && heightInches < 12 && weightLbs >= 44 && weightLbs <= 1100))
    }
    
    func saveProfile() async {
        guard isValid else {
            errorMessage = "Please fill in all fields with valid values"
            return
        }
        
        isSaving = true
        errorMessage = nil
        
        do {
            // Get current user ID
            guard let userId = try? await sessionManager.getCurrentUser()?.id, !userId.isEmpty else {
                errorMessage = "No user session found"
                isSaving = false
                return
            }
            
            // Convert to metric if needed
            let heightInCm: Double
            let weightInKg: Double
            
            if unitSystem == .metric {
                heightInCm = heightCm
                weightInKg = weightKg
            } else {
                // Convert imperial to metric
                heightInCm = Double(heightFeet * 12 + heightInches) * 2.54
                weightInKg = weightLbs * 0.453592
            }
            
            // Create profile update
            let update = ProfileUpdate(
                firstName: name.components(separatedBy: " ").first,
                lastName: name.components(separatedBy: " ").dropFirst().joined(separator: " ").isEmpty ? nil : name.components(separatedBy: " ").dropFirst().joined(separator: " "),
                displayName: name,
                avatarUrl: nil,
                age: KotlinInt(value: Int32(age)),
                heightCm: KotlinDouble(value: heightInCm),
                weightKg: KotlinDouble(value: weightInKg),
                gender: nil,
                goalType: nil,
                goalByWhen: nil,
                activityLevel: nil,
                dietaryPreferences: nil,
                onboardingCompleted: nil,
                onboardingCompletedAt: nil,
                unitPreference: unitSystem == .metric ? .metric : .imperial,
                language: nil,
                theme: nil
            )
            
            if try await profileManager.updateProfileOrNull(userId: userId, updates: update) != nil {
                isProfileSaved = true
            } else {
                errorMessage = "Failed to save profile"
            }
        } catch {
            errorMessage = "Failed to save profile: \(error.localizedDescription)"
        }
        
        isSaving = false
    }
}

#Preview {
    ProfileSetupView(onComplete: {})
}

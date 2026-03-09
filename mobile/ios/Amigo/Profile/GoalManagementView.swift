import SwiftUI
import shared

struct GoalManagementView: View {
    @StateObject private var viewModel: GoalManagementViewModel
    @State private var showTalkToAmigo = false
    
    init(sessionManager: SessionManager) {
        _viewModel = StateObject(wrappedValue: GoalManagementViewModel(sessionManager: sessionManager))
    }
    
    var body: some View {
        VStack(spacing: 0) {
            if viewModel.isLoading {
                ProgressView()
                    .scaleEffect(1.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 24) {
                        // Current Goal Section
                        if let currentGoal = viewModel.currentGoalType {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Current Goal")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                
                                GoalCard(
                                    goalType: currentGoal,
                                    isSelected: true,
                                    onSelect: {}
                                )
                            }
                            .padding(.horizontal)
                        }
                        
                        // Options Section
                        VStack(spacing: 16) {
                            Text("How would you like to set your goal?")
                                .font(.headline)
                                .padding(.horizontal)
                            
                            Button(action: {
                                showTalkToAmigo = true
                            }) {
                                HStack {
                                    Image(systemName: "message.fill")
                                        .font(.system(size: 24))
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Talk to Amigo")
                                            .font(.headline)
                                        Text("Let Amigo help you set a smart goal with calculations")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                }
                                .padding()
                                .background(Color.pink.opacity(0.1))
                                .cornerRadius(12)
                            }
                            .buttonStyle(.plain)
                            .padding(.horizontal)
                        }
                        
                        // Manual Goal Selection Section
                        VStack(alignment: .leading, spacing: 12) {
                            Text(viewModel.currentGoalType == nil ? "Choose your goal" : "Or choose manually")
                                .font(.headline)
                                .foregroundColor(.secondary)
                                .padding(.horizontal)
                            
                            VStack(spacing: 12) {
                                ForEach([GoalType.weightLoss, GoalType.muscleGain, GoalType.maintenance, GoalType.improvedEnergy, GoalType.betterSleep].filter { $0 != viewModel.currentGoalType }, id: \.self) { goalType in
                                    GoalCard(
                                        goalType: goalType,
                                        isSelected: false,
                                        onSelect: {
                                            viewModel.selectedGoal = goalType
                                            viewModel.showConfirmation = true
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal)
                        }
                        
                        // Only show error if it's not a "no profile" error
                        if let errorMessage = viewModel.errorMessage, !errorMessage.contains("Failed to load goal") {
                            Text(errorMessage)
                                .foregroundColor(.red)
                                .font(.caption)
                                .padding()
                        }
                    }
                    .padding(.vertical)
                }
            }
        }
        .navigationTitle("My Goal")
        .navigationBarTitleDisplayMode(.large)
        .alert("Change Goal?", isPresented: $viewModel.showConfirmation) {
            Button("Cancel", role: .cancel) {
                viewModel.selectedGoal = nil
            }
            Button("Change Goal") {
                Task {
                    await viewModel.updateGoal()
                }
            }
        } message: {
            if let selectedGoal = viewModel.selectedGoal {
                Text("Are you sure you want to change your goal to \(goalTypeDisplayName(selectedGoal))?")
            }
        }
        .sheet(isPresented: $showTalkToAmigo) {
            if let currentGoal = viewModel.currentGoalType {
                TalkToAmigoGoalView(
                    sessionManager: viewModel.sessionManager,
                    goalType: goalTypeToString(currentGoal)
                )
            } else {
                // Default to weight_loss if no current goal
                TalkToAmigoGoalView(
                    sessionManager: viewModel.sessionManager,
                    goalType: "weight_loss"
                )
            }
        }
        .task {
            await viewModel.loadCurrentGoal()
        }
    }
    
    private func goalTypeDisplayName(_ goalType: GoalType) -> String {
        switch goalType {
        case .weightLoss:
            return "Weight Loss"
        case .muscleGain:
            return "Muscle Gain"
        case .maintenance:
            return "Maintenance"
        case .improvedEnergy:
            return "Improved Energy"
        case .betterSleep:
            return "Better Sleep"
        default:
            return "Unknown"
        }
    }
    
    private func goalTypeToString(_ goalType: GoalType) -> String {
        switch goalType {
        case .weightLoss:
            return "weight_loss"
        case .muscleGain:
            return "muscle_gain"
        case .maintenance:
            return "maintenance"
        case .improvedEnergy:
            return "improved_energy"
        case .betterSleep:
            return "better_sleep"
        default:
            return "weight_loss"
        }
    }
}

struct GoalCard: View {
    let goalType: GoalType
    let isSelected: Bool
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 16) {
                // Icon
                Text(goalIcon)
                    .font(.system(size: 32))
                    .frame(width: 50, height: 50)
                    .background(isSelected ? Color.pink.opacity(0.2) : Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                // Content
                VStack(alignment: .leading, spacing: 4) {
                    Text(goalDisplayName)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(goalDescription)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.pink)
                        .font(.system(size: 24))
                }
            }
            .padding()
            .background(isSelected ? Color.pink.opacity(0.05) : Color(.systemGray6))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.pink : Color.clear, lineWidth: 2)
            )
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
        .disabled(isSelected)
    }
    
    private var goalIcon: String {
        switch goalType {
        case .weightLoss:
            return "⚖️"
        case .muscleGain:
            return "💪"
        case .maintenance:
            return "🎯"
        case .improvedEnergy:
            return "⚡️"
        case .betterSleep:
            return "😴"
        default:
            return "❓"
        }
    }
    
    private var goalDisplayName: String {
        switch goalType {
        case .weightLoss:
            return "Weight Loss"
        case .muscleGain:
            return "Muscle Gain"
        case .maintenance:
            return "Maintenance"
        case .improvedEnergy:
            return "Improved Energy"
        case .betterSleep:
            return "Better Sleep"
        default:
            return "Unknown"
        }
    }
    
    private var goalDescription: String {
        switch goalType {
        case .weightLoss:
            return "Lose weight and improve body composition"
        case .muscleGain:
            return "Build muscle and increase strength"
        case .maintenance:
            return "Maintain current weight and health"
        case .improvedEnergy:
            return "Boost energy levels throughout the day"
        case .betterSleep:
            return "Improve sleep quality and duration"
        default:
            return ""
        }
    }
}

@MainActor
class GoalManagementViewModel: ObservableObject {
    @Published var currentGoalType: GoalType?
    @Published var selectedGoal: GoalType?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showConfirmation = false
    
    private let profileManager: ProfileManager
    let sessionManager: SessionManager
    
    init(sessionManager: SessionManager) {
        self.sessionManager = sessionManager
        self.profileManager = ProfileManagerFactory.shared.create()
    }
    
    func loadCurrentGoal() async {
        guard let userId = sessionManager.getCurrentUser()?.id, !userId.isEmpty else {
            errorMessage = "No user session found"
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        if let profile = try? await profileManager.getProfileOrNull(userId: userId) {
            currentGoalType = profile.goalType
        } else {
            // Don't show error for missing profile - this is expected for new users
            currentGoalType = nil
        }
        
        isLoading = false
    }
    
    func updateGoal() async {
                guard let userId = sessionManager.getCurrentUser()?.id,
                            !userId.isEmpty,
              let newGoal = selectedGoal else {
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        let update = ProfileUpdate(
            displayName: nil,
            avatarUrl: nil,
            age: nil,
            heightCm: nil,
            weightKg: nil,
            goalType: newGoal,
            goalByWhen: nil,
            activityLevel: nil,
            dietaryPreferences: nil,
            onboardingCompleted: nil,
            onboardingCompletedAt: nil,
            unitPreference: nil,
            language: nil,
            theme: nil
        )
        
        if (try? await profileManager.updateProfileOrNull(userId: userId, updates: update)) != nil {
            currentGoalType = newGoal
            selectedGoal = nil
        } else {
            errorMessage = "Failed to update goal"
        }
        
        isLoading = false
    }
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    return GoalManagementView(sessionManager: sessionManager)
}

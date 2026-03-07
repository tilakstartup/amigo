import SwiftUI
import shared

struct GoalSelectionView: View {
    @StateObject private var viewModel = GoalSelectionViewModel()
    let onComplete: () -> Void
    let allowSkip: Bool
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("What's your health goal?")
                    .font(.title2)
                    .fontWeight(.bold)
                    .padding(.top, 20)
                
                Text("Choose a goal to help Amigo personalize your coaching. You can change this anytime in settings.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                ScrollView {
                    VStack(spacing: 12) {
                        GoalOptionView(
                            icon: "arrow.down.circle.fill",
                            title: "Weight Loss",
                            description: "Lose weight in a healthy, sustainable way",
                            color: .blue,
                            isSelected: viewModel.selectedGoal == .weightLoss
                        ) {
                            viewModel.selectedGoal = .weightLoss
                        }
                        
                        GoalOptionView(
                            icon: "figure.strengthtraining.traditional",
                            title: "Muscle Gain",
                            description: "Build muscle and increase strength",
                            color: .orange,
                            isSelected: viewModel.selectedGoal == .muscleGain
                        ) {
                            viewModel.selectedGoal = .muscleGain
                        }
                        
                        GoalOptionView(
                            icon: "equal.circle.fill",
                            title: "Maintenance",
                            description: "Maintain your current weight and health",
                            color: .green,
                            isSelected: viewModel.selectedGoal == .maintenance
                        ) {
                            viewModel.selectedGoal = .maintenance
                        }
                        
                        GoalOptionView(
                            icon: "bolt.fill",
                            title: "Improved Energy",
                            description: "Boost your energy levels throughout the day",
                            color: .yellow,
                            isSelected: viewModel.selectedGoal == .improvedEnergy
                        ) {
                            viewModel.selectedGoal = .improvedEnergy
                        }
                        
                        GoalOptionView(
                            icon: "moon.stars.fill",
                            title: "Better Sleep",
                            description: "Improve your sleep quality and patterns",
                            color: .purple,
                            isSelected: viewModel.selectedGoal == .betterSleep
                        ) {
                            viewModel.selectedGoal = .betterSleep
                        }
                    }
                    .padding(.horizontal)
                }
                
                VStack(spacing: 12) {
                    Button(action: {
                        Task {
                            await viewModel.saveGoal()
                            if viewModel.isGoalSaved {
                                onComplete()
                            }
                        }
                    }) {
                        Text("Continue")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(viewModel.selectedGoal != nil ? Color.pink : Color.gray)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                    .disabled(viewModel.selectedGoal == nil || viewModel.isSaving)
                    .padding(.horizontal)
                    
                    if allowSkip {
                        Button(action: onComplete) {
                            Text("Skip for now")
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(.bottom, 20)
                
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .foregroundColor(.red)
                        .font(.caption)
                        .padding(.horizontal)
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

struct GoalOptionView: View {
    let icon: String
    let title: String
    let description: String
    let color: Color
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 15) {
                Image(systemName: icon)
                    .font(.system(size: 30))
                    .foregroundColor(color)
                    .frame(width: 50)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.pink)
                        .font(.system(size: 24))
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color.pink.opacity(0.1) : Color(.systemGray6))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.pink : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}

enum HealthGoalType {
    case weightLoss
    case muscleGain
    case maintenance
    case improvedEnergy
    case betterSleep
    
    var apiValue: String {
        switch self {
        case .weightLoss: return "weight_loss"
        case .muscleGain: return "muscle_gain"
        case .maintenance: return "maintenance"
        case .improvedEnergy: return "improved_energy"
        case .betterSleep: return "better_sleep"
        }
    }
}

@MainActor
class GoalSelectionViewModel: ObservableObject {
    @Published var selectedGoal: HealthGoalType?
    @Published var errorMessage: String?
    @Published var isSaving: Bool = false
    @Published var isGoalSaved: Bool = false
    
    func saveGoal() async {
        guard let goal = selectedGoal else {
            errorMessage = "Please select a goal"
            return
        }
        
        isSaving = true
        errorMessage = nil
        
        // TODO: Implement goal saving via ProfileManager or GoalManager
        // For now, just simulate saving
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        
        isGoalSaved = true
        isSaving = false
    }
}

#Preview {
    GoalSelectionView(onComplete: {}, allowSkip: true)
}

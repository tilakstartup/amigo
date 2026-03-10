import SwiftUI

// MARK: - Onboarding Page Types

enum OnboardingPage: Equatable {
    case welcome
    case goalSelection
    case question(QuestionType)
    case feature(FeatureType)
    case privacy
    case getStarted
}

enum QuestionType: Equatable {
    case weightTarget
    case targetWeight
    case activityLevel
    case sleepHours
    case stressLevel
    case dietaryPreferences
    case currentWeight
}

enum FeatureType: Equatable {
    case mealLogging
    case aiCoaching
    case fastingTracking
    case waterTracking
}

enum HealthGoal: String, CaseIterable {
    case weightLoss = "Weight Loss"
    case muscleGain = "Muscle Gain"
    case maintenance = "Maintenance"
    case improvedEnergy = "Improved Energy"
    case betterSleep = "Better Sleep"
    
    var icon: String {
        switch self {
        case .weightLoss: return "figure.walk"
        case .muscleGain: return "dumbbell.fill"
        case .maintenance: return "heart.fill"
        case .improvedEnergy: return "bolt.fill"
        case .betterSleep: return "moon.zzz.fill"
        }
    }
    
    var color: Color {
        switch self {
        case .weightLoss: return .red
        case .muscleGain: return .blue
        case .maintenance: return .green
        case .improvedEnergy: return .orange
        case .betterSleep: return .purple
        }
    }
    
    var description: String {
        switch self {
        case .weightLoss: return "Lose weight in a healthy, sustainable way"
        case .muscleGain: return "Build strength and increase muscle mass"
        case .maintenance: return "Maintain your current healthy lifestyle"
        case .improvedEnergy: return "Boost your energy and vitality"
        case .betterSleep: return "Improve your sleep quality and routine"
        }
    }
}

// MARK: - Onboarding ViewModel

class OnboardingViewModel: ObservableObject {
    @Published var selectedGoal: HealthGoal?
    @Published var pages: [OnboardingPage] = [.welcome, .goalSelection]
    @Published var answers: [QuestionType: String] = [:]
    
    func selectGoal(_ goal: HealthGoal) {
        selectedGoal = goal
        generateGoalSpecificPages(for: goal)
    }
    
    private func generateGoalSpecificPages(for goal: HealthGoal) {
        var newPages: [OnboardingPage] = [.welcome, .goalSelection]
        
        switch goal {
        case .weightLoss:
            newPages.append(.question(.targetWeight))
            newPages.append(.feature(.mealLogging))
            newPages.append(.question(.activityLevel))
            newPages.append(.feature(.aiCoaching))
            newPages.append(.question(.dietaryPreferences))
            newPages.append(.feature(.fastingTracking))
            
        case .muscleGain:
            newPages.append(.question(.targetWeight))
            newPages.append(.feature(.mealLogging))
            newPages.append(.question(.activityLevel))
            newPages.append(.feature(.aiCoaching))
            newPages.append(.question(.dietaryPreferences))
            
        case .maintenance:
            newPages.append(.question(.currentWeight))
            newPages.append(.feature(.mealLogging))
            newPages.append(.question(.activityLevel))
            newPages.append(.feature(.waterTracking))
            newPages.append(.question(.dietaryPreferences))
            
        case .improvedEnergy:
            newPages.append(.question(.sleepHours))
            newPages.append(.feature(.waterTracking))
            newPages.append(.question(.stressLevel))
            newPages.append(.feature(.aiCoaching))
            newPages.append(.question(.activityLevel))
            newPages.append(.feature(.mealLogging))
            
        case .betterSleep:
            newPages.append(.question(.sleepHours))
            newPages.append(.feature(.fastingTracking))
            newPages.append(.question(.stressLevel))
            newPages.append(.feature(.aiCoaching))
            newPages.append(.question(.activityLevel))
            newPages.append(.feature(.waterTracking))
        }
        
        newPages.append(.privacy)
        newPages.append(.getStarted)
        
        pages = newPages
    }
    
    func saveAnswer(for question: QuestionType, answer: String) {
        answers[question] = answer
    }
}

// MARK: - Main Welcome View

struct WelcomeView: View {
    @StateObject private var viewModel = OnboardingViewModel()
    @State private var currentPageIndex: Int = 0
    let onComplete: () -> Void
    
    var body: some View {
        TabView(selection: $currentPageIndex) {
            ForEach(Array(viewModel.pages.enumerated()), id: \.offset) { index, page in
                pageView(for: page, at: index)
                    .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .indexViewStyle(.page(backgroundDisplayMode: .always))
    }
    
    @ViewBuilder
    private func pageView(for page: OnboardingPage, at index: Int) -> some View {
        switch page {
        case .welcome:
            WelcomePageView(
                icon: "heart.fill",
                iconColor: .pink,
                title: "Welcome to Amigo",
                description: "Your AI-powered personal health coach. Let's start by understanding what you want to achieve.",
                showSkip: true,
                onSkip: onComplete
            )
            
        case .goalSelection:
            GoalSelectionPageView(
                viewModel: viewModel,
                currentPageIndex: $currentPageIndex,
                showSkip: true,
                onSkip: onComplete
            )
            
        case .question(let questionType):
            QuestionPageView(
                viewModel: viewModel,
                questionType: questionType,
                currentPageIndex: $currentPageIndex,
                showSkip: true,
                onSkip: onComplete
            )
            
        case .feature(let featureType):
            FeaturePageView(
                featureType: featureType,
                goal: viewModel.selectedGoal,
                showSkip: true,
                onSkip: onComplete
            )
            
        case .privacy:
            WelcomePageView(
                icon: "lock.shield.fill",
                iconColor: .green,
                title: "Your Data is Secure",
                description: "We take your privacy seriously. Your health data is encrypted and only accessible by you.",
                showSkip: false,
                onSkip: onComplete
            )
            
        case .getStarted:
            WelcomePageView(
                icon: "checkmark.circle.fill",
                iconColor: .pink,
                title: "You're All Set!",
                description: "Let's begin your health journey together. Amigo is here to support you every step of the way.",
                showSkip: false,
                isLastPage: true,
                onSkip: onComplete
            )
        }
    }
}

// MARK: - Welcome Page View

struct WelcomePageView: View {
    let icon: String
    let iconColor: Color
    let title: String
    let description: String
    let showSkip: Bool
    var isLastPage: Bool = false
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            Spacer()
            
            Image(systemName: icon)
                .font(.system(size: 80))
                .foregroundColor(iconColor)
            
            Text(title)
                .font(.title)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            Text(description)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Spacer()
            
            if isLastPage {
                Button(action: onSkip) {
                    Text("Get Started")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.pink)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .padding(.horizontal, 40)
            } else if showSkip {
                Button(action: onSkip) {
                    Text("Skip")
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
                .frame(height: 50)
        }
    }
}

// MARK: - Goal Selection Page View

struct GoalSelectionPageView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @Binding var currentPageIndex: Int
    let showSkip: Bool
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 10) {
                Image(systemName: "target")
                    .font(.system(size: 60))
                    .foregroundColor(.pink)
                
                Text("What's Your Goal?")
                    .font(.title)
                    .fontWeight(.bold)
                
                Text("Choose your primary health goal. You can change this anytime.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
            .padding(.top, 40)
            
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(HealthGoal.allCases, id: \.self) { goal in
                        GoalOptionButton(
                            goal: goal,
                            isSelected: viewModel.selectedGoal == goal,
                            action: {
                                viewModel.selectGoal(goal)
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                    withAnimation {
                                        currentPageIndex += 1
                                    }
                                }
                            }
                        )
                    }
                }
                .padding(.horizontal, 20)
            }
            
            Spacer()
            
            if showSkip {
                Button(action: onSkip) {
                    Text("Skip for now")
                        .foregroundColor(.secondary)
                }
                .padding(.bottom, 20)
            }
        }
    }
}

struct GoalOptionButton: View {
    let goal: HealthGoal
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 15) {
                Image(systemName: goal.icon)
                    .font(.system(size: 24))
                    .foregroundColor(isSelected ? .white : goal.color)
                    .frame(width: 50, height: 50)
                    .background(isSelected ? goal.color : goal.color.opacity(0.1))
                    .cornerRadius(10)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(goal.rawValue)
                        .font(.headline)
                        .foregroundColor(isSelected ? goal.color : .primary)
                    
                    Text(goal.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(goal.color)
                        .font(.system(size: 24))
                }
            }
            .padding()
            .background(isSelected ? goal.color.opacity(0.1) : Color(.systemGray6))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? goal.color : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Question Page View

struct QuestionPageView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    let questionType: QuestionType
    @Binding var currentPageIndex: Int
    @State private var answer: String = ""
    let showSkip: Bool
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            Spacer()
            
            Image(systemName: questionIcon)
                .font(.system(size: 60))
                .foregroundColor(viewModel.selectedGoal?.color ?? .pink)
            
            Text(questionTitle)
                .font(.title2)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Text(questionSubtitle)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            questionInput
                .padding(.horizontal, 40)
            
            Button(action: {
                viewModel.saveAnswer(for: questionType, answer: answer)
                withAnimation {
                    currentPageIndex += 1
                }
            }) {
                Text("Continue")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(answer.isEmpty ? Color.gray : (viewModel.selectedGoal?.color ?? .pink))
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .disabled(answer.isEmpty)
            .padding(.horizontal, 40)
            
            Spacer()
            
            if showSkip {
                Button(action: {
                    withAnimation {
                        currentPageIndex += 1
                    }
                }) {
                    Text("Skip")
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
                .frame(height: 50)
        }
    }
    
    private var questionIcon: String {
        switch questionType {
        case .weightTarget, .targetWeight, .currentWeight:
            return "scalemass.fill"
        case .activityLevel:
            return "figure.run"
        case .sleepHours:
            return "bed.double.fill"
        case .stressLevel:
            return "brain.head.profile"
        case .dietaryPreferences:
            return "leaf.fill"
        }
    }
    
    private var questionTitle: String {
        switch questionType {
        case .weightTarget:
            return "What's your target weight?"
        case .targetWeight:
            return "What's your target weight?"
        case .currentWeight:
            return "What's your current weight?"
        case .activityLevel:
            return "How active are you?"
        case .sleepHours:
            return "How many hours do you sleep?"
        case .stressLevel:
            return "How would you rate your stress level?"
        case .dietaryPreferences:
            return "Any dietary preferences?"
        }
    }
    
    private var questionSubtitle: String {
        switch questionType {
        case .weightTarget, .targetWeight:
            return "This helps us personalize your meal recommendations"
        case .currentWeight:
            return "We'll help you maintain a healthy lifestyle"
        case .activityLevel:
            return "Understanding your activity helps us tailor your nutrition"
        case .sleepHours:
            return "Sleep is crucial for your health goals"
        case .stressLevel:
            return "We'll help you manage stress through nutrition"
        case .dietaryPreferences:
            return "We'll respect your dietary choices"
        }
    }
    
    @ViewBuilder
    private var questionInput: some View {
        switch questionType {
        case .weightTarget, .targetWeight, .currentWeight:
            TextField("Enter weight (lbs)", text: $answer)
                .keyboardType(.decimalPad)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .multilineTextAlignment(.center)
                .font(.title3)
            
        case .sleepHours:
            TextField("Enter hours", text: $answer)
                .keyboardType(.decimalPad)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .multilineTextAlignment(.center)
                .font(.title3)
            
        case .activityLevel:
            VStack(spacing: 12) {
                ForEach(["Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extremely Active"], id: \.self) { level in
                    Button(action: {
                        answer = level
                    }) {
                        HStack {
                            Text(level)
                                .foregroundColor(answer == level ? .white : .primary)
                            Spacer()
                            if answer == level {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.white)
                            }
                        }
                        .padding()
                        .background(answer == level ? (viewModel.selectedGoal?.color ?? .pink) : Color(.systemGray6))
                        .cornerRadius(10)
                    }
                }
            }
            
        case .stressLevel:
            VStack(spacing: 12) {
                ForEach(["Low", "Moderate", "High"], id: \.self) { level in
                    Button(action: {
                        answer = level
                    }) {
                        HStack {
                            Text(level)
                                .foregroundColor(answer == level ? .white : .primary)
                            Spacer()
                            if answer == level {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.white)
                            }
                        }
                        .padding()
                        .background(answer == level ? (viewModel.selectedGoal?.color ?? .pink) : Color(.systemGray6))
                        .cornerRadius(10)
                    }
                }
            }
            
        case .dietaryPreferences:
            VStack(spacing: 12) {
                ForEach(["No Restrictions", "Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo"], id: \.self) { pref in
                    Button(action: {
                        answer = pref
                    }) {
                        HStack {
                            Text(pref)
                                .foregroundColor(answer == pref ? .white : .primary)
                            Spacer()
                            if answer == pref {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.white)
                            }
                        }
                        .padding()
                        .background(answer == pref ? (viewModel.selectedGoal?.color ?? .pink) : Color(.systemGray6))
                        .cornerRadius(10)
                    }
                }
            }
        }
    }
}

// MARK: - Feature Page View

struct FeaturePageView: View {
    let featureType: FeatureType
    let goal: HealthGoal?
    let showSkip: Bool
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            Spacer()
            
            Image(systemName: featureIcon)
                .font(.system(size: 80))
                .foregroundColor(featureColor)
            
            Text(featureTitle)
                .font(.title)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            Text(featureDescription)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Spacer()
            
            if showSkip {
                Button(action: onSkip) {
                    Text("Skip")
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
                .frame(height: 50)
        }
    }
    
    private var featureIcon: String {
        switch featureType {
        case .mealLogging: return "camera.fill"
        case .aiCoaching: return "brain.head.profile"
        case .fastingTracking: return "timer"
        case .waterTracking: return "drop.fill"
        }
    }
    
    private var featureColor: Color {
        switch featureType {
        case .mealLogging: return .blue
        case .aiCoaching: return .purple
        case .fastingTracking: return .orange
        case .waterTracking: return .cyan
        }
    }
    
    private var featureTitle: String {
        switch featureType {
        case .mealLogging: return "Track Every Meal"
        case .aiCoaching: return "Your AI Coach"
        case .fastingTracking: return "Fasting Made Easy"
        case .waterTracking: return "Stay Hydrated"
        }
    }
    
    private var featureDescription: String {
        let goalContext = goal != nil ? " to help you achieve your goal" : ""
        
        switch featureType {
        case .mealLogging:
            return "Simply snap a photo, speak, or type what you ate. Our AI analyzes your meals and provides detailed nutritional information\(goalContext)."
        case .aiCoaching:
            return "Get personalized advice from Amigo, your AI mentor who learns your habits and provides tailored guidance\(goalContext)."
        case .fastingTracking:
            return "Track intermittent fasting sessions with smart timers and get insights on how fasting supports\(goalContext)."
        case .waterTracking:
            return "Monitor your daily water intake with smart reminders to keep you properly hydrated\(goalContext)."
        }
    }
}

#Preview {
    WelcomeView(onComplete: {})
}

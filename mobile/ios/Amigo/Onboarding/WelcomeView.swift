import SwiftUI

struct WelcomeView: View {
    @Binding var currentPage: Int
    let onComplete: () -> Void
    
    var body: some View {
        TabView(selection: $currentPage) {
            // Page 1: Welcome
            WelcomePageView(
                icon: "heart.fill",
                iconColor: .pink,
                title: "Welcome to Amigo",
                description: "Your AI-powered personal health coach. Let's start by understanding what you want to achieve.",
                showSkip: true,
                onSkip: onComplete
            )
            .tag(0)
            
            // Page 2: Goal Selection (moved up for engagement)
            GoalSelectionPageView(
                currentPage: $currentPage,
                showSkip: true,
                onSkip: onComplete
            )
            .tag(1)
            
            // Page 3: Smart Meal Logging (feature to help achieve goal)
            WelcomePageView(
                icon: "camera.fill",
                iconColor: .blue,
                title: "Smart Meal Logging",
                description: "Log meals effortlessly with photos, voice, or text. Our AI analyzes your food and provides detailed nutritional information to help you reach your goal.",
                showSkip: true,
                onSkip: onComplete
            )
            .tag(2)
            
            // Page 4: AI Coaching (feature to help achieve goal)
            WelcomePageView(
                icon: "brain.head.profile",
                iconColor: .purple,
                title: "Personalized AI Coaching",
                description: "Get tailored advice from Amigo, your AI mentor who learns your habits and helps you make better choices toward your goal.",
                showSkip: true,
                onSkip: onComplete
            )
            .tag(3)
            
            // Page 5: Fasting & Water Tracking (feature to help achieve goal)
            WelcomePageView(
                icon: "timer",
                iconColor: .orange,
                title: "Fasting & Hydration",
                description: "Track intermittent fasting sessions and water intake with smart reminders to support your health journey.",
                showSkip: true,
                onSkip: onComplete
            )
            .tag(4)
            
            // Page 6: Privacy & Security
            WelcomePageView(
                icon: "lock.shield.fill",
                iconColor: .green,
                title: "Your Data is Secure",
                description: "We take your privacy seriously. Your health data is encrypted and only accessible by you.",
                showSkip: false,
                isLastPage: true,
                onSkip: onComplete
            )
            .tag(5)
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .indexViewStyle(.page(backgroundDisplayMode: .always))
    }
}

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
            
            // Icon
            Image(systemName: icon)
                .font(.system(size: 80))
                .foregroundColor(iconColor)
            
            // Title
            Text(title)
                .font(.title)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            // Description
            Text(description)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Spacer()
            
            // Action Button
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

struct GoalSelectionPageView: View {
    @Binding var currentPage: Int
    @State private var selectedGoal: String? = nil
    let showSkip: Bool
    let onSkip: () -> Void
    
    let goals = [
        ("figure.walk", "Weight Loss", "Lose weight in a healthy, sustainable way", Color.red),
        ("dumbbell.fill", "Muscle Gain", "Build strength and increase muscle mass", Color.blue),
        ("heart.fill", "Maintenance", "Maintain your current healthy lifestyle", Color.green),
        ("bolt.fill", "Improved Energy", "Boost your energy and vitality", Color.orange),
        ("moon.zzz.fill", "Better Sleep", "Improve your sleep quality and routine", Color.purple)
    ]
    
    var body: some View {
        VStack(spacing: 20) {
            // Header
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
            
            // Goal Options
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(goals, id: \.1) { goal in
                        GoalOptionButton(
                            icon: goal.0,
                            title: goal.1,
                            description: goal.2,
                            color: goal.3,
                            isSelected: selectedGoal == goal.1,
                            action: {
                                selectedGoal = goal.1
                                // Auto-advance to next page after selection
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                    withAnimation {
                                        currentPage += 1
                                    }
                                }
                            }
                        )
                    }
                }
                .padding(.horizontal, 20)
            }
            
            Spacer()
            
            // Skip Button
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
    let icon: String
    let title: String
    let description: String
    let color: Color
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 15) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(isSelected ? .white : color)
                    .frame(width: 50, height: 50)
                    .background(isSelected ? color : color.opacity(0.1))
                    .cornerRadius(10)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(isSelected ? color : .primary)
                    
                    Text(description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(color)
                        .font(.system(size: 24))
                }
            }
            .padding()
            .background(isSelected ? color.opacity(0.1) : Color(.systemGray6))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? color : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    WelcomeView(currentPage: .constant(0), onComplete: {})
}

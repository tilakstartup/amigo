import SwiftUI
import shared
import Charts

struct SmartGoalPlanningView: View {
    @StateObject private var viewModel: SmartGoalPlanningViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showManualPlanning = false
    @State private var showAIPlanning = false
    
    init(sessionManager: SessionManager, goalType: String) {
        _viewModel = StateObject(wrappedValue: SmartGoalPlanningViewModel(
            sessionManager: sessionManager,
            goalType: goalType
        ))
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 8) {
                        Text("Set Your Goal")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        
                        Text("Choose how you'd like to plan your journey")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top)
                    
                    // Options
                    VStack(spacing: 16) {
                        // Talk to Amigo Option
                        Button(action: {
                            showAIPlanning = true
                        }) {
                            PlanningOptionCard(
                                icon: "message.fill",
                                title: "Talk to Amigo",
                                description: "Let Amigo guide you through setting a realistic goal with personalized calculations",
                                color: .pink
                            )
                        }
                        .buttonStyle(.plain)
                        
                        // Manual Planning Option
                        Button(action: {
                            showManualPlanning = true
                        }) {
                            PlanningOptionCard(
                                icon: "slider.horizontal.3",
                                title: "Set Manually",
                                description: "Enter your target weight and date yourself with real-time calculations",
                                color: .blue
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showAIPlanning) {
                AIGoalPlanningView(
                    sessionManager: viewModel.sessionManager,
                    goalType: viewModel.goalType
                )
            }
            .sheet(isPresented: $showManualPlanning) {
                ManualGoalPlanningView(
                    sessionManager: viewModel.sessionManager,
                    goalType: viewModel.goalType
                )
            }
        }
    }
}

struct PlanningOptionCard: View {
    let icon: String
    let title: String
    let description: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundColor(color)
                .frame(width: 60, height: 60)
                .background(color.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(16)
    }
}

// AI-Guided Goal Planning View
struct AIGoalPlanningView: View {
    @StateObject private var viewModel: AIGoalPlanningViewModel
    @Environment(\.dismiss) private var dismiss
    
    init(sessionManager: SessionManager, goalType: String) {
        _viewModel = StateObject(wrappedValue: AIGoalPlanningViewModel(
            sessionManager: sessionManager,
            goalType: goalType
        ))
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Messages
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            ForEach(viewModel.messages) { message in
                                MessageBubble(message: message)
                                    .id(message.id)
                            }
                            
                            if viewModel.isTyping {
                                TypingIndicator()
                            }
                        }
                        .padding()
                    }
                    .onChange(of: viewModel.messages.count) { _ in
                        if let lastMessage = viewModel.messages.last {
                            withAnimation {
                                proxy.scrollTo(lastMessage.id, anchor: .bottom)
                            }
                        }
                    }
                }
                
                // Show plan if available
                if let plan = viewModel.currentPlan {
                    GoalPlanSummaryCard(plan: plan)
                        .padding()
                }
                
                // Input area
                if viewModel.state == .collecting || viewModel.state == .reviewingPlan {
                    VStack(spacing: 12) {
                        if viewModel.state == .reviewingPlan {
                            HStack(spacing: 12) {
                                Button("Adjust") {
                                    Task {
                                        await viewModel.requestAdjustment()
                                    }
                                }
                                .buttonStyle(.bordered)
                                
                                Button("Accept Plan") {
                                    Task {
                                        await viewModel.acceptPlan()
                                        dismiss()
                                    }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(.pink)
                            }
                            .padding(.horizontal)
                        }
                        
                        HStack(spacing: 12) {
                            TextField("Type your response...", text: $viewModel.userInput)
                                .textFieldStyle(.roundedBorder)
                                .disabled(viewModel.isTyping)
                            
                            Button(action: {
                                Task {
                                    await viewModel.sendMessage()
                                }
                            }) {
                                Image(systemName: "arrow.up.circle.fill")
                                    .font(.system(size: 32))
                                    .foregroundColor(viewModel.userInput.isEmpty ? .gray : .pink)
                            }
                            .disabled(viewModel.userInput.isEmpty || viewModel.isTyping)
                        }
                        .padding()
                        .background(Color(.systemGray6))
                    }
                }
            }
            .navigationTitle("Talk to Amigo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .task {
                await viewModel.startConversation()
            }
        }
    }
}

// Manual Goal Planning View
struct ManualGoalPlanningView: View {
    @StateObject private var viewModel: ManualGoalPlanningViewModel
    @Environment(\.dismiss) private var dismiss
    
    init(sessionManager: SessionManager, goalType: String) {
        _viewModel = StateObject(wrappedValue: ManualGoalPlanningViewModel(
            sessionManager: sessionManager,
            goalType: goalType
        ))
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Input Form
                    VStack(alignment: .leading, spacing: 20) {
                        Text("Goal Details")
                            .font(.headline)
                        
                        // Target Weight
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Target Weight (kg)")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            
                            TextField("Enter target weight", value: $viewModel.targetWeight, format: .number)
                                .textFieldStyle(.roundedBorder)
                                .keyboardType(.decimalPad)
                            
                            if let error = viewModel.validationErrors["targetWeight"] {
                                Text(error)
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                        }
                        
                        // Target Date
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Target Date")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            
                            DatePicker("", selection: $viewModel.targetDate, in: Date()..., displayedComponents: .date)
                                .datePickerStyle(.compact)
                            
                            if let error = viewModel.validationErrors["targetDate"] {
                                Text(error)
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                        }
                        
                        // Activity Level
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Activity Level")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            
                            Picker("Activity Level", selection: $viewModel.activityLevel) {
                                Text("Sedentary").tag("sedentary")
                                Text("Lightly Active").tag("light")
                                Text("Moderately Active").tag("moderate")
                                Text("Active").tag("active")
                                Text("Very Active").tag("very_active")
                            }
                            .pickerStyle(.menu)
                        }
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(16)
                    
                    // Calculations Display
                    if let plan = viewModel.calculatedPlan {
                        VStack(spacing: 16) {
                            Text("Your Plan")
                                .font(.headline)
                            
                            // Validation Warning
                            if !plan.validation.isRealistic {
                                ValidationWarningCard(validation: plan.validation)
                            }
                            
                            // Key Metrics
                            MetricsGrid(plan: plan)
                            
                            // Progress Chart
                            ProgressChartView(milestones: plan.milestones)
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(16)
                    }
                    
                    // Save Button
                    Button(action: {
                        Task {
                            await viewModel.savePlan()
                            dismiss()
                        }
                    }) {
                        Text("Save Goal")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(viewModel.canSave ? Color.pink : Color.gray)
                            .cornerRadius(12)
                    }
                    .disabled(!viewModel.canSave)
                }
                .padding()
            }
            .navigationTitle("Set Your Goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .task {
                await viewModel.initialize()
            }
        }
    }
}

// Supporting Views
struct MessageBubble: View {
    let message: GoalMessageWrapper
    
    var body: some View {
        HStack {
            if message.isUser {
                Spacer()
            }
            
            Text(message.content)
                .padding()
                .background(message.isUser ? Color.pink : Color(.systemGray5))
                .foregroundColor(message.isUser ? .white : .primary)
                .cornerRadius(16)
                .frame(maxWidth: .infinity * 0.75, alignment: message.isUser ? .trailing : .leading)
            
            if !message.isUser {
                Spacer()
            }
        }
    }
}

struct TypingIndicator: View {
    @State private var animating = false
    
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3) { index in
                Circle()
                    .fill(Color.gray)
                    .frame(width: 8, height: 8)
                    .scaleEffect(animating ? 1.0 : 0.5)
                    .animation(
                        Animation.easeInOut(duration: 0.6)
                            .repeatForever()
                            .delay(Double(index) * 0.2),
                        value: animating
                    )
            }
        }
        .padding()
        .background(Color(.systemGray5))
        .cornerRadius(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear {
            animating = true
        }
    }
}

struct GoalPlanSummaryCard: View {
    let plan: GoalPlanWrapper
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your Plan Summary")
                .font(.headline)
            
            HStack {
                MetricItem(label: "Daily Calories", value: "\(Int(plan.calculations.dailyCalories))")
                Spacer()
                MetricItem(label: "Weekly Rate", value: String(format: "%.1f kg", plan.calculations.weeklyWeightLossRate))
            }
            
            if !plan.validation.isRealistic {
                Text("⚠️ \(plan.validation.reason)")
                    .font(.caption)
                    .foregroundColor(.orange)
                    .padding(8)
                    .background(Color.orange.opacity(0.1))
                    .cornerRadius(8)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

struct ValidationWarningCard: View {
    let validation: GoalValidationResultWrapper
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
                Text("Goal Adjustment Needed")
                    .font(.headline)
            }
            
            Text(validation.reason)
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            if let recommendedDays = validation.recommendedTargetDays {
                Text("Recommended: \(recommendedDays) days")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.orange)
            }
        }
        .padding()
        .background(Color.orange.opacity(0.1))
        .cornerRadius(12)
    }
}

struct MetricsGrid: View {
    let plan: GoalPlanWrapper
    
    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
            MetricCard(title: "BMR", value: "\(Int(plan.calculations.bmr))", unit: "cal/day")
            MetricCard(title: "TDEE", value: "\(Int(plan.calculations.tdee))", unit: "cal/day")
            MetricCard(title: "Daily Calories", value: "\(Int(plan.calculations.dailyCalories))", unit: "cal")
            MetricCard(title: "Weekly Rate", value: String(format: "%.1f", plan.calculations.weeklyWeightLossRate), unit: "kg/week")
        }
    }
}

struct MetricCard: View {
    let title: String
    let value: String
    let unit: String
    
    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
            Text(unit)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 4)
    }
}

struct MetricItem: View {
    let label: String
    let value: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.headline)
        }
    }
}

struct ProgressChartView: View {
    let milestones: [WeeklyMilestoneWrapper]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Progress Projection")
                .font(.headline)
            
            if #available(iOS 16.0, *) {
                Chart {
                    ForEach(milestones) { milestone in
                        LineMark(
                            x: .value("Week", milestone.week),
                            y: .value("Weight", milestone.projectedWeightKg)
                        )
                        .foregroundStyle(.pink)
                    }
                }
                .frame(height: 200)
                .chartXAxis {
                    AxisMarks(values: .automatic) { _ in
                        AxisGridLine()
                        AxisValueLabel()
                    }
                }
                .chartYAxis {
                    AxisMarks { _ in
                        AxisGridLine()
                        AxisValueLabel()
                    }
                }
            } else {
                // Fallback for iOS 15
                Text("Chart requires iOS 16+")
                    .foregroundColor(.secondary)
                    .frame(height: 200)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
    }
}

// ViewModels
@MainActor
class SmartGoalPlanningViewModel: ObservableObject {
    let sessionManager: SessionManager
    let goalType: String
    
    init(sessionManager: SessionManager, goalType: String) {
        self.sessionManager = sessionManager
        self.goalType = goalType
    }
}

@MainActor
class AIGoalPlanningViewModel: ObservableObject {
    @Published var messages: [GoalMessageWrapper] = []
    @Published var userInput = ""
    @Published var isTyping = false
    @Published var state: GoalPlanningStateType = .initial
    @Published var currentPlan: GoalPlanWrapper?
    
    let sessionManager: SessionManager
    let goalType: String
    private var engine: GoalPlanningConversationEngine?
    
    init(sessionManager: SessionManager, goalType: String) {
        self.sessionManager = sessionManager
        self.goalType = goalType
    }
    
    func startConversation() async {
        // Initialize engine and start conversation
        // Implementation would use the KMP GoalPlanningConversationEngine
    }
    
    func sendMessage() async {
        guard !userInput.isEmpty else { return }
        let message = userInput
        userInput = ""
        
        // Send to engine
        // Implementation would call engine.processUserResponse()
    }
    
    func requestAdjustment() async {
        // Request plan adjustment
    }
    
    func acceptPlan() async {
        // Accept and save the plan
    }
}

@MainActor
class ManualGoalPlanningViewModel: ObservableObject {
    @Published var targetWeight: Double?
    @Published var targetDate = Date().addingTimeInterval(90 * 24 * 60 * 60) // 90 days from now
    @Published var activityLevel = "moderate"
    @Published var calculatedPlan: GoalPlanWrapper?
    @Published var validationErrors: [String: String] = [:]
    @Published var canSave = false
    
    let sessionManager: SessionManager
    let goalType: String
    private var manager: ManualGoalPlanningManager?
    
    init(sessionManager: SessionManager, goalType: String) {
        self.sessionManager = sessionManager
        self.goalType = goalType
    }
    
    func initialize() async {
        // Initialize with user profile
        // Implementation would use ManualGoalPlanningManager
    }
    
    func savePlan() async {
        // Save the goal plan
    }
}

// Wrapper types for Swift interop
struct GoalMessageWrapper: Identifiable {
    let id = UUID()
    let content: String
    let isUser: Bool
    let timestamp: Int64
}

struct GoalPlanWrapper {
    let goalType: String
    let currentMetrics: CurrentMetricsWrapper
    let targetMetrics: TargetMetricsWrapper
    let calculations: GoalCalculationsWrapper
    let validation: GoalValidationResultWrapper
    let milestones: [WeeklyMilestoneWrapper]
}

struct CurrentMetricsWrapper {
    let weightKg: Double
    let heightCm: Double
    let age: Int32
    let gender: String
    let activityLevel: String
    let bmi: Double
}

struct TargetMetricsWrapper {
    let weightKg: Double
    let targetDate: String
    let targetDays: Int32
    let targetBMI: Double
}

struct GoalCalculationsWrapper {
    let bmr: Double
    let tdee: Double
    let dailyCalories: Double
    let weeklyWeightLossRate: Double
    let totalWeightToLose: Double
    let calorieDeficitPerDay: Double
}

struct GoalValidationResultWrapper {
    let isRealistic: Bool
    let reason: String
    let recommendedTargetDays: Int32?
    let calculatedDailyCalories: Double?
    let weeklyWeightLossRate: Double?
}

struct WeeklyMilestoneWrapper: Identifiable {
    let id = UUID()
    let week: Int32
    let projectedWeightKg: Double
    let cumulativeWeightLossKg: Double
    let percentComplete: Double
}

enum GoalPlanningStateType {
    case initial
    case collecting
    case reviewingPlan
    case completed
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    return SmartGoalPlanningView(sessionManager: sessionManager, goalType: "weight_loss")
}

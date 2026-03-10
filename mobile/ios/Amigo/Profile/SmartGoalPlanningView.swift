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
    let sessionManager: SessionManager
    let goalType: String
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        let config = ChatSessionConfig(
            cap: "goal_setting",
            responsibilities: [
                "Get user profile to retrieve current weight, height, age, gender, activity_level",
                "Confirm or ask for goal type (weight_loss, muscle_gain, or maintenance)",
                "Ask for target weight in kg",
                "Ask for target date in yyyy-MM-dd format",
                "Calculate BMR using calculate_bmr(weight, height, age, gender)",
                "Calculate TDEE using calculate_tdee(weight, height, age, gender, activity_level)",
                "Calculate daily calories needed: For weight loss = TDEE - (weight_difference * 7700 / days_until_target)",
                "Validate goal is realistic using validate_goal(goal_type, daily_calories)",
                "Present summary with: current weight, target weight, target date, BMR, TDEE, daily calories, weekly weight change rate",
                "Ask user to confirm the goal",
                "When confirmed, call save_goal(goal_type, current_weight, target_weight, target_date, bmr, tdee, daily_calories)",
                "Set status to completed after successful save"
            ],
            collectData: [
                "current_weight",
                "target_weight",
                "target_date",
                "goal_type"
            ],
            collectMetrics: [
                "bmr",
                "tdee",
                "daily_calories",
                "weekly_weight_change"
            ],
            initialMessage: "I'd like to set a \(goalType) goal"
        )
        
        AgentConversationView(
            sessionManager: sessionManager,
            chatConfig: config,
            onComplete: { _ in
                dismiss()
            }
        )
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
            VStack(spacing: 0) {
                TabView(selection: $viewModel.currentStep) {
                    goalTypeStep.tag(0)
                    detailsStep.tag(1)
                    validationStep.tag(2)
                    projectionStep.tag(3)
                }
                .tabViewStyle(.page(indexDisplayMode: .always))

                VStack(spacing: 8) {
                    if let error = viewModel.validationError {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    }

                    HStack(spacing: 12) {
                        Button("Back") {
                            viewModel.previousStep()
                        }
                        .buttonStyle(.bordered)
                        .disabled(viewModel.currentStep == 0 || viewModel.isSaving)

                        Button(viewModel.currentStep == 3 ? "Save Goal" : "Next") {
                            Task {
                                if viewModel.currentStep == 3 {
                                    await viewModel.savePlan()
                                    if viewModel.saveSucceeded {
                                        dismiss()
                                    }
                                } else {
                                    await viewModel.nextStep()
                                }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.pink)
                        .disabled(!viewModel.canProceed || viewModel.isSaving)
                    }
                }
                .padding()
                .background(Color(.systemBackground))
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

    private var goalTypeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Step 1 of 4")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("Choose your health goal")
                    .font(.title3)
                    .fontWeight(.semibold)

                ForEach(ManualGoalType.allCases, id: \.self) { goal in
                    Button(action: { viewModel.selectedGoal = goal }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(goal.title)
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                Text(goal.subtitle)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            if viewModel.selectedGoal == goal {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.pink)
                            }
                        }
                        .padding()
                        .background(viewModel.selectedGoal == goal ? Color.pink.opacity(0.08) : Color(.systemGray6))
                        .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
    }

    private var detailsStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Step 2 of 4")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("Enter your details")
                    .font(.title3)
                    .fontWeight(.semibold)

                if viewModel.requiresWeightInputs {
                    numericField("Current Weight (kg)", value: $viewModel.currentWeightKg)
                    numericField("Target Weight (kg)", value: $viewModel.targetWeightKg)
                }

                numericField("Height (cm)", value: $viewModel.heightCm)
                numericField("Age", value: $viewModel.age)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Gender")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Picker("Gender", selection: $viewModel.gender) {
                        Text("Male").tag("male")
                        Text("Female").tag("female")
                    }
                    .pickerStyle(.segmented)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Activity Level")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Picker("Activity Level", selection: $viewModel.activityLevel) {
                        Text("Sedentary").tag("sedentary")
                        Text("Lightly Active").tag("lightly_active")
                        Text("Moderately Active").tag("moderately_active")
                        Text("Very Active").tag("very_active")
                        Text("Extremely Active").tag("extremely_active")
                    }
                    .pickerStyle(.menu)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Target Date")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    DatePicker("", selection: $viewModel.targetDate, in: Date()..., displayedComponents: .date)
                        .datePickerStyle(.compact)
                }
            }
            .padding()
        }
    }

    private var validationStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Step 3 of 4")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("Validate your plan")
                    .font(.title3)
                    .fontWeight(.semibold)

                if let plan = viewModel.calculatedPlan {
                    MetricsGrid(plan: plan)

                    if !plan.validation.isRealistic {
                        ValidationWarningCard(validation: plan.validation)

                        VStack(spacing: 12) {
                            Text("Suggestions to meet safe guidelines:")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            
                            if let suggested = viewModel.suggestedTargetDate {
                                Button {
                                    Task {
                                        await viewModel.applySuggestedDate()
                                    }
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("Extend Timeline")
                                                .font(.subheadline)
                                                .fontWeight(.semibold)
                                            Text("Target date: \(viewModel.displayDate(suggested))")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                        Spacer()
                                        Image(systemName: "calendar")
                                    }
                                    .padding()
                                    .background(Color.orange.opacity(0.1))
                                    .cornerRadius(10)
                                }
                                .buttonStyle(.plain)
                            }
                            
                            if let suggestedWeight = viewModel.calculateSuggestedTargetWeight() {
                                Button {
                                    Task {
                                        await viewModel.applySuggestedTargetWeight(suggestedWeight)
                                    }
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("Adjust Target Weight")
                                                .font(.subheadline)
                                                .fontWeight(.semibold)
                                            Text("Target weight: \(String(format: "%.1f", suggestedWeight)) kg")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                        Spacer()
                                        Image(systemName: "scalemass")
                                    }
                                    .padding()
                                    .background(Color.orange.opacity(0.1))
                                    .cornerRadius(10)
                                }
                                .buttonStyle(.plain)
                            }
                            
                            Text("Or proceed anyway (not recommended)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .padding(.top, 4)
                        }
                    }
                }
            }
            .padding()
        }
    }

    private var projectionStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Step 4 of 4")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("Review projection")
                    .font(.title3)
                    .fontWeight(.semibold)

                if let plan = viewModel.calculatedPlan {
                    ProgressChartView(milestones: plan.milestones)
                    GoalPlanSummaryCard(plan: plan)
                }

                if let saveError = viewModel.saveError {
                    Text(saveError)
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
            .padding()
        }
    }

    private func numericField(_ title: String, value: Binding<Double?>) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline)
                .foregroundColor(.secondary)
            TextField(title, value: value, format: .number)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.decimalPad)
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
    
    private var halfwayWeek: Int32 {
        guard milestones.count > 1 else { return 0 }
        return milestones[milestones.count / 2].week
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Progress Projection")
                .font(.headline)
            
            if #available(iOS 16.0, *) {
                Chart {
                    // Main progress line
                    ForEach(milestones) { milestone in
                        LineMark(
                            x: .value("Week", milestone.week),
                            y: .value("Weight", milestone.projectedWeightKg)
                        )
                        .foregroundStyle(.pink)
                        .lineStyle(StrokeStyle(lineWidth: 2))
                    }
                    
                    // Weekly checkpoint markers
                    ForEach(milestones) { milestone in
                        PointMark(
                            x: .value("Week", milestone.week),
                            y: .value("Weight", milestone.projectedWeightKg)
                        )
                        .foregroundStyle(.pink)
                        .symbolSize(50)
                    }
                    
                    // Halfway milestone - vertical dashed line
                    if milestones.count > 1 {
                        RuleMark(x: .value("Halfway", halfwayWeek))
                            .foregroundStyle(.green)
                            .lineStyle(StrokeStyle(lineWidth: 2, dash: [5, 5]))
                            .annotation(position: .top, alignment: .center) {
                                Text("Halfway")
                                    .font(.caption2)
                                    .foregroundColor(.green)
                                    .padding(4)
                                    .background(Color.white.opacity(0.8))
                                    .cornerRadius(4)
                            }
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
        do {
            guard let plan = currentPlan else {
                NSLog("❌ [AIGoalPlanning] acceptPlan called but currentPlan is nil")
                return
            }
            
            NSLog("🔵 [AIGoalPlanning] acceptPlan started")
            
            // Get user ID - try multiple approaches
            var userId: String? = nil
            
            // Approach 1: Try getCurrentUser
            if let user = sessionManager.getCurrentUser(), !user.id.isEmpty {
                userId = user.id
                NSLog("✅ [AIGoalPlanning] Got user from getCurrentUser: %@", userId!)
            }
            
            // Approach 2: Extract from JWT access token
            if userId == nil {
                NSLog("⚠️ [AIGoalPlanning] getCurrentUser failed, trying JWT token...")
                do {
                    if let accessToken = try await sessionManager.getAccessToken(), !accessToken.isEmpty {
                        NSLog("✅ [AIGoalPlanning] Got access token, extracting user ID...")
                        userId = extractUserIdFromToken(accessToken)
                        if let extractedId = userId {
                            NSLog("✅ [AIGoalPlanning] Extracted user ID from JWT: %@", extractedId)
                        } else {
                            NSLog("❌ [AIGoalPlanning] Failed to extract user ID from JWT")
                        }
                    } else {
                        NSLog("❌ [AIGoalPlanning] No access token available")
                    }
                } catch {
                    NSLog("❌ [AIGoalPlanning] Error getting access token: %@", error.localizedDescription)
                }
            }
            
            guard let finalUserId = userId else {
                NSLog("❌ [AIGoalPlanning] No user found")
                return
            }
            
            // Ensure Supabase session is synced before database operation
            NSLog("🔄 [AIGoalPlanning] Ensuring Supabase session is synced...")
            let sessionSynced = try await sessionManager.ensureSessionSynced()
            if sessionSynced.boolValue == false {
                NSLog("❌ [AIGoalPlanning] Failed to sync Supabase session")
                return
            }
            NSLog("✅ [AIGoalPlanning] Supabase session synced successfully")
        
        let profileManager = ProfileManagerFactory.shared.create()
        
        // Prepare milestone data
        let milestonePayload = plan.milestones.map { milestone in
            [
                "week": Int(milestone.week),
                "projected_weight_kg": milestone.projectedWeightKg,
                "percent_complete": milestone.percentComplete
            ] as [String: Any]
        }
        
        // Convert goal type string to GoalType enum
        let goalTypeEnum: GoalType
        switch plan.goalType.lowercased() {
        case "weight_loss":
            goalTypeEnum = .weightLoss
        case "muscle_gain":
            goalTypeEnum = .muscleGain
        case "improved_energy":
            goalTypeEnum = .improvedEnergy
        case "maintenance":
            goalTypeEnum = .maintenance
        default:
            goalTypeEnum = .weightLoss
        }
        
        // Use the shared ProfileManager.updateGoal method
        do {
            _ = try await profileManager.updateGoal(
                userId: finalUserId,
                goalType: goalTypeEnum,
                targetWeightKg: KotlinDouble(value: plan.targetMetrics.weightKg),
                targetDate: plan.targetMetrics.targetDate,
                currentWeightKg: KotlinDouble(value: plan.currentMetrics.weightKg),
                currentHeightCm: KotlinDouble(value: plan.currentMetrics.heightCm),
                activityLevel: plan.currentMetrics.activityLevel,
                calculatedBmr: KotlinDouble(value: plan.calculations.bmr),
                calculatedTdee: KotlinDouble(value: plan.calculations.tdee),
                calculatedDailyCalories: KotlinDouble(value: plan.calculations.dailyCalories),
                calculatedBmiStart: KotlinDouble(value: plan.currentMetrics.bmi),
                calculatedBmiTarget: KotlinDouble(value: plan.targetMetrics.targetBMI),
                weeklyMilestones: milestonePayload,
                isRealistic: KotlinBoolean(value: plan.validation.isRealistic),
                recommendedTargetDate: nil,
                validationReason: plan.validation.reason,
                goalContext: nil
            )
            NSLog("🎉 [AIGoalPlanning] Plan accepted and saved successfully!")
        } catch {
            NSLog("❌ [AIGoalPlanning] Error saving plan: %@", error.localizedDescription)
        }
        } catch {
            NSLog("❌ [AIGoalPlanning] Error in acceptPlan: %@", error.localizedDescription)
        }
    }
    
    private func extractUserIdFromToken(_ token: String) -> String? {
        // Split JWT token into parts (header.payload.signature)
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else {
            NSLog("❌ [AIGoalPlanning] Invalid JWT format - expected 3 parts, got %d", parts.count)
            return nil
        }
        
        // Decode the payload (second part)
        var payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        
        // Add padding if needed
        let remainder = payload.count % 4
        if remainder > 0 {
            payload += String(repeating: "=", count: 4 - remainder)
        }
        
        // Decode base64
        guard let data = Data(base64Encoded: payload) else {
            NSLog("❌ [AIGoalPlanning] Failed to decode base64 payload")
            return nil
        }
        
        // Parse JSON
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            NSLog("❌ [AIGoalPlanning] Failed to parse JWT payload JSON")
            return nil
        }
        
        // Extract "sub" claim (user ID)
        guard let sub = json["sub"] as? String, !sub.isEmpty else {
            NSLog("❌ [AIGoalPlanning] No 'sub' claim in JWT payload")
            return nil
        }
        
        return sub
    }
}

@MainActor
class ManualGoalPlanningViewModel: ObservableObject {
    @Published var currentStep = 0
    @Published var selectedGoal: ManualGoalType = .weightLoss
    @Published var currentWeightKg: Double?
    @Published var targetWeightKg: Double?
    @Published var targetDate = Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date()
    @Published var activityLevel = "moderately_active"
    @Published var gender = "male"
    @Published var age: Double?
    @Published var heightCm: Double?
    @Published var calculatedPlan: GoalPlanWrapper?
    @Published var validationError: String?
    @Published var suggestedTargetDate: Date?
    @Published var isSaving = false
    @Published var saveError: String?
    @Published var saveSucceeded = false
    
    let sessionManager: SessionManager
    let goalType: String
    private let profileManager: ProfileManager
    
    init(sessionManager: SessionManager, goalType: String) {
        self.sessionManager = sessionManager
        self.goalType = goalType
        self.profileManager = ProfileManagerFactory.shared.create()

        if let mapped = ManualGoalType.from(goalType: goalType) {
            self.selectedGoal = mapped
        }
    }

    var requiresWeightInputs: Bool {
        selectedGoal == .weightLoss || selectedGoal == .weightGain
    }

    var canProceed: Bool {
        switch currentStep {
        case 0:
            return true
        case 1:
            return detailsAreValid()
        case 2:
            // Allow proceeding even if not realistic (user can choose to ignore warning)
            return calculatedPlan != nil
        case 3:
            return calculatedPlan != nil && !isSaving
        default:
            return false
        }
    }
    
    func initialize() async {
        NSLog("🔵 [ManualGoalPlanning] initialize() called")
        // Check what's in storage BEFORE initialize
        let userDefaults = UserDefaults.standard
        let sessionKey = "amigo_secure_amigo_session"
        let accessTokenKey = "amigo_secure_amigo_access_token"
        let sessionInStorage = userDefaults.string(forKey: sessionKey)
        let tokenInStorage = userDefaults.string(forKey: accessTokenKey)
        NSLog("🔍 [ManualGoalPlanning] BEFORE initialize - Storage check:")
        NSLog("   Session in storage: %@", sessionInStorage != nil ? "YES (length: \(sessionInStorage!.count))" : "NO")
        NSLog("   Token in storage: %@", tokenInStorage != nil ? "YES (length: \(tokenInStorage!.count))" : "NO")
        if let sessionJson = sessionInStorage {
            NSLog("📝 [ManualGoalPlanning] Session JSON (truncated): %@", String(sessionJson.prefix(500)))
        } else {
            NSLog("📝 [ManualGoalPlanning] No session JSON found in storage")
        }
        // Check if sessionManager already has a user BEFORE calling initialize
        var userBeforeInit = sessionManager.getCurrentUser()
        NSLog("🔍 [ManualGoalPlanning] User BEFORE initialize: id=%@, email=%@", 
              userBeforeInit?.id ?? "nil", userBeforeInit?.email ?? "nil")
        
        // Ensure session is initialized
        do {
            try await sessionManager.initialize()
            NSLog("✅ [ManualGoalPlanning] Session initialized in initialize()")
        } catch {
            NSLog("❌ [ManualGoalPlanning] Failed to initialize session in initialize(): %@", error.localizedDescription)
        }
        
        let currentUser = sessionManager.getCurrentUser()
        NSLog("🔍 [ManualGoalPlanning] initialize() - Current user: id=%@, email=%@", 
              currentUser?.id ?? "nil", currentUser?.email ?? "nil")
        
        guard let userId = currentUser?.id, !userId.isEmpty else {
            NSLog("⚠️ [ManualGoalPlanning] initialize() - No user found, cannot load profile")
            return
        }
        guard let profile = try? await profileManager.getProfileOrNull(userId: userId) else {
            NSLog("⚠️ [ManualGoalPlanning] initialize() - Failed to load profile for user: %@", userId)
            return
        }

        NSLog("✅ [ManualGoalPlanning] initialize() - Profile loaded for user: %@", userId)
        if let weight = profile.weightKg?.doubleValue {
            currentWeightKg = weight
        }
        if let height = profile.heightCm?.doubleValue {
            heightCm = height
        }
        if let profileAge = profile.age?.int32Value {
            age = Double(profileAge)
        }
        if let level = profile.activityLevel {
            activityLevel = String(describing: level)
                .lowercased()
                .replacingOccurrences(of: "_", with: "_")
        }
        if let profileGoal = profile.goalType {
            let raw = String(describing: profileGoal).lowercased()
            if raw.contains("muscle") {
                selectedGoal = .weightGain
            } else if raw.contains("maint") {
                selectedGoal = .maintenance
            } else if raw.contains("improved") {
                selectedGoal = .fitness
            } else {
                selectedGoal = .weightLoss
            }
        }
    }
    
    func nextStep() async {
        validationError = nil
        if currentStep == 1 || currentStep == 2 {
            computePlan()
        }
        guard canProceed else {
            if currentStep == 1 {
                validationError = "Please complete all required fields with valid values."
            }
            return
        }
        currentStep = min(currentStep + 1, 3)
    }

    func previousStep() {
        validationError = nil
        currentStep = max(currentStep - 1, 0)
    }

    func applySuggestedDate() async {
        guard let suggested = suggestedTargetDate else { return }
        targetDate = suggested
        computePlan()
    }
    
    func calculateSuggestedTargetWeight() -> Double? {
        guard let current = currentWeightKg else { return nil }
        guard requiresWeightInputs else { return nil }
        
        let today = Calendar.current.startOfDay(for: Date())
        let endDate = Calendar.current.startOfDay(for: targetDate)
        let days = max(1, Calendar.current.dateComponents([.day], from: today, to: endDate).day ?? 1)
        let weeks = Double(days) / 7.0
        
        // Calculate max safe weight change based on timeline
        let maxWeeklyChange = selectedGoal == .weightLoss ? 1.0 : 0.5 // kg per week
        let maxTotalChange = maxWeeklyChange * weeks
        
        if selectedGoal == .weightLoss {
            return max(current - maxTotalChange, 50.0) // minimum 50kg
        } else if selectedGoal == .weightGain {
            return current + maxTotalChange
        }
        return nil
    }
    
    func applySuggestedTargetWeight(_ weight: Double) async {
        targetWeightKg = weight
        computePlan()
    }

    func savePlan() async {
        guard let plan = calculatedPlan else {
            NSLog("❌ [ManualGoalPlanning] savePlan called but calculatedPlan is nil")
            return
        }
        
        NSLog("🔵 [ManualGoalPlanning] savePlan started")
        isSaving = true
        saveError = nil
        
        // Prepare milestone data first
        let milestonePayload = plan.milestones.map { milestone in
            [
                "week": Int(milestone.week),
                "projected_weight_kg": milestone.projectedWeightKg,
                "percent_complete": milestone.percentComplete
            ] as [String: Any]
        }

        // Try to save directly - let ProfileManager handle the session
        // ProfileManager uses the Supabase client which should have the session
        do {
            NSLog("🔄 [ManualGoalPlanning] Attempting to get user from session...")
            
            // Get user ID - try multiple approaches
            var userId: String? = nil
            
            // Approach 1: Try getCurrentUser
            if let user = sessionManager.getCurrentUser(), !user.id.isEmpty {
                userId = user.id
                NSLog("✅ [ManualGoalPlanning] Got user from getCurrentUser: %@", userId!)
            }
            
            // Approach 2: Extract from JWT access token
            if userId == nil {
                NSLog("⚠️ [ManualGoalPlanning] getCurrentUser failed, trying JWT token...")
                if let accessToken = try await sessionManager.getAccessToken(), !accessToken.isEmpty {
                    NSLog("✅ [ManualGoalPlanning] Got access token, extracting user ID...")
                    userId = extractUserIdFromToken(accessToken)
                    if let extractedId = userId {
                        NSLog("✅ [ManualGoalPlanning] Extracted user ID from JWT: %@", extractedId)
                    } else {
                        NSLog("❌ [ManualGoalPlanning] Failed to extract user ID from JWT")
                    }
                } else {
                    NSLog("❌ [ManualGoalPlanning] No access token available")
                }
            }
            
            // If still no user, fail
            guard let finalUserId = userId else {
                NSLog("❌ [ManualGoalPlanning] Could not get user ID")
                saveError = "Your session has expired. Please log out and log in again to continue."
                isSaving = false
                return
            }
            
            // Ensure Supabase session is synced before database operation
            NSLog("🔄 [ManualGoalPlanning] Ensuring Supabase session is synced...")
            let sessionSynced = try await sessionManager.ensureSessionSynced()
            if sessionSynced.boolValue == false {
                NSLog("❌ [ManualGoalPlanning] Failed to sync Supabase session")
                saveError = "Failed to sync session. Please try again."
                isSaving = false
                return
            }
            NSLog("✅ [ManualGoalPlanning] Supabase session synced successfully")
            
            NSLog("🔵 [ManualGoalPlanning] Calling ProfileManager.updateGoal for user: %@", finalUserId)
            _ = try await profileManager.updateGoal(
                userId: finalUserId,
                goalType: sharedGoalType(),
                targetWeightKg: KotlinDouble(value: plan.targetMetrics.weightKg),
                targetDate: isoDateOnly(targetDate),
                currentWeightKg: KotlinDouble(value: plan.currentMetrics.weightKg),
                currentHeightCm: KotlinDouble(value: plan.currentMetrics.heightCm),
                activityLevel: plan.currentMetrics.activityLevel,
                calculatedBmr: KotlinDouble(value: plan.calculations.bmr),
                calculatedTdee: KotlinDouble(value: plan.calculations.tdee),
                calculatedDailyCalories: KotlinDouble(value: plan.calculations.dailyCalories),
                calculatedBmiStart: KotlinDouble(value: plan.currentMetrics.bmi),
                calculatedBmiTarget: KotlinDouble(value: plan.targetMetrics.targetBMI),
                weeklyMilestones: milestonePayload,
                isRealistic: KotlinBoolean(value: plan.validation.isRealistic),
                recommendedTargetDate: nil,
                validationReason: plan.validation.reason,
                goalContext: nil
            )
            
            NSLog("🎉 [ManualGoalPlanning] Health goal saved successfully!")
            saveSucceeded = true
        } catch let error as NSError {
            NSLog("❌ [ManualGoalPlanning] Failed to save - Error domain: %@, code: %ld, description: %@", 
                  error.domain, error.code, error.localizedDescription)
            saveError = "Failed to save goal: \(error.localizedDescription)"
        } catch {
            NSLog("❌ [ManualGoalPlanning] Failed to save: %@", error.localizedDescription)
            saveError = "Failed to save goal: \(error.localizedDescription)"
        }

        isSaving = false
    }
    
    private func extractUserIdFromToken(_ token: String) -> String? {
        // Split JWT token into parts (header.payload.signature)
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else {
            NSLog("❌ [ManualGoalPlanning] Invalid JWT format - expected 3 parts, got %d", parts.count)
            return nil
        }
        
        // Decode the payload (second part)
        var payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        
        // Add padding if needed
        let remainder = payload.count % 4
        if remainder > 0 {
            payload += String(repeating: "=", count: 4 - remainder)
        }
        
        // Decode base64
        guard let data = Data(base64Encoded: payload) else {
            NSLog("❌ [ManualGoalPlanning] Failed to decode base64 payload")
            return nil
        }
        
        // Parse JSON
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            NSLog("❌ [ManualGoalPlanning] Failed to parse JWT payload JSON")
            return nil
        }
        
        // Extract "sub" claim (user ID)
        guard let sub = json["sub"] as? String, !sub.isEmpty else {
            NSLog("❌ [ManualGoalPlanning] No 'sub' claim in JWT payload")
            return nil
        }
        
        return sub
    }

    private func detailsAreValid() -> Bool {
        if age == nil || (age ?? 0) < 13 { return false }
        if heightCm == nil || (heightCm ?? 0) < 100 { return false }
        if requiresWeightInputs {
            guard let current = currentWeightKg, let target = targetWeightKg else { return false }
            if current <= 0 || target <= 0 { return false }
            if selectedGoal == .weightLoss && target >= current { return false }
            if selectedGoal == .weightGain && target <= current { return false }
        }
        return true
    }

    private func computePlan() {
        guard detailsAreValid() else {
            calculatedPlan = nil
            return
        }

        let today = Calendar.current.startOfDay(for: Date())
        let endDate = Calendar.current.startOfDay(for: targetDate)
        let days = max(1, Calendar.current.dateComponents([.day], from: today, to: endDate).day ?? 1)

        let weight = currentWeightKg ?? 70
        let height = heightCm ?? 170
        let personAge = age ?? 30

        let bmrBase = 10.0 * weight + 6.25 * height - 5.0 * personAge
        let bmr = gender == "male" ? (bmrBase + 5.0) : (bmrBase - 161.0)
        let activityMultiplier: Double = {
            switch activityLevel {
            case "sedentary": return 1.2
            case "lightly_active": return 1.375
            case "moderately_active": return 1.55
            case "very_active": return 1.725
            case "extremely_active": return 1.9
            default: return 1.55
            }
        }()
        let tdee = bmr * activityMultiplier

        var dailyCalories = tdee
        var weeklyRate = 0.0
        var totalWeightDelta = 0.0

        if requiresWeightInputs {
            let target = targetWeightKg ?? weight
            totalWeightDelta = selectedGoal == .weightLoss ? (weight - target) : (target - weight)
            let kcalDeltaPerDay = (totalWeightDelta * 7700.0) / Double(days)
            if selectedGoal == .weightLoss {
                dailyCalories = tdee - kcalDeltaPerDay
            } else {
                dailyCalories = tdee + kcalDeltaPerDay
            }
            weeklyRate = totalWeightDelta / (Double(days) / 7.0)
        }

        let minCalories = gender == "male" ? 1500.0 : 1200.0
        var isRealistic = dailyCalories >= minCalories
        var reason = ""
        var recommendedDays: Int32? = nil
        var recommendedDate: Date? = nil

        if !isRealistic && requiresWeightInputs {
            let target = targetWeightKg ?? weight
            let delta = abs(weight - target)
            let safeDailyChangeBudget = max(50.0, abs(tdee - minCalories))
            let neededDays = Int(ceil((delta * 7700.0) / safeDailyChangeBudget))
            recommendedDays = Int32(max(neededDays, days + 1))
            recommendedDate = Calendar.current.date(byAdding: .day, value: Int(recommendedDays ?? 0), to: today)
            reason = "Your target requires about \(Int(dailyCalories)) kcal/day, below the USDA minimum (\(Int(minCalories)) kcal/day)."
        }

        suggestedTargetDate = recommendedDate

        let heightM = height / 100.0
        let targetWeight = targetWeightKg ?? weight
        let bmiStart = weight / (heightM * heightM)
        let bmiTarget = targetWeight / (heightM * heightM)

        let milestones = buildMilestones(
            startWeight: weight,
            targetWeight: targetWeight,
            startDate: today,
            endDate: endDate
        )

        let validation = GoalValidationResultWrapper(
            isRealistic: isRealistic,
            reason: reason,
            recommendedTargetDays: recommendedDays,
            calculatedDailyCalories: dailyCalories,
            weeklyWeightLossRate: weeklyRate
        )

        calculatedPlan = GoalPlanWrapper(
            goalType: selectedGoal.backendValue,
            currentMetrics: CurrentMetricsWrapper(
                weightKg: weight,
                heightCm: height,
                age: Int32(personAge),
                gender: gender,
                activityLevel: activityLevel,
                bmi: bmiStart
            ),
            targetMetrics: TargetMetricsWrapper(
                weightKg: targetWeight,
                targetDate: isoDateOnly(targetDate),
                targetDays: Int32(days),
                targetBMI: bmiTarget
            ),
            calculations: GoalCalculationsWrapper(
                bmr: bmr,
                tdee: tdee,
                dailyCalories: dailyCalories,
                weeklyWeightLossRate: weeklyRate,
                totalWeightToLose: totalWeightDelta,
                calorieDeficitPerDay: abs(tdee - dailyCalories)
            ),
            validation: validation,
            milestones: milestones
        )

        if !isRealistic {
            validationError = "Plan needs adjustment before proceeding."
        } else {
            validationError = nil
        }
    }

    private func buildMilestones(startWeight: Double, targetWeight: Double, startDate: Date, endDate: Date) -> [WeeklyMilestoneWrapper] {
        let totalDays = max(1, Calendar.current.dateComponents([.day], from: startDate, to: endDate).day ?? 1)
        let weeks = max(1, Int(ceil(Double(totalDays) / 7.0)))
        let delta = targetWeight - startWeight

        return (0...weeks).map { week in
            let progress = min(1.0, Double(week * 7) / Double(totalDays))
            let projected = startWeight + (delta * progress)
            let cumulative = abs(startWeight - projected)
            return WeeklyMilestoneWrapper(
                week: Int32(week),
                projectedWeightKg: projected,
                cumulativeWeightLossKg: cumulative,
                percentComplete: progress * 100.0
            )
        }
    }

    private func sharedGoalType() -> GoalType {
        switch selectedGoal {
        case .weightLoss:
            return .weightLoss
        case .weightGain:
            return .muscleGain
        case .fitness:
            return .improvedEnergy
        case .maintenance:
            return .maintenance
        }
    }

    private func isoDateOnly(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: date)
    }

    func displayDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }


    }

enum ManualGoalType: CaseIterable {
    case weightLoss
    case weightGain
    case fitness
    case maintenance

    var title: String {
        switch self {
        case .weightLoss: return "Weight Loss"
        case .weightGain: return "Weight Gain"
        case .fitness: return "Fitness"
        case .maintenance: return "Maintenance"
        }
    }

    var subtitle: String {
        switch self {
        case .weightLoss: return "Reduce body weight with safe calorie targets"
        case .weightGain: return "Increase body weight and support muscle gain"
        case .fitness: return "Improve performance and daily energy"
        case .maintenance: return "Maintain current weight with balanced nutrition"
        }
    }

    var backendValue: String {
        switch self {
        case .weightLoss: return "weight_loss"
        case .weightGain: return "muscle_gain"
        case .fitness: return "improved_energy"
        case .maintenance: return "maintenance"
        }
    }

    static func from(goalType: String) -> ManualGoalType? {
        switch goalType.lowercased() {
        case "weight_loss": return .weightLoss
        case "muscle_gain": return .weightGain
        case "improved_energy", "fitness": return .fitness
        case "maintenance": return .maintenance
        default: return nil
        }
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

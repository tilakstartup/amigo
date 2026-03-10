import SwiftUI
import shared

struct AgentConversationView: View {
    @StateObject private var viewModel: AgentConversationViewModel
    let onComplete: ([String: String]) -> Void
    
    init(
        sessionManager: SessionManager,
        chatConfig: ChatSessionConfig,
        onComplete: @escaping ([String: String]) -> Void
    ) {
        self.onComplete = onComplete
        _viewModel = StateObject(wrappedValue: AgentConversationViewModel(sessionManager: sessionManager, chatConfig: chatConfig))
    }
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                gradient: Gradient(colors: [Color.pink.opacity(0.1), Color.purple.opacity(0.1)]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Amigo")
                            .font(.title2)
                            .fontWeight(.bold)
                        Text("Your AI Health Coach")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    // Amigo avatar
                    Image(systemName: "heart.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.pink)
                }
                .padding()
                .background(Color(.systemBackground))
                
                // Messages
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            ForEach(viewModel.messages) { message in
                                MessageBubbleView(message: message, onQuickReply: { reply in
                                    Task {
                                        await viewModel.sendQuickReply(reply)
                                    }
                                })
                                .id(message.id)
                            }
                            
                            // Typing indicator
                            if viewModel.isTyping {
                                TypingIndicatorView()
                                    .id("typing")
                            }
                        }
                        .padding()
                    }
                    .onChange(of: viewModel.messages.count) { _ in
                        withAnimation {
                            if let lastMessage = viewModel.messages.last {
                                proxy.scrollTo(lastMessage.id, anchor: .bottom)
                            }
                        }
                    }
                    .onChange(of: viewModel.isTyping) { isTyping in
                        if isTyping {
                            withAnimation {
                                proxy.scrollTo("typing", anchor: .bottom)
                            }
                        }
                    }
                }
                
                // Input area
                if viewModel.needsTextInput {
                    TextInputView(
                        placeholder: viewModel.inputPlaceholder,
                        text: $viewModel.userInput,
                        onSend: {
                            Task {
                                await viewModel.sendMessage()
                            }
                        }
                    )
                    .padding()
                    .background(Color(.systemBackground))
                }
            }
        }
        .onAppear {
            Task {
                await viewModel.startChat()
            }
        }
        .onChange(of: viewModel.isComplete) { isComplete in
            if isComplete {
                // Handle permissions if needed
                if viewModel.shouldRequestPermissions {
                    viewModel.showPermissionsSheet = true
                } else {
                    let profileData = viewModel.getProfileData()
                    onComplete(profileData)
                }
            }
        }
        .sheet(isPresented: $viewModel.showPermissionsSheet) {
            PermissionsRequestView(
                onComplete: {
                    viewModel.showPermissionsSheet = false
                    let profileData = viewModel.getProfileData()
                    onComplete(profileData)
                }
            )
        }
    }
}

// MARK: - Message Bubble View
struct MessageBubbleView: View {
    let message: MessageViewModel
    let onQuickReply: (String) -> Void
    @State private var showMessage = false
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if message.isFromAmigo {
                // Amigo avatar (only show if there's actual content)
                if !message.text.isEmpty || message.isFeatureIntro {
                    Image(systemName: "heart.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(.pink)
                }
            } else {
                Spacer()
            }
            
            VStack(alignment: message.isFromAmigo ? .leading : .trailing, spacing: 8) {
                // Feature intro only (just the card, no message)
                if message.isFeatureIntro, let feature = message.feature {
                    FeatureCard(feature: feature)
                        .transition(.scale.combined(with: .opacity))
                }
                // Regular message with optional feature card
                else {
                    // Feature card before message (if present and not a feature intro)
                    if let feature = message.feature, !message.isFeatureIntro {
                        FeatureCard(feature: feature)
                    }
                    
                    // Message bubble (only if there's text)
                    if !message.text.isEmpty {
                        Text(message.text)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(message.isFromAmigo ? Color(.systemGray5) : Color.pink)
                            .foregroundColor(message.isFromAmigo ? .primary : .white)
                            .cornerRadius(20)
                            .frame(maxWidth: UIScreen.main.bounds.width * 0.7, alignment: message.isFromAmigo ? .leading : .trailing)
                    }

                    if message.isFromAmigo,
                       message.renderType == "message_with_summary",
                       let items = message.renderItems,
                       !items.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(items, id: \.self) { item in
                                HStack(alignment: .top, spacing: 6) {
                                    Text("•")
                                        .foregroundColor(.secondary)
                                    Text(item)
                                        .font(.subheadline)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .padding(12)
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                        .frame(maxWidth: UIScreen.main.bounds.width * 0.7, alignment: .leading)
                    }
                    
                    // Reply options based on type
                    if let replyType = message.replyType {
                        switch replyType {
                        case "quick_pills":
                            if let replies = message.replies, !replies.isEmpty {
                                QuickReplyPillsView(options: replies, onSelect: onQuickReply, isDisabled: message.isDisabled)
                            }
                        case "yes_no":
                            YesNoButtonsView(
                                options: message.replies,
                                onSelect: onQuickReply,
                                isDisabled: message.isDisabled
                            )
                        case "list":
                            if let replies = message.replies, !replies.isEmpty {
                                ListPickerView(options: replies, onSelect: onQuickReply, isDisabled: message.isDisabled)
                            }
                        case "date":
                            DatePickerView(onSelect: onQuickReply, isDisabled: message.isDisabled)
                        case "weight":
                            WeightPickerView(onSelect: onQuickReply, isDisabled: message.isDisabled)
                        default:
                            EmptyView()
                        }
                    }
                }
                
                // Timestamp (only if there's content)
                if !message.text.isEmpty || message.isFeatureIntro {
                    Text(message.formattedTime)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            
            if !message.isFromAmigo {
                Spacer()
            }
        }
        .opacity(showMessage ? 1 : 0)
        .transition(.opacity.combined(with: .move(edge: message.isFromAmigo ? .leading : .trailing)))
        .onAppear {
            if message.delayAfterPrevious > 0 {
                DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(message.delayAfterPrevious))) {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        showMessage = true
                    }
                }
            } else {
                withAnimation(.easeInOut(duration: 0.2)) {
                    showMessage = true
                }
            }
        }
    }
}

// MARK: - Feature Card View
struct FeatureCard: View {
    let feature: FeatureIntro
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Text("Feature Highlight")
                    .font(.caption2)
                    .fontWeight(.semibold)
                    .foregroundColor(.pink)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.pink.opacity(0.12))
                    .cornerRadius(8)

                Spacer()
            }

            HStack(alignment: .top, spacing: 12) {
                Text(feature.icon)
                    .font(.system(size: 26))
                    .frame(width: 44, height: 44)
                    .background(Color.pink.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 6) {
                    Text(feature.name)
                        .font(.headline)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)

                    Text(feature.description_)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)
            }
        }
        .padding(14)
        .background(Color(.systemGray6))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.pink.opacity(0.25), lineWidth: 1)
        )
        .cornerRadius(14)
        .frame(maxWidth: UIScreen.main.bounds.width * 0.78, alignment: .leading)
    }
}

// MARK: - Quick Reply Pills View
struct QuickReplyPillsView: View {
    let options: [String]
    let onSelect: (String) -> Void
    let isDisabled: Bool
    
    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(options, id: \.self) { option in
                Button(action: {
                    if !isDisabled {
                        onSelect(option)
                    }
                }) {
                    Text(option)
                        .font(.subheadline)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(isDisabled ? Color.gray.opacity(0.1) : Color.pink.opacity(0.1))
                        .foregroundColor(isDisabled ? .gray : .pink)
                        .cornerRadius(20)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(isDisabled ? Color.gray : Color.pink, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(isDisabled)
            }
        }
    }
}

// MARK: - Yes/No Buttons View
private struct YesNoButtonsView: View {
    let options: [String]?
    let onSelect: (String) -> Void
    let isDisabled: Bool

    private var labels: [String] {
        let cleaned = options?
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .prefix(2) ?? []
        let resolved = Array(cleaned)
        return resolved.count == 2 ? resolved : ["Yes", "No"]
    }
    
    var body: some View {
        HStack(spacing: 12) {
            Button(action: {
                if !isDisabled {
                    onSelect(labels[0])
                }
            }) {
                Text(labels[0])
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(isDisabled ? Color.gray : Color.pink)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
            
            Button(action: {
                if !isDisabled {
                    onSelect(labels[1])
                }
            }) {
                Text(labels[1])
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(isDisabled ? Color.gray.opacity(0.3) : Color(.systemGray5))
                    .foregroundColor(isDisabled ? .gray : .primary)
                    .cornerRadius(12)
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
        }
        .frame(maxWidth: UIScreen.main.bounds.width * 0.7)
    }
}

// MARK: - List Picker View
struct ListPickerView: View {
    let options: [String]
    let onSelect: (String) -> Void
    let isDisabled: Bool
    @State private var selectedOption: String?
    @State private var showPicker = false
    
    var body: some View {
        VStack(spacing: 8) {
            Button(action: {
                if !isDisabled {
                    showPicker.toggle()
                }
            }) {
                HStack {
                    Text(selectedOption ?? "Select an option")
                        .foregroundColor(selectedOption == nil ? .secondary : .primary)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .foregroundColor(.secondary)
                }
                .padding()
                .background(isDisabled ? Color.gray.opacity(0.2) : Color(.systemGray6))
                .cornerRadius(12)
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
            
            if showPicker && !isDisabled {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(options, id: \.self) { option in
                            Button(action: {
                                selectedOption = option
                                showPicker = false
                                onSelect(option)
                            }) {
                                HStack {
                                    Text(option)
                                        .foregroundColor(.primary)
                                    Spacer()
                                    if selectedOption == option {
                                        Image(systemName: "checkmark")
                                            .foregroundColor(.pink)
                                    }
                                }
                                .padding()
                                .background(Color(.systemBackground))
                            }
                            .buttonStyle(.plain)
                            
                            if option != options.last {
                                Divider()
                            }
                        }
                    }
                }
                .frame(maxHeight: 200)
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }
        }
        .frame(maxWidth: UIScreen.main.bounds.width * 0.7)
    }
}

// MARK: - Weight Picker View
struct WeightPickerView: View {
    let onSelect: (String) -> Void
    let isDisabled: Bool
    @State private var selectedWeight: Double = 70.0

    var body: some View {
        VStack(spacing: 12) {
            VStack(spacing: 4) {
                Text(String(format: "%.1f kg", selectedWeight))
                    .font(.headline)
                    .foregroundColor(.primary)
                Text("Use the slider to set your weight")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Slider(value: $selectedWeight, in: 30...200, step: 0.5)
                .tint(.pink)
                .disabled(isDisabled)

            Button(action: {
                if !isDisabled {
                    onSelect(String(format: "%.1f", selectedWeight))
                }
            }) {
                Text("Confirm weight")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(isDisabled ? Color.gray : Color.pink)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
        .frame(maxWidth: UIScreen.main.bounds.width * 0.7)
    }
}

// MARK: - Date Picker View
struct DatePickerView: View {
    let onSelect: (String) -> Void
    let isDisabled: Bool
    @State private var selectedDate = Date()
    @State private var showPicker = false
    @State private var hasSelected = false
    
    var body: some View {
        VStack(spacing: 8) {
            Button(action: {
                if !isDisabled {
                    showPicker.toggle()
                }
            }) {
                HStack {
                    Text(hasSelected ? formatDate(selectedDate) : "Select a date")
                        .foregroundColor(hasSelected ? .primary : .secondary)
                    Spacer()
                    Image(systemName: "calendar")
                        .foregroundColor(isDisabled ? .gray : .pink)
                }
                .padding()
                .background(isDisabled ? Color.gray.opacity(0.2) : Color(.systemGray6))
                .cornerRadius(12)
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
            
            if showPicker && !isDisabled {
                VStack(spacing: 12) {
                    DatePicker(
                        "Select Date",
                        selection: $selectedDate,
                        in: Date()...,
                        displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(12)
                    
                    Button(action: {
                        hasSelected = true
                        showPicker = false
                        let dateString = formatDateForAPI(selectedDate)
                        onSelect(dateString)
                    }) {
                        Text("Confirm")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.pink)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }
        }
        .frame(maxWidth: UIScreen.main.bounds.width * 0.7)
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }
    
    private func formatDateForAPI(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}

// MARK: - Flow Layout (for wrapping pills)
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = FlowResult(
            in: proposal.replacingUnspecifiedDimensions().width,
            subviews: subviews,
            spacing: spacing
        )
        return result.size
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = FlowResult(
            in: bounds.width,
            subviews: subviews,
            spacing: spacing
        )
        for (index, subview) in subviews.enumerated() {
            subview.place(at: CGPoint(x: bounds.minX + result.positions[index].x, y: bounds.minY + result.positions[index].y), proposal: .unspecified)
        }
    }
    
    struct FlowResult {
        var size: CGSize = .zero
        var positions: [CGPoint] = []
        
        init(in maxWidth: CGFloat, subviews: Subviews, spacing: CGFloat) {
            var x: CGFloat = 0
            var y: CGFloat = 0
            var lineHeight: CGFloat = 0
            
            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)
                
                if x + size.width > maxWidth && x > 0 {
                    x = 0
                    y += lineHeight + spacing
                    lineHeight = 0
                }
                
                positions.append(CGPoint(x: x, y: y))
                lineHeight = max(lineHeight, size.height)
                x += size.width + spacing
            }
            
            self.size = CGSize(width: maxWidth, height: y + lineHeight)
        }
    }
}

// MARK: - Typing Indicator View
struct TypingIndicatorView: View {
    @State private var animationPhase = 0
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Image(systemName: "heart.circle.fill")
                .font(.system(size: 32))
                .foregroundColor(.pink)
            
            HStack(spacing: 4) {
                ForEach(0..<3) { index in
                    Circle()
                        .fill(Color.gray)
                        .frame(width: 8, height: 8)
                        .opacity(animationPhase == index ? 1.0 : 0.4)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(.systemGray5))
            .cornerRadius(20)
            
            Spacer()
        }
        .onAppear {
            Timer.scheduledTimer(withTimeInterval: 0.4, repeats: true) { _ in
                withAnimation {
                    animationPhase = (animationPhase + 1) % 3
                }
            }
        }
    }
}

// MARK: - Text Input View
struct TextInputView: View {
    let placeholder: String
    @Binding var text: String
    let onSend: () -> Void
    @FocusState private var isFocused: Bool
    
    var body: some View {
        HStack(spacing: 12) {
            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(.systemGray6))
                .cornerRadius(24)
                .focused($isFocused)
                .onSubmit {
                    if !text.trimmingCharacters(in: .whitespaces).isEmpty {
                        onSend()
                    }
                }
            
            Button(action: onSend) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(text.trimmingCharacters(in: .whitespaces).isEmpty ? .gray : .pink)
            }
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }
}

// MARK: - Permissions Request View
struct PermissionsRequestView: View {
    @StateObject private var viewModel = PermissionsViewModel()
    let onComplete: () -> Void
    
    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: "hand.raised.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.pink)
                    
                    Text("Just a Few Permissions")
                        .font(.title2)
                        .fontWeight(.bold)
                    
                    Text("To give you the best experience, I need access to a few things")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
                .padding(.top, 40)
                
                // Permission cards
                VStack(spacing: 16) {
                    PermissionCardCompactView(
                        icon: "camera.fill",
                        title: "Camera",
                        description: "Snap photos of meals for easy logging",
                        status: viewModel.cameraStatus,
                        onRequest: {
                            await viewModel.requestCameraPermission()
                        }
                    )
                    
                    PermissionCardCompactView(
                        icon: "bell.fill",
                        title: "Notifications",
                        description: "Get reminders for water and fasting",
                        status: viewModel.notificationStatus,
                        onRequest: {
                            await viewModel.requestNotificationPermission()
                        }
                    )
                    
                    PermissionCardCompactView(
                        icon: "heart.fill",
                        title: "Health Data",
                        description: "Track activity and health metrics",
                        status: viewModel.healthKitStatus,
                        onRequest: {
                            await viewModel.requestHealthKitPermission()
                        }
                    )
                }
                .padding(.horizontal)
                
                Spacer()
                
                // Continue button
                Button(action: onComplete) {
                    Text("Continue")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.pink)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom, 20)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Skip") {
                        onComplete()
                    }
                    .foregroundColor(.secondary)
                }
            }
        }
    }
}

// MARK: - Compact Permission Card
struct PermissionCardCompactView: View {
    let icon: String
    let title: String
    let description: String
    let status: PermissionStatus
    let onRequest: () async -> Void
    
    @State private var isRequesting = false
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 24))
                .foregroundColor(.pink)
                .frame(width: 40)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            if status == .notDetermined {
                Button(action: {
                    Task {
                        isRequesting = true
                        await onRequest()
                        isRequesting = false
                    }
                }) {
                    Text("Enable")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.pink)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .disabled(isRequesting)
            } else {
                Image(systemName: status == .granted ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundColor(status == .granted ? .green : .orange)
                    .font(.system(size: 24))
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    let previewConfig = ChatSessionConfig(
        cap: "preview",
        responsibilities: ["Collect minimal profile details"],
        collectData: ["first_name"],
        collectMetrics: [],
        initialMessage: "Let's start."
    )
    AgentConversationView(sessionManager: sessionManager, chatConfig: previewConfig, onComplete: { _ in })
}

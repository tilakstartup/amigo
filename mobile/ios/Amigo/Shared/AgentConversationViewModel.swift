import SwiftUI
import shared
import Combine

@MainActor
class AgentConversationViewModel: ObservableObject {
    @Published var messages: [MessageViewModel] = []
    @Published var isTyping: Bool = false
    @Published var userInput: String = ""
    @Published var needsTextInput: Bool = false
    @Published var inputPlaceholder: String = "Type your message..."
    @Published var isComplete: Bool = false
    @Published var shouldRequestPermissions: Bool = false
    @Published var showPermissionsSheet: Bool = false
    @Published private(set) var isSubmitting: Bool = false
    
    private var engine: AmigoAgentConversation?
    private var cancellables = Set<AnyCancellable>()
    private let sessionManager: SessionManager
    
    init(sessionManager: SessionManager) {
        self.sessionManager = sessionManager
        setupEngine()
    }
    
    private func setupEngine() {
        // Initialize Bedrock client with correct API endpoint
        let apiEndpoint = "https://n96755fzqk.execute-api.us-east-1.amazonaws.com/dev/invoke"
        
        engine = AmigoAgentConversationFactory.shared.create(
            apiEndpoint: apiEndpoint,
            sessionManager: sessionManager
        )
    }
    
    func startOnboarding() async {
        guard let engine = engine else {
            return
        }
        
        do {
            _ = try await engine.startSession(
                cap: "onboarding",
                responsibilities: [
                    "just introduce yourself as Amigo, a friendly and supportive health coach and jump into questions",
                    "Collect onboarding profile information from user",
                    "start with the goal question first",
                    "Validate and normalize collected onboarding fields",
                    "Summarize the onboarding details and review with the user",
                    "Save onboarding data only after user confirmation",
                    "Mark onboarding as complete and close the onboarding chat"
                ],
                collectData: ["first_name", "last_name", "age", "weight", "height", "gender", "activity_level", "goal_type", "goal_detail", "goal_by_when"],
                collectMetrics: ["bmr", "tdee", "daily_calories"],
                initialMessage: "I want to start onboarding."
            )
            // Start observing messages
            observeMessages()
            refreshMessagesFromEngine()
        } catch {
            print("Error starting onboarding: \(error)")
        }
    }

    private func mapMessages(_ rawMessages: [ConversationMessage]) -> [MessageViewModel] {
        rawMessages.map { msg in
            let replies = msg.replies?.compactMap { item -> String? in
                if let str = item as? String { return str }
                if let nsStr = item as? NSString { return String(nsStr) }
                return nil
            }

            let renderItems = msg.renderItems?.compactMap { item -> String? in
                if let str = item as? String { return str }
                if let nsStr = item as? NSString { return String(nsStr) }
                return nil
            }

            return MessageViewModel(
                id: msg.id,
                text: msg.text,
                isFromAmigo: msg.isFromAmigo,
                timestamp: msg.timestamp,
                replyType: msg.replyType,
                replies: replies,
                renderType: msg.renderType,
                renderItems: renderItems,
                feature: msg.feature,
                isFeatureIntro: msg.isFeatureIntro,
                delayAfterPrevious: msg.delayAfterPrevious,
                isDisabled: msg.isDisabled
            )
        }
    }

    private func refreshMessagesFromEngine() {
        guard let engine = engine else { return }

        let snapshot = engine.getMessagesSnapshot()
        let mapped = mapMessages(snapshot)
        messages = mapped

        if let lastMessage = snapshot.last, lastMessage.isFromAmigo {
            needsTextInput = lastMessage.replyType == "text" || lastMessage.replyType == nil
        }
    }
    
    private func observeMessages() {
        guard let engine = engine else { return }
        
        // Observe messages from the engine
        Task {
            let messagesFlow = engine.messages
            do {
                let collector = FlowCollector<NSArray> { [weak self] messages in
                    guard let self = self else { return }
                    await MainActor.run {
                        let currentMessages = (messages as? [ConversationMessage] ?? [])
                        self.messages = self.mapMessages(currentMessages)
                        
                        // Update input state based on last message
                        if let lastMessage = messages.lastObject as? ConversationMessage, lastMessage.isFromAmigo {
                            self.needsTextInput = lastMessage.replyType == "text" || lastMessage.replyType == nil
                        }
                    }
                }
                try await messagesFlow.collect(collector: collector)
            } catch {
                print("Flow collection error: \(error)")
            }
        }
        
        // Observe typing state
        Task {
            let typingFlow = engine.isTyping
            do {
                let collector = FlowCollector<KotlinBoolean> { [weak self] isTyping in
                    guard let self = self else { return }
                    await MainActor.run {
                        self.isTyping = isTyping.boolValue
                    }
                }
                try await typingFlow.collect(collector: collector)
            } catch {
                print("Flow collection error: \(error)")
            }
        }
        
        // Observe conversation state
        Task {
            let stateFlow = engine.conversationState
            do {
                let collector = FlowCollector<OnboardingState> { [weak self] state in
                    guard let self = self else { return }
                    await MainActor.run {
                        self.handleStateChange(state)
                    }
                }
                try await stateFlow.collect(collector: collector)
            } catch {
                print("Flow collection error: \(error)")
            }
        }
    }
    
    private func handleStateChange(_ state: OnboardingState) {
        switch state {
        case is OnboardingState.Complete:
            isComplete = true
            shouldRequestPermissions = false
        case is OnboardingState.RequestingPermissions:
            needsTextInput = false
            shouldRequestPermissions = true
            showPermissionsSheet = true
        default:
            break
        }
    }
    
    func sendMessage() async {
        let message = userInput.trimmingCharacters(in: .whitespaces)
        guard !message.isEmpty, let engine = engine, !isSubmitting else { return }
        isSubmitting = true
        
        // Clear input immediately
        userInput = ""
        needsTextInput = false
        
        do {
            _ = try await engine.processUserResponse(response: message)
            refreshMessagesFromEngine()
        } catch {
            print("Error processing response: \(error)")
            refreshMessagesFromEngine()
        }
        isSubmitting = false
    }
    
    func sendQuickReply(_ reply: String) async {
        guard let engine = engine, !isSubmitting else { return }
        isSubmitting = true
        
        do {
            _ = try await engine.processQuickReply(option: reply)
            refreshMessagesFromEngine()
        } catch {
            print("Error processing quick reply: \(error)")
            refreshMessagesFromEngine()
        }
        isSubmitting = false
    }
    
    func getProfileData() -> [String: String] {
        guard let engine = engine else { return [:] }
        
        let kotlinMap = engine.getProfileData()
        var swiftDict: [String: String] = [:]
        
        // Convert Kotlin Map to Swift Dictionary
        for key in kotlinMap.keys {
            if let value = kotlinMap[key] as? String {
                swiftDict[key as! String] = value
            }
        }
        
        return swiftDict
    }
}

// MARK: - Message View Model
struct MessageViewModel: Identifiable {
    let id: String
    let text: String
    let isFromAmigo: Bool
    let timestamp: Int64
    let replyType: String?
    let replies: [String]?
    let renderType: String?
    let renderItems: [String]?
    let feature: FeatureIntro?
    let isFeatureIntro: Bool
    let delayAfterPrevious: Int64
    let isDisabled: Bool
    
    var formattedTime: String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000)
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Flow Collector Implementation
class FlowCollector<T>: Kotlinx_coroutines_coreFlowCollector {
    private let onValue: (T) async -> Void
    
    init(onValue: @escaping (T) async -> Void) {
        self.onValue = onValue
    }
    
    func emit(value: Any?) async throws {
        if let typedValue = value as? T {
            await onValue(typedValue)
        }
    }
}

import SwiftUI
import shared
import Combine

struct ChatSessionConfig {
    let cap: String
    let responsibilities: [String]
    let collectData: [String]
    let collectMetrics: [String]
    let initialMessage: String
}

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
    private let chatConfig: ChatSessionConfig
    
    init(sessionManager: SessionManager, chatConfig: ChatSessionConfig) {
        print("🔧 iOS: Initializing AgentConversationViewModel")
        print("📍 iOS: Cap: \(chatConfig.cap)")
        self.sessionManager = sessionManager
        self.chatConfig = chatConfig
        setupEngine()
    }
    
    private func setupEngine() {
        // Use the correct API endpoint from AppConfig
        let apiEndpoint = AppConfig.shared.BEDROCK_API_ENDPOINT
        print("🔧 iOS: Setting up engine with endpoint: \(apiEndpoint)")
        print("📍 iOS: Agent ID: \(AppConfig.shared.BEDROCK_AGENT_ID)")
        print("📍 iOS: Agent Alias: \(AppConfig.shared.BEDROCK_AGENT_ALIAS_ID)")
        
        engine = AmigoAgentConversationFactory.shared.create(
            apiEndpoint: apiEndpoint,
            sessionManager: sessionManager,
            supabaseClient: try? SupabaseClientProvider.shared.getClient()
        )
        print("✅ iOS: Engine created successfully")
    }
    
    func startChat() async {
        print("🚀 iOS: startChat() called")
        guard let engine = engine else {
            print("❌ iOS: Engine is nil!")
            return
        }
        
        print("📞 iOS: Calling engine.startSession()")
        print("📍 iOS: Cap: \(chatConfig.cap)")
        print("📍 iOS: Responsibilities: \(chatConfig.responsibilities.count) items")
        print("📍 iOS: CollectData: \(chatConfig.collectData.count) fields")
        print("📍 iOS: InitialMessage: \(chatConfig.initialMessage)")
        
        do {
            _ = try await engine.startSession(
                cap: chatConfig.cap,
                responsibilities: chatConfig.responsibilities,
                collectData: chatConfig.collectData,
                collectMetrics: chatConfig.collectMetrics,
                initialMessage: chatConfig.initialMessage
            )
            print("✅ iOS: engine.startSession() completed successfully")
            // Start observing messages
            observeMessages()
            refreshMessagesFromEngine()
        } catch {
            print("❌ iOS: Error starting chat: \(error)")
            print("❌ iOS: Error details: \(error.localizedDescription)")
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
        guard let engine = engine else {
            print("⚠️ iOS: Engine is nil in refreshMessagesFromEngine")
            return
        }

        let snapshot = engine.getMessagesSnapshot()
        print("📨 iOS: Refreshing messages - Count: \(snapshot.count)")
        let mapped = mapMessages(snapshot)
        messages = mapped

        if let lastMessage = snapshot.last, lastMessage.isFromAmigo {
            needsTextInput = lastMessage.replyType == "text" || lastMessage.replyType == nil
            print("💬 iOS: needsTextInput = \(needsTextInput), replyType = \(lastMessage.replyType ?? "nil")")
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
        
        print("📤 iOS: Sending message: \(message)")
        isSubmitting = true
        
        // Clear input immediately
        userInput = ""
        needsTextInput = false
        
        do {
            _ = try await engine.processUserResponse(response: message)
            print("✅ iOS: Message processed successfully")
            refreshMessagesFromEngine()
        } catch {
            print("❌ iOS: Error processing response: \(error)")
            print("❌ iOS: Error details: \(error.localizedDescription)")
            refreshMessagesFromEngine()
        }
        isSubmitting = false
    }
    
    func sendQuickReply(_ reply: String) async {
        guard let engine = engine, !isSubmitting else { return }
        
        print("📤 iOS: Sending quick reply: \(reply)")
        isSubmitting = true
        
        do {
            _ = try await engine.processQuickReply(option: reply)
            print("✅ iOS: Quick reply processed successfully")
            refreshMessagesFromEngine()
        } catch {
            print("❌ iOS: Error processing quick reply: \(error)")
            print("❌ iOS: Error details: \(error.localizedDescription)")
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

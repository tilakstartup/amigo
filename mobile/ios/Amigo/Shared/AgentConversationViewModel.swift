import SwiftUI
import shared
import Combine

struct ChatSessionConfig {
    let hat: String
    let responsibilities: [String]
    let data_to_be_collected: [String]
    let data_to_be_calculated: [String]
    let initial_message: String
}

// MARK: - SessionConfig Helpers
extension ChatSessionConfig {
    /// Create a ChatSessionConfig from a Kotlin SessionConfig name
    static func from(configName: String) -> ChatSessionConfig? {
        guard let kotlinConfig = SessionConfigs.shared.getConfig(hat: configName) else {
            return nil
        }
        
        return ChatSessionConfig(
            hat: kotlinConfig.hat,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            data_to_be_collected: kotlinConfig.data_to_be_collected.map { $0 as String },
            data_to_be_calculated: kotlinConfig.data_to_be_calculated.map { $0 as String },
            initial_message: kotlinConfig.initial_message
        )
    }
    
    /// Predefined onboarding config
    static var onboarding: ChatSessionConfig {
        let kotlinConfig = SessionConfigs.shared.ONBOARDING
        return ChatSessionConfig(
            hat: kotlinConfig.hat,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            data_to_be_collected: kotlinConfig.data_to_be_collected.map { $0 as String },
            data_to_be_calculated: kotlinConfig.data_to_be_calculated.map { $0 as String },
            initial_message: kotlinConfig.initial_message
        )
    }
    
    /// Predefined goal setting config
    static var goalSetting: ChatSessionConfig {
        let kotlinConfig = SessionConfigs.shared.GOAL_SETTING
        return ChatSessionConfig(
            hat: kotlinConfig.hat,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            data_to_be_collected: kotlinConfig.data_to_be_collected.map { $0 as String },
            data_to_be_calculated: kotlinConfig.data_to_be_calculated.map { $0 as String },
            initial_message: kotlinConfig.initial_message
        )
    }

    /// Predefined general chat config for the main chat tab
    static var generalChat: ChatSessionConfig {
        let kotlinConfig = SessionConfigs.shared.GENERAL_CHAT
        return ChatSessionConfig(
            hat: kotlinConfig.hat,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            data_to_be_collected: kotlinConfig.data_to_be_collected.map { $0 as String },
            data_to_be_calculated: kotlinConfig.data_to_be_calculated.map { $0 as String },
            initial_message: kotlinConfig.initial_message
        )
    }
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
    private var hasStarted = false
    
    init(sessionManager: SessionManager, chatConfig: ChatSessionConfig) {
        print("🔧 iOS: Initializing AgentConversationViewModel")
        print("📍 iOS: Hat: \(chatConfig.hat)")
        self.sessionManager = sessionManager
        self.chatConfig = chatConfig
        setupEngine()
    }
    
    private func setupEngine() {
        // Use the correct API endpoint from AppConfig
        let apiEndpoint = AppConfig.shared.BEDROCK_API_ENDPOINT
        print("🔧 iOS: Setting up engine with endpoint: \(apiEndpoint)")
        
        engine = AmigoAgentConversationFactory.shared.create(
            apiEndpoint: apiEndpoint,
            sessionManager: sessionManager,
            supabaseClient: try? SupabaseClientProvider.shared.getClient()
        )
        print("✅ iOS: Engine created successfully")
    }
    
    func startChat() async {
        guard !hasStarted else { return }
        hasStarted = true
        print("🚀 iOS: startChat() called")
        guard let engine = engine else {
            print("❌ iOS: Engine is nil!")
            return
        }
        
        print("📞 iOS: Calling engine.startSession()")
        print("📍 iOS: Hat: \(chatConfig.hat)")
        print("📍 iOS: Responsibilities: \(chatConfig.responsibilities.count) items")
        print("📍 iOS: Data to be collected: \(chatConfig.data_to_be_collected.count) fields")
        print("📍 iOS: Initial message: \(chatConfig.initial_message)")
        
        do {
            _ = try await engine.startSession(
                hat: chatConfig.hat,
                responsibilities: chatConfig.responsibilities,
                data_to_be_collected: chatConfig.data_to_be_collected,
                data_to_be_calculated: chatConfig.data_to_be_calculated,
                initial_message: chatConfig.initial_message
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

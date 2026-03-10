import Foundation
import SwiftUI
import shared

@MainActor
final class ConversationalOnboardingViewModel: ObservableObject {
    @Published var messages: [MessageViewModel] = []
    @Published var isTyping: Bool = false
    @Published var isComplete: Bool = false
    @Published var shouldRequestPermissions: Bool = false
    @Published var needsTextInput: Bool = false
    @Published var userInput: String = ""
    @Published var inputPlaceholder: String = "Type your message..."
    
    private let engine: AmigoAgentConversation
    private var isSubmitting: Bool = false
    
    init(sessionManager: SessionManager) {
        print("🔧 iOS: Initializing ConversationalOnboardingViewModel")
        print("📍 iOS: API Endpoint: \(AppConfig.shared.BEDROCK_API_ENDPOINT)")
        print("📍 iOS: Agent ID: \(AppConfig.shared.BEDROCK_AGENT_ID)")
        
        self.engine = AmigoAgentConversationFactory.shared.create(
            apiEndpoint: AppConfig.shared.BEDROCK_API_ENDPOINT,
            sessionManager: sessionManager,
            supabaseClient: try? SupabaseClientProvider.shared.getClient()
        )
        
        print("✅ iOS: Engine created successfully")
        observeEngine()
        print("✅ iOS: Engine observation started")
    }
    
    private func observeEngine() {
        print("👀 iOS: Setting up engine observers")
        
        // Observe messages
        Task {
            while !Task.isCancelled {
                let messagesList = engine.messages.value as! [shared.ConversationMessage]
                if messagesList.count != self.messages.count {
                    print("📨 iOS: Messages updated - Count: \(messagesList.count)")
                }
                self.messages = messagesList.map { msg in
                    MessageViewModel(
                        id: msg.id,
                        text: msg.text,
                        isFromAmigo: msg.isFromAmigo,
                        timestamp: msg.timestamp,
                        replyType: msg.replyType,
                        replies: msg.replies?.map { $0 as String },
                        renderType: msg.renderType,
                        renderItems: msg.renderItems?.map { $0 as String },
                        feature: msg.feature,
                        isFeatureIntro: msg.isFeatureIntro,
                        delayAfterPrevious: msg.delayAfterPrevious,
                        isDisabled: msg.isDisabled
                    )
                }
                
                // Update needsTextInput
                if let lastMessage = self.messages.last {
                    self.needsTextInput = lastMessage.isFromAmigo && 
                        (lastMessage.replyType == "text" || lastMessage.replyType == nil)
                }
                
                try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
            }
        }
        
        // Observe isTyping
        Task {
            while !Task.isCancelled {
                let previousTyping = self.isTyping
                if let typing = engine.isTyping.value as? Bool {
                    self.isTyping = typing
                } else if let typing = engine.isTyping.value as? KotlinBoolean {
                    self.isTyping = typing.boolValue
                }
                if previousTyping != self.isTyping {
                    print("⌨️ iOS: isTyping changed to: \(self.isTyping)")
                }
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
        
        // Observe conversation state
        Task {
            while !Task.isCancelled {
                let state = engine.conversationState.value
                let previousComplete = self.isComplete
                let previousPermissions = self.shouldRequestPermissions
                
                self.isComplete = state is shared.OnboardingState.Complete
                self.shouldRequestPermissions = state is shared.OnboardingState.RequestingPermissions
                
                if previousComplete != self.isComplete {
                    print("🏁 iOS: isComplete changed to: \(self.isComplete)")
                }
                if previousPermissions != self.shouldRequestPermissions {
                    print("🔐 iOS: shouldRequestPermissions changed to: \(self.shouldRequestPermissions)")
                }
                
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
        
        print("✅ iOS: All observers set up")
    }
    
    func startOnboarding() {
        print("🚀 iOS: startOnboarding() called")
        Task {
            do {
                print("📞 iOS: Calling engine.startSession()")
                print("📍 iOS: API Endpoint: \(AppConfig.shared.BEDROCK_API_ENDPOINT)")
                print("📍 iOS: Agent ID: \(AppConfig.shared.BEDROCK_AGENT_ID)")
                print("📍 iOS: Agent Alias: \(AppConfig.shared.BEDROCK_AGENT_ALIAS_ID)")
                
                try await engine.startSession(
                    cap: "onboarding",
                    responsibilities: [
                        "Collect onboarding profile information from user",
                        "Validate and normalize collected onboarding fields",
                        "Summarize the onboarding details and review with the user",
                        "Save onboarding data only after user confirmation",
                        "Mark onboarding as complete and close the onboarding chat"
                    ],
                    collectData: [
                        "first_name", "last_name", "age", "weight", "height",
                        "gender", "activity_level", "goal_type", "goal_detail", "goal_by_when"
                    ],
                    collectMetrics: ["bmr", "tdee", "daily_calories"],
                    initialMessage: "I want to start onboarding."
                )
                print("✅ iOS: engine.startSession() completed successfully")
            } catch {
                print("❌ iOS: Error starting onboarding: \(error.localizedDescription)")
                print("❌ iOS: Error details: \(error)")
            }
        }
    }
    
    func sendMessage() {
        let message = userInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty && !isSubmitting else { return }
        
        print("📤 iOS: Sending message: \(message)")
        isSubmitting = true
        userInput = ""
        
        Task {
            do {
                try await engine.processUserResponse(response: message)
                print("✅ iOS: Message processed successfully")
            } catch {
                print("❌ iOS: Error processing response: \(error.localizedDescription)")
                print("❌ iOS: Error details: \(error)")
            }
            isSubmitting = false
        }
    }
    
    func sendQuickReply(_ reply: String) {
        guard !isSubmitting else { return }
        
        print("📤 iOS: Sending quick reply: \(reply)")
        isSubmitting = true
        
        Task {
            do {
                try await engine.processQuickReply(option: reply)
                print("✅ iOS: Quick reply processed successfully")
            } catch {
                print("❌ iOS: Error processing quick reply: \(error.localizedDescription)")
                print("❌ iOS: Error details: \(error)")
            }
            isSubmitting = false
        }
    }
    
    func getProfileData() -> [String: String] {
        return engine.getProfileData() as? [String: String] ?? [:]
    }
}

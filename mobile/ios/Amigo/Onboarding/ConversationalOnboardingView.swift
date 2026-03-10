import SwiftUI
import shared

struct ConversationalOnboardingView: View {
    let sessionManager: SessionManager
    let onComplete: ([String: String]) -> Void

    private var chatConfig: ChatSessionConfig {
        ChatSessionConfig(
            cap: "onboarding",
            responsibilities: [
                "just introduce yourself as Amigo, a friendly and supportive health coach",
                "get the user profile first using get profile tool and greet if you get their name",
                "Fill UI render data too with all the information you get from profile",
                "jump into questions to collect onboarding profile information from user that you dont have",
                "start with the health goal question and move to other questions",
                "Validate and normalize collected onboarding fields",
                "Summarize the onboarding details and review with the user",
                "Save onboarding data only after user confirmation",
                "Mark onboarding as complete once you save"
            ],
            collectData: ["first_name", "last_name", "age", "weight", "height", "gender", "activity_level", "goal_type", "goal_detail", "goal_by_when"],
            collectMetrics: ["bmr", "tdee", "daily_calories"],
            initialMessage: "Hi, get my profile and let's start onboarding."
        )
    }

    var body: some View {
        AgentConversationView(
            sessionManager: sessionManager,
            chatConfig: chatConfig,
            onComplete: onComplete
        )
    }
}

import SwiftUI
import shared

struct BedrockTestView: View {
    @StateObject private var viewModel = BedrockTestViewModel()
    @State private var prompt: String = "Hello, how are you?"
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Bedrock Lambda Test")
                    .font(.title)
                    .padding()
                
                // Prompt input
                VStack(alignment: .leading, spacing: 8) {
                    Text("Prompt:")
                        .font(.headline)
                    
                    TextEditor(text: $prompt)
                        .frame(height: 100)
                        .padding(8)
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                        )
                }
                .padding(.horizontal)
                
                // Send button
                Button(action: {
                    viewModel.testBedrock(prompt: prompt)
                }) {
                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("Send to Bedrock")
                            .fontWeight(.semibold)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(viewModel.isLoading ? Color.gray : Color.blue)
                .foregroundColor(.white)
                .cornerRadius(10)
                .padding(.horizontal)
                .disabled(viewModel.isLoading)
                
                // Response display
                if let response = viewModel.response {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Response:")
                            .font(.headline)
                        
                        ScrollView {
                            Text(response)
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.green.opacity(0.1))
                                .cornerRadius(8)
                        }
                        .frame(maxHeight: 200)
                    }
                    .padding(.horizontal)
                }
                
                // Error display
                if let error = viewModel.error {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Error:")
                            .font(.headline)
                            .foregroundColor(.red)
                        
                        Text(error)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.red.opacity(0.1))
                            .cornerRadius(8)
                    }
                    .padding(.horizontal)
                }
                
                // Usage info
                if let usage = viewModel.usage {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Token Usage:")
                            .font(.headline)
                        Text("Input: \(usage.inputTokens) tokens")
                            .font(.caption)
                        Text("Output: \(usage.outputTokens) tokens")
                            .font(.caption)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(8)
                    .padding(.horizontal)
                }
                
                Spacer()
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

class BedrockTestViewModel: ObservableObject {
    @Published var response: String?
    @Published var error: String?
    @Published var usage: BedrockUsage?
    @Published var isLoading = false
    
    init() {
        checkConfiguration()
    }
    
    private func checkConfiguration() {
        // Get API endpoint from Info.plist
        guard let apiEndpoint = Bundle.main.object(forInfoDictionaryKey: "BEDROCK_API_ENDPOINT") as? String,
              apiEndpoint != "https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke" else {
            self.error = """
            BEDROCK_API_ENDPOINT not configured.
            
            To test Bedrock integration:
            1. Deploy CloudFormation stack (see docs/infrastructure/aws-bedrock.md)
            2. Update Info.plist with API endpoint
            3. Rebuild the app
            """
            return
        }
        
        // Configuration looks good
        self.error = nil
    }
    
    func testBedrock(prompt: String) {
        // Check configuration first
        guard let apiEndpoint = Bundle.main.object(forInfoDictionaryKey: "BEDROCK_API_ENDPOINT") as? String,
              apiEndpoint != "https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke" else {
            self.error = "BEDROCK_API_ENDPOINT not configured in Info.plist"
            return
        }
        
        isLoading = true
        response = nil
        error = nil
        usage = nil
        
        Task {
            do {
                // Get auth token from SessionManager
                guard let token = await SessionManagerHelper.shared.getAccessToken() else {
                    await MainActor.run {
                        self.error = "Not signed in. Please sign in first to test Bedrock."
                        self.isLoading = false
                    }
                    return
                }
                
                // TODO: Fix BedrockClient API call
                // For now, just show a placeholder message
                await MainActor.run {
                    self.response = "Bedrock test temporarily disabled. Use conversational onboarding to test AI integration."
                    self.isLoading = false
                }
                return
                
                // Create BedrockClient
                // let client = BedrockClientFactory.shared.create(
                //     apiEndpoint: apiEndpoint,
                //     getAuthToken: { token },
                //     maxRetries: 3
                // )
                //
                // // Call Bedrock
                // let result = try await client.invokeModel(
                //     modelId: "anthropic.claude-3-haiku-20240307-v1:0",
                //     prompt: prompt,
                //     maxTokens: 2048,
                //     temperature: 0.7,
                //     systemPrompt: nil
                // )
                
                // Placeholder - actual Bedrock call commented out above
                // await MainActor.run {
                //     if let bedrockResponse = result.getOrNull() {
                //         self.response = bedrockResponse.completion
                //         self.usage = bedrockResponse.usage
                //     } else if let error = result.exceptionOrNull() {
                //         self.error = "Error: \(error.localizedDescription)"
                //     }
                //     self.isLoading = false
                // }
            } catch {
                await MainActor.run {
                    self.error = "Error: \(error.localizedDescription)"
                    self.isLoading = false
                }
            }
        }
    }
}

// Helper to get access token from SessionManager
class SessionManagerHelper {
    static let shared = SessionManagerHelper()
    
    func getAccessToken() async -> String? {
        // In a real app, you would get this from your SessionManager instance
        // For testing, you can hardcode a token or get it from UserDefaults
        // This is just a placeholder implementation
        
        // Try to get from UserDefaults (where SessionManager stores it)
        if let token = UserDefaults.standard.string(forKey: "amigo_access_token") {
            return token
        }
        
        return nil
    }
}

#Preview {
    BedrockTestView()
}

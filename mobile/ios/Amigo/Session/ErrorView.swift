import SwiftUI

struct ErrorView: View {
    let message: String
    let onRetry: () async -> Void
    
    @State private var isRetrying = false
    
    var body: some View {
        VStack(spacing: 24) {
            // Error Icon
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 64))
                .foregroundColor(.orange)
            
            // Error Title
            Text("Error Loading Profile")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.primary)
            
            // Error Message
            Text(message)
                .font(.system(size: 16, weight: .regular))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(nil)
                .padding(.horizontal, 16)
            
            Spacer()
            
            // Retry Button
            Button(action: {
                Task {
                    isRetrying = true
                    await onRetry()
                    isRetrying = false
                }
            }) {
                if isRetrying {
                    HStack(spacing: 8) {
                        ProgressView()
                            .tint(.white)
                        Text("Retrying...")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                } else {
                    Text("Retry")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
            .disabled(isRetrying)
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}

#Preview {
    ErrorView(
        message: "Failed to load your profile. Please check your internet connection and try again.",
        onRetry: {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
        }
    )
}

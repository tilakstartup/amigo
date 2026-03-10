import SwiftUI
import shared

struct GoalManagementView: View {
    let sessionManager: SessionManager
    @State private var showTalkToAmigo = false
    @State private var showManualPlanning = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Set Your Health Goal")
                    .font(.title2)
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                Text("Choose how you'd like to plan")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                Spacer().frame(height: 8)
                
                // Talk to Amigo button
                Button(action: { showTalkToAmigo = true }) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "message.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.pink)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Talk to Amigo")
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                Text("Get personalized guidance")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
                
                // Set Manually button
                Button(action: { showManualPlanning = true }) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "slider.horizontal.3")
                                .font(.system(size: 24))
                                .foregroundColor(.blue)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Set Manually")
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                Text("Step-by-step form")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
                
                Spacer()
            }
            .padding()
            .navigationTitle("My Goal")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showTalkToAmigo) {
                AIGoalPlanningView(
                    sessionManager: sessionManager,
                    goalType: "weight_loss"
                )
            }
            .sheet(isPresented: $showManualPlanning) {
                ManualGoalPlanningView(
                    sessionManager: sessionManager,
                    goalType: "weight_loss"
                )
            }
        }
    }
}

#Preview {
    let secureStorage = SecureStorage()
    let sessionManager = AuthFactory.shared.createSessionManager(secureStorage: secureStorage)
    return GoalManagementView(sessionManager: sessionManager)
}

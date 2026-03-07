import SwiftUI
import UserNotifications
import HealthKit
import AVFoundation

struct PermissionsView: View {
    @StateObject private var viewModel = PermissionsViewModel()
    let onComplete: () -> Void
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Enable Permissions")
                    .font(.title2)
                    .fontWeight(.bold)
                    .padding(.top, 20)
                
                Text("Grant permissions to unlock the full Amigo experience")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                ScrollView {
                    VStack(spacing: 16) {
                        PermissionCardView(
                            icon: "camera.fill",
                            title: "Camera",
                            description: "Take photos of your meals for AI analysis",
                            status: viewModel.cameraStatus,
                            onRequest: {
                                await viewModel.requestCameraPermission()
                            }
                        )
                        
                        PermissionCardView(
                            icon: "bell.fill",
                            title: "Notifications",
                            description: "Receive reminders for water intake and fasting",
                            status: viewModel.notificationStatus,
                            onRequest: {
                                await viewModel.requestNotificationPermission()
                            }
                        )
                        
                        PermissionCardView(
                            icon: "heart.fill",
                            title: "Health Data",
                            description: "Sync your activity and health metrics",
                            status: viewModel.healthKitStatus,
                            onRequest: {
                                await viewModel.requestHealthKitPermission()
                            }
                        )
                    }
                    .padding(.horizontal)
                }
                
                Spacer()
                
                VStack(spacing: 12) {
                    Button(action: onComplete) {
                        Text("Continue")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.pink)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                    .padding(.horizontal)
                    
                    Button(action: onComplete) {
                        Text("Skip for now")
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.bottom, 20)
            }
        }
    }
}

struct PermissionCardView: View {
    let icon: String
    let title: String
    let description: String
    let status: PermissionStatus
    let onRequest: () async -> Void
    
    @State private var isRequesting = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 15) {
                Image(systemName: icon)
                    .font(.system(size: 30))
                    .foregroundColor(.pink)
                    .frame(width: 50)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                    
                    Text(description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                statusIcon
            }
            
            if status == .notDetermined {
                Button(action: {
                    Task {
                        isRequesting = true
                        await onRequest()
                        isRequesting = false
                    }
                }) {
                    Text("Enable")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.pink)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .disabled(isRequesting)
            } else if status == .denied {
                Button(action: {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }) {
                    Text("Open Settings")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.orange)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemGray6))
        )
    }
    
    @ViewBuilder
    private var statusIcon: some View {
        switch status {
        case .granted:
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.green)
                .font(.system(size: 24))
        case .denied:
            Image(systemName: "xmark.circle.fill")
                .foregroundColor(.red)
                .font(.system(size: 24))
        case .notDetermined:
            Image(systemName: "circle")
                .foregroundColor(.gray)
                .font(.system(size: 24))
        }
    }
}

enum PermissionStatus {
    case granted
    case denied
    case notDetermined
}

@MainActor
class PermissionsViewModel: ObservableObject {
    @Published var cameraStatus: PermissionStatus = .notDetermined
    @Published var notificationStatus: PermissionStatus = .notDetermined
    @Published var healthKitStatus: PermissionStatus = .notDetermined
    
    private let healthStore = HKHealthStore()
    
    init() {
        checkPermissions()
    }
    
    func checkPermissions() {
        checkCameraPermission()
        checkNotificationPermission()
        checkHealthKitPermission()
    }
    
    private func checkCameraPermission() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            cameraStatus = .granted
        case .denied, .restricted:
            cameraStatus = .denied
        case .notDetermined:
            cameraStatus = .notDetermined
        @unknown default:
            cameraStatus = .notDetermined
        }
    }
    
    private func checkNotificationPermission() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                switch settings.authorizationStatus {
                case .authorized, .provisional:
                    self.notificationStatus = .granted
                case .denied:
                    self.notificationStatus = .denied
                case .notDetermined:
                    self.notificationStatus = .notDetermined
                @unknown default:
                    self.notificationStatus = .notDetermined
                }
            }
        }
    }
    
    private func checkHealthKitPermission() {
        guard HKHealthStore.isHealthDataAvailable() else {
            healthKitStatus = .denied
            return
        }
        
        // Check if we have authorization for at least one type
        let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        let status = healthStore.authorizationStatus(for: stepType)
        
        switch status {
        case .sharingAuthorized:
            healthKitStatus = .granted
        case .sharingDenied:
            healthKitStatus = .denied
        case .notDetermined:
            healthKitStatus = .notDetermined
        @unknown default:
            healthKitStatus = .notDetermined
        }
    }
    
    func requestCameraPermission() async {
        let status = await AVCaptureDevice.requestAccess(for: .video)
        cameraStatus = status ? .granted : .denied
    }
    
    func requestNotificationPermission() async {
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
            notificationStatus = granted ? .granted : .denied
        } catch {
            notificationStatus = .denied
        }
    }
    
    func requestHealthKitPermission() async {
        guard HKHealthStore.isHealthDataAvailable() else {
            healthKitStatus = .denied
            return
        }
        
        let typesToRead: Set<HKObjectType> = [
            HKQuantityType.quantityType(forIdentifier: .stepCount)!,
            HKQuantityType.quantityType(forIdentifier: .heartRate)!,
            HKCategoryType.categoryType(forIdentifier: .sleepAnalysis)!,
            HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!
        ]
        
        do {
            try await healthStore.requestAuthorization(toShare: [], read: typesToRead)
            healthKitStatus = .granted
        } catch {
            healthKitStatus = .denied
        }
    }
}

#Preview {
    PermissionsView(onComplete: {})
}

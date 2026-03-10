import Foundation
import shared

/// Helper to convert Kotlin SessionConfig to Swift ChatSessionConfig
extension ChatSessionConfig {
    /// Create a ChatSessionConfig from a Kotlin SessionConfig name
    static func from(configName: String) -> ChatSessionConfig? {
        // Access Kotlin object directly - objects in Kotlin/Native are accessed as singletons
        guard let kotlinConfig = SessionConfigs.shared.getConfig(cap: configName) else {
            return nil
        }
        
        return ChatSessionConfig(
            cap: kotlinConfig.cap,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            collectData: kotlinConfig.collectData.map { $0 as String },
            collectMetrics: kotlinConfig.collectMetrics.map { $0 as String },
            initialMessage: kotlinConfig.initialMessage
        )
    }
    
    /// Predefined onboarding config
    static var onboarding: ChatSessionConfig {
        // Access Kotlin object properties directly
        let kotlinConfig = SessionConfigs.shared.ONBOARDING
        return ChatSessionConfig(
            cap: kotlinConfig.cap,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            collectData: kotlinConfig.collectData.map { $0 as String },
            collectMetrics: kotlinConfig.collectMetrics.map { $0 as String },
            initialMessage: kotlinConfig.initialMessage
        )
    }
    
    /// Predefined goal setting config
    static var goalSetting: ChatSessionConfig {
        // Access Kotlin object properties directly
        let kotlinConfig = SessionConfigs.shared.GOAL_SETTING
        return ChatSessionConfig(
            cap: kotlinConfig.cap,
            responsibilities: kotlinConfig.responsibilities.map { $0 as String },
            collectData: kotlinConfig.collectData.map { $0 as String },
            collectMetrics: kotlinConfig.collectMetrics.map { $0 as String },
            initialMessage: kotlinConfig.initialMessage
        )
    }
}

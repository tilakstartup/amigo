import Foundation
import shared

/// Wrapper to bridge Swift async functions to Kotlin suspend functions
class KotlinSuspendFunction0Wrapper: KotlinSuspendFunction0 {
    private let block: () async throws -> Any?
    
    init(block: @escaping () async throws -> Any?) {
        self.block = block
    }
    
    func invoke() async throws -> Any? {
        return try await block()
    }
}

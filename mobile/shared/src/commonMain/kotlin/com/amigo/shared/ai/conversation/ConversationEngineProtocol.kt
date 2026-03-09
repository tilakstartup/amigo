package com.amigo.shared.ai.conversation

import kotlinx.coroutines.flow.StateFlow

/**
 * Protocol that both conversation engines conform to
 * Allows AmigoConversationView to work with any implementation
 */
interface ConversationEngine {
    val conversationState: StateFlow<ConversationState>
    val messages: StateFlow<List<AmigoMessage>>
    val isProcessing: StateFlow<Boolean>
    
    suspend fun startConversation(): Result<Unit>
    suspend fun processUserResponse(response: String): Result<Unit>
    suspend fun processQuickReply(option: String): Result<Unit>
    
    fun getCollectedData(): Map<String, String>
    fun isComplete(): Boolean
}

/**
 * Adapter to make AmigoConversationEngine compatible with ConversationEngine interface
 */
class SessionEngineAdapter(
    private val engine: AmigoConversationEngine
) : ConversationEngine {
    override val conversationState: StateFlow<ConversationState> = engine.conversationState
    override val messages: StateFlow<List<AmigoMessage>> = engine.messages
    override val isProcessing: StateFlow<Boolean> = engine.isProcessing
    
    override suspend fun startConversation(): Result<Unit> = engine.startConversation()
    override suspend fun processUserResponse(response: String): Result<Unit> = engine.processUserResponse(response)
    override suspend fun processQuickReply(option: String): Result<Unit> = engine.processUserResponse(option)
    
    override fun getCollectedData(): Map<String, String> = engine.getCollectedData()
    override fun isComplete(): Boolean = (engine.conversationState.value as? ConversationState.Complete) != null
}

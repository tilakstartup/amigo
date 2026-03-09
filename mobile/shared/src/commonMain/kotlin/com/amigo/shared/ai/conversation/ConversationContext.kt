package com.amigo.shared.ai.conversation

import com.amigo.shared.ai.tools.AmigoToolRegistry

/**
 * Context for a specific type of conversation (e.g., onboarding, goal setting, etc.)
 * This defines the behavior and personality for that specific conversation flow
 */
interface ConversationContext {
    /**
     * Unique identifier for this conversation context
     */
    val contextId: String
    
    /**
     * Display name for this conversation context
     */
    val contextName: String
    
    /**
     * Build system prompt for AI model that includes field definitions and tool metadata
     */
    fun buildSystemPrompt(session: ConversationSession): String
    
    /**
     * Build user prompt based on session state
     */
    fun buildUserPrompt(
        userInput: String,
        session: ConversationSession
    ): String
    
    /**
     * List of fields that need to be collected from user
     */
    fun getRequiredFields(): List<CollectableField>
    
    /**
     * List of fields that can be calculated using tools
     */
    fun getCalculatedFields(): List<CollectableField>
    
    /**
     * Get metadata for all available tools in this context
     */
    fun getToolMetadata(): List<ToolMetadata>
    
    /**
     * Get the tool registry for this context
     */
    fun getToolRegistry(): AmigoToolRegistry?
    
    /**
     * Get next field to collect based on current session state
     */
    fun getNextFieldToCollect(session: ConversationSession): CollectableField?
    
    /**
     * Parse and validate user input for a specific field
     */
    fun parseUserInput(field: CollectableField, userInput: String): String?
    
    /**
     * Determine if all required data is collected and conversation is complete
     */
    fun isComplete(session: ConversationSession): Boolean
    
    /**
     * Get tool calls needed to calculate missing fields
     */
    fun getToolsToExecute(session: ConversationSession): List<ToolCall>
    
    /**
     * Merge tool execution results into session
     */
    fun mergeToolResults(
        session: ConversationSession,
        toolName: String,
        result: Map<String, Any>
    ): ConversationSession
    
    /**
     * Get initial session with seeded data (user profile, etc.)
     */
    fun initializeSession(): ConversationSession
    
    /**
     * Get the model to use for this conversation
     */
    fun getModelId(): String = "amazon.nova-micro-v1:0"
    
    /**
     * Get temperature for AI responses
     */
    fun getTemperature(): Double = 0.1
    
    /**
     * Get max tokens for AI responses
     */
    fun getMaxTokens(): Int = 800
}

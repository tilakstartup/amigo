package com.amigo.shared.ai.conversation

import kotlinx.serialization.Serializable

/**
 * Base data structures for Amigo conversations
 */

/**
 * Message in a conversation
 */
@Serializable
data class AmigoMessage(
    val id: String,
    val text: String,
    val isFromAmigo: Boolean,
    val timestamp: Long,
    val replyType: ReplyType = ReplyType.TEXT,
    val replies: List<String>? = null,
    val isDisabled: Boolean = false,
    val feature: FeatureCard? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Type of reply expected from user
 */
@Serializable
enum class ReplyType {
    TEXT,           // Free text input
    QUICK_PILLS,    // Button/chip selection (2-6 options)
    LIST,           // Dropdown list (many options)
    YES_NO,         // Yes/No buttons
    DATE,           // Date picker
    NUMBER,         // Number input
    SINGLE_CHOICE   // Single choice from multiple options
}

/**
 * Feature card to show in conversation
 */
@Serializable
data class FeatureCard(
    val name: String,
    val description: String,
    val icon: String
)

/**
 * AI response structure (standardized across all contexts)
 */
@Serializable
data class AmigoResponse(
    val message: String = "",
    val replyType: ReplyType = ReplyType.TEXT,
    val replies: List<String>? = null,
    val featureIntro: FeatureIntro? = null,
    val data: Map<String, String>? = null,
    val nextAction: String? = null,
    val toolCalls: List<ToolCall>? = null
)

/**
 * Feature introduction in conversation
 */
@Serializable
data class FeatureIntro(
    val pretext: String,
    val feature: FeatureCard,
    val followUpMessage: String,
    val followUpReplyType: ReplyType,
    val followUpReplies: List<String>? = null
)

/**
 * Tool call request from AI
 */
@Serializable
data class ToolCall(
    val toolName: String,
    val parameters: Map<String, String>
)

/**
 * Conversation state
 */
sealed class ConversationState {
    object Idle : ConversationState()
    object Active : ConversationState()
    object Complete : ConversationState()
    data class Error(val message: String) : ConversationState()
}

/**
 * Input validation for user responses
 */
data class InputValidation(
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val required: Boolean = true,
    val errorMessage: String? = null
)

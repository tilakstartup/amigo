package com.amigo.shared.ai.models

import kotlinx.serialization.Serializable

/**
 * Conversation message model for agent conversations
 */
@Serializable
data class ConversationMessage(
    val id: String,
    val text: String,
    val isFromAmigo: Boolean,
    val timestamp: Long,
    val replyType: String, // REQUIRED: "quick_pills", "list", "yes_no", "text", "date", "weight", "none"
    val replies: List<String>? = null,
    val renderType: String? = null,
    val renderItems: List<String>? = null,
    val feature: FeatureIntro? = null,
    val isFeatureIntro: Boolean = false, // True if this is just a feature card
    val delayAfterPrevious: Long = 0, // Milliseconds to wait before showing this message
    val isDisabled: Boolean = false // True if user has already responded to this message
)

/**
 * Feature introduction card
 */
@Serializable
data class FeatureIntro(
    val name: String,
    val description: String,
    val icon: String
)

package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ConversationMessage(
    val id: String,
    val userId: String,
    val role: MessageRole,
    val message: String,
    val feedbackRating: Int? = null,  // 1-5
    val createdAt: String
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT
}

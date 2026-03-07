package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class FastingSession(
    val id: String,
    val userId: String,
    val startTime: String,
    val endTime: String? = null,
    val targetDurationHours: Double,
    val protocol: FastingProtocol? = null,
    val status: FastingStatus = FastingStatus.ACTIVE,
    val createdAt: String
)

@Serializable
enum class FastingProtocol {
    SIXTEEN_EIGHT,  // 16:8
    EIGHTEEN_SIX,   // 18:6
    TWENTY_FOUR,    // 20:4
    CUSTOM
}

@Serializable
enum class FastingStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class WaterLog(
    val id: String,
    val userId: String,
    val volumeMl: Double,
    val loggedAt: String,
    val createdAt: String
)

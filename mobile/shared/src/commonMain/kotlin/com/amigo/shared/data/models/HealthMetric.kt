package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthMetric(
    val id: String,
    val userId: String,
    val metricType: MetricType,
    val value: Double,
    val unit: String,
    val sourcePlatform: SourcePlatform,
    val timestamp: String,
    val metadata: Map<String, String>? = null,
    val createdAt: String
)

@Serializable
enum class MetricType {
    STEPS,
    HEART_RATE,
    SLEEP,
    EXERCISE,
    WEIGHT,
    BLOOD_PRESSURE
}

@Serializable
enum class SourcePlatform {
    FITBIT,
    GARMIN,
    APPLE_HEALTH,
    GOOGLE_HEALTH_CONNECT,
    MANUAL
}

package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val unitPreference: UnitPreference = UnitPreference.METRIC,
    val language: String = "en",
    val theme: Theme = Theme.AUTO,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class UnitPreference {
    METRIC,
    IMPERIAL
}

@Serializable
enum class Theme {
    LIGHT,
    DARK,
    AUTO
}

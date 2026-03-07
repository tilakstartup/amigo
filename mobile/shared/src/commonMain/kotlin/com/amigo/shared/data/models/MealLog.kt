package com.amigo.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
data class MealLog(
    val id: String,
    val userId: String,
    val mealTime: String,
    val inputMethod: InputMethod,
    val photoUrl: String? = null,
    val foodItems: List<FoodItem>,
    val totalCalories: Double? = null,
    val totalProteinG: Double? = null,
    val totalCarbsG: Double? = null,
    val totalFatG: Double? = null,
    val dataSource: DataSource? = null,
    val barcode: String? = null,
    val createdAt: String
)

@Serializable
enum class InputMethod {
    IMAGE,
    VOICE,
    TEXT,
    BARCODE,
    MANUAL
}

@Serializable
enum class DataSource {
    AI_ESTIMATE,
    USDA,
    BARCODE,
    CUSTOM,
    MANUAL
}

@Serializable
data class FoodItem(
    val name: String,
    val quantity: String,
    val nutritionalData: NutritionalData
)

@Serializable
data class NutritionalData(
    val calories: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null
)

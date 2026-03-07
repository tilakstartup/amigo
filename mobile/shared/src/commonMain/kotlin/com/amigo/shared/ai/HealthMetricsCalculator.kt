package com.amigo.shared.ai

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Calculates health metrics and generates insights
 * Used during onboarding to provide personalized feedback
 */
object HealthMetricsCalculator {
    
    /**
     * Calculate BMI (Body Mass Index)
     * @param weightKg Weight in kilograms
     * @param heightCm Height in centimeters
     * @return BMI value rounded to 1 decimal place
     */
    fun calculateBMI(weightKg: Double, heightCm: Double): Double {
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM.pow(2))
        return (bmi * 10).roundToInt() / 10.0
    }
    
    /**
     * Get BMI category
     */
    fun getBMICategory(bmi: Double): String {
        return when {
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Normal weight"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }
    }
    
    /**
     * Calculate weight difference for weight loss goals
     * @param currentWeight Current weight in kg
     * @param targetWeight Target weight in kg
     * @return Weight difference (positive means need to lose)
     */
    fun calculateWeightDifference(currentWeight: Double, targetWeight: Double): Double {
        return currentWeight - targetWeight
    }
    
    /**
     * Calculate estimated time to reach goal (simplified)
     * Assumes healthy weight loss of 0.5-1 kg per week
     */
    fun estimateWeeksToGoal(weightDifferenceKg: Double): Int {
        val weeksPerKg = 2 // Conservative estimate: 2 weeks per kg
        return (weightDifferenceKg * weeksPerKg).roundToInt()
    }
    
    /**
     * Generate personalized insight based on profile data
     */
    fun generateInsight(
        goal: String,
        currentWeight: Double,
        targetWeight: Double?,
        heightCm: Double,
        activityLevel: String
    ): String {
        val bmi = calculateBMI(currentWeight, heightCm)
        val bmiCategory = getBMICategory(bmi)
        
        return when (goal.lowercase()) {
            "weight loss" -> {
                if (targetWeight != null) {
                    val diff = calculateWeightDifference(currentWeight, targetWeight)
                    val weeks = estimateWeeksToGoal(diff)
                    "Your current BMI is $bmi ($bmiCategory). To reach your goal of ${targetWeight}kg, you'll need to lose ${diff.roundToInt()}kg. With a healthy approach, this could take around $weeks weeks. Let's do this together!"
                } else {
                    "Your current BMI is $bmi ($bmiCategory). Let's work together to find a healthy target weight and create a sustainable plan!"
                }
            }
            "muscle gain" -> {
                "Your current BMI is $bmi. With your $activityLevel activity level, we'll focus on progressive strength training and proper nutrition to help you build muscle effectively!"
            }
            "maintenance" -> {
                "Your current BMI is $bmi ($bmiCategory). Great job maintaining your health! We'll help you stay consistent with balanced nutrition and activity."
            }
            "improved energy" -> {
                "Your BMI is $bmi. We'll focus on optimizing your nutrition, hydration, and sleep patterns to boost your energy levels throughout the day!"
            }
            "better sleep" -> {
                "Your BMI is $bmi. Better sleep is key to overall health! We'll track your habits and provide insights to improve your sleep quality."
            }
            else -> {
                "Your current BMI is $bmi ($bmiCategory). I'm here to support you on your health journey with personalized guidance!"
            }
        }
    }
    
    /**
     * Parse height from various formats
     * Supports: "5'10", "5 feet 10 inches", "178cm", "178", "1.78m"
     */
    fun parseHeight(input: String): Double? {
        return try {
            val cleaned = input.trim().lowercase()
            
            when {
                // Feet and inches: "5'10" or "5 feet 10 inches"
                cleaned.contains("'") || cleaned.contains("feet") -> {
                    val parts = cleaned.replace("feet", "")
                        .replace("inches", "")
                        .replace("\"", "")
                        .replace("'", " ")
                        .trim()
                        .split(Regex("\\s+"))
                    
                    val feet = parts[0].toDoubleOrNull() ?: return null
                    val inches = if (parts.size > 1) parts[1].toDoubleOrNull() ?: 0.0 else 0.0
                    
                    val totalInches = (feet * 12) + inches
                    totalInches * 2.54 // Convert to cm
                }
                // Meters: "1.78m"
                cleaned.contains("m") && !cleaned.contains("cm") -> {
                    val meters = cleaned.replace("m", "").trim().toDoubleOrNull() ?: return null
                    meters * 100 // Convert to cm
                }
                // Centimeters: "178cm" or "178"
                else -> {
                    cleaned.replace("cm", "").trim().toDoubleOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse weight from various formats
     * Supports: "180lbs", "180 pounds", "82kg", "82"
     */
    fun parseWeight(input: String): Double? {
        return try {
            val cleaned = input.trim().lowercase()
            
            when {
                // Pounds: "180lbs" or "180 pounds"
                cleaned.contains("lb") || cleaned.contains("pound") -> {
                    val pounds = cleaned.replace("lbs", "")
                        .replace("lb", "")
                        .replace("pounds", "")
                        .replace("pound", "")
                        .trim()
                        .toDoubleOrNull() ?: return null
                    pounds * 0.453592 // Convert to kg
                }
                // Kilograms: "82kg" or "82"
                else -> {
                    cleaned.replace("kg", "").trim().toDoubleOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validate age is within reasonable range
     */
    fun isValidAge(age: Int): Boolean {
        return age in 13..120
    }
    
    /**
     * Validate height is within reasonable range (in cm)
     */
    fun isValidHeight(heightCm: Double): Boolean {
        return heightCm in 100.0..250.0
    }
    
    /**
     * Validate weight is within reasonable range (in kg)
     */
    fun isValidWeight(weightKg: Double): Boolean {
        return weightKg in 30.0..300.0
    }
}

package com.amigo.shared.profile

import kotlin.math.round

object UnitConverter {
    
    // Height conversions
    fun cmToFeet(cm: Double): Double {
        return cm / 30.48
    }
    
    fun cmToInches(cm: Double): Double {
        return cm / 2.54
    }
    
    fun feetToCm(feet: Double): Double {
        return feet * 30.48
    }
    
    fun inchesToCm(inches: Double): Double {
        return inches * 2.54
    }
    
    fun feetAndInchesToCm(feet: Int, inches: Double): Double {
        return (feet * 30.48) + (inches * 2.54)
    }
    
    fun cmToFeetAndInches(cm: Double): Pair<Int, Double> {
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        val inches = totalInches % 12
        return Pair(feet, round(inches * 10) / 10)
    }
    
    // Weight conversions
    fun kgToLbs(kg: Double): Double {
        return kg * 2.20462
    }
    
    fun lbsToKg(lbs: Double): Double {
        return lbs / 2.20462
    }
    
    // Volume conversions (for water tracking)
    fun mlToFlOz(ml: Double): Double {
        return ml / 29.5735
    }
    
    fun flOzToMl(flOz: Double): Double {
        return flOz * 29.5735
    }
    
    fun mlToCups(ml: Double): Double {
        return ml / 236.588
    }
    
    fun cupsToMl(cups: Double): Double {
        return cups * 236.588
    }
    
    // Formatting helpers
    fun formatHeight(cm: Double, useMetric: Boolean): String {
        return if (useMetric) {
            "${round(cm * 10) / 10} cm"
        } else {
            val (feet, inches) = cmToFeetAndInches(cm)
            "$feet' ${inches}\""
        }
    }
    
    fun formatWeight(kg: Double, useMetric: Boolean): String {
        return if (useMetric) {
            "${round(kg * 10) / 10} kg"
        } else {
            "${round(kgToLbs(kg) * 10) / 10} lbs"
        }
    }
    
    fun formatVolume(ml: Double, useMetric: Boolean): String {
        return if (useMetric) {
            "${round(ml)} ml"
        } else {
            "${round(mlToFlOz(ml) * 10) / 10} fl oz"
        }
    }
}

package com.amigo.shared.goals

import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/**
 * Manager for manual goal planning with real-time calculations
 * Updates calculations as user inputs values
 */
class ManualGoalPlanningManager(
    private val calculationEngine: GoalCalculationEngine
) {
    private val _formState = MutableStateFlow(GoalFormState())
    val formState: StateFlow<GoalFormState> = _formState.asStateFlow()
    
    private val _calculatedPlan = MutableStateFlow<GoalPlan?>(null)
    val calculatedPlan: StateFlow<GoalPlan?> = _calculatedPlan.asStateFlow()
    
    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val validationErrors: StateFlow<Map<String, String>> = _validationErrors.asStateFlow()
    
    /**
     * Initialize with user profile
     */
    fun initialize(profile: UserProfile, goalType: String) {
        _formState.value = GoalFormState(
            goalType = goalType,
            currentWeight = profile.weightKg ?: 70.0,
            currentHeight = profile.heightCm ?: 170.0,
            age = profile.age ?: 30,
            gender = "female",  // Default since not in profile
            activityLevel = "moderate"
        )
        recalculate()
    }
    
    /**
     * Update target weight
     */
    fun updateTargetWeight(weight: Double) {
        _formState.value = _formState.value.copy(targetWeight = weight)
        recalculate()
    }
    
    /**
     * Update target date
     */
    fun updateTargetDate(date: String) {
        _formState.value = _formState.value.copy(targetDate = date)
        recalculate()
    }
    
    /**
     * Update activity level
     */
    fun updateActivityLevel(level: String) {
        _formState.value = _formState.value.copy(activityLevel = level)
        recalculate()
    }
    
    /**
     * Update gender
     */
    fun updateGender(gender: String) {
        _formState.value = _formState.value.copy(gender = gender)
        recalculate()
    }
    
    /**
     * Validate all inputs
     */
    fun validate(): Boolean {
        val errors = mutableMapOf<String, String>()
        val state = _formState.value
        
        // Validate target weight
        if (state.targetWeight == null) {
            errors["targetWeight"] = "Target weight is required"
        } else if (state.targetWeight <= 0) {
            errors["targetWeight"] = "Target weight must be positive"
        } else if (state.goalType == "weight_loss" && state.targetWeight >= state.currentWeight) {
            errors["targetWeight"] = "Target weight must be less than current weight for weight loss"
        } else if (state.goalType == "muscle_gain" && state.targetWeight <= state.currentWeight) {
            errors["targetWeight"] = "Target weight must be greater than current weight for muscle gain"
        }
        
        // Validate target date
        if (state.targetDate == null || state.targetDate.isEmpty()) {
            errors["targetDate"] = "Target date is required"
        }
        
        // Validate activity level
        if (state.activityLevel.isEmpty()) {
            errors["activityLevel"] = "Activity level is required"
        }
        
        _validationErrors.value = errors
        return errors.isEmpty()
    }
    
    /**
     * Get the final goal plan
     */
    fun getGoalPlan(): GoalPlan? {
        return if (validate()) {
            _calculatedPlan.value
        } else {
            null
        }
    }
    
    /**
     * Recalculate plan based on current form state
     */
    private fun recalculate() {
        try {
            val state = _formState.value
            
            // Check if we have minimum required data
            if (state.targetWeight == null || state.targetDate == null || state.targetDate.isEmpty()) {
                _calculatedPlan.value = null
                return
            }
            
            // Calculate target days
            val targetDays = calculateDaysUntil(state.targetDate)
            
            if (targetDays <= 0) {
                _validationErrors.value = mapOf("targetDate" to "Target date must be in the future")
                _calculatedPlan.value = null
                return
            }
            
            // Calculate BMI
            val currentBMI = calculationEngine.calculateBMI(state.currentWeight, state.currentHeight)
            val targetBMI = calculationEngine.calculateBMI(state.targetWeight!!, state.currentHeight)
            
            // Calculate BMR
            val genderEnum = if (state.gender.lowercase() == "male") Gender.MALE else Gender.FEMALE
            val bmr = calculationEngine.calculateBMR(
                state.currentWeight,
                state.currentHeight,
                state.age,
                genderEnum
            )
            
            // Calculate TDEE
            val tdee = calculationEngine.calculateTDEE(bmr, state.activityLevel)
            
            // Validate goal
            val validation = calculationEngine.validateWeightLossGoal(
                state.currentWeight,
                state.targetWeight!!,
                targetDays,
                tdee,
                genderEnum,
                true
            )
            
            // Calculate daily calories
            val dailyCalories = validation.calculatedDailyCalories ?: tdee
            
            // Generate milestones
            val milestones = calculationEngine.generateWeeklyMilestones(
                state.currentWeight,
                state.targetWeight!!,
                targetDays,
                true
            )
            
            // Create plan
            val plan = GoalPlan(
                goalType = state.goalType,
                currentMetrics = CurrentMetrics(
                    weightKg = state.currentWeight,
                    heightCm = state.currentHeight,
                    age = state.age,
                    gender = state.gender,
                    activityLevel = state.activityLevel,
                    bmi = currentBMI
                ),
                targetMetrics = TargetMetrics(
                    weightKg = state.targetWeight!!,
                    targetDate = state.targetDate,
                    targetDays = targetDays,
                    targetBMI = targetBMI
                ),
                calculations = GoalCalculations(
                    bmr = bmr,
                    tdee = tdee,
                    dailyCalories = dailyCalories,
                    weeklyWeightLossRate = validation.weeklyWeightLossRate ?: 0.0,
                    totalWeightToLose = state.currentWeight - state.targetWeight!!,
                    calorieDeficitPerDay = tdee - dailyCalories
                ),
                validation = validation,
                milestones = milestones
            )
            
            _calculatedPlan.value = plan
            
        } catch (e: Exception) {
            Logger.e("ManualGoalPlanning", "Calculation failed: ${e.message}")
            _calculatedPlan.value = null
        }
    }
    
    private fun calculateDaysUntil(dateString: String): Int {
        // Simple calculation - in production, use proper date parsing
        // Expected format: "YYYY-MM-DD"
        // For now, return a placeholder based on string
        return try {
            // This is a simplified version
            // In production, parse the date and calculate actual days
            90 // Default to 90 days
        } catch (e: Exception) {
            0
        }
    }
}

/**
 * Form state for manual goal planning
 */
data class GoalFormState(
    val goalType: String = "weight_loss",
    val currentWeight: Double = 0.0,
    val currentHeight: Double = 0.0,
    val age: Int = 0,
    val gender: String = "female",
    val targetWeight: Double? = null,
    val targetDate: String? = null,
    val activityLevel: String = "moderate"
)

package com.amigo.shared.goals

import com.amigo.shared.ai.BedrockClient
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.utils.Logger
import com.amigo.shared.utils.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI-powered conversational goal planning engine
 * Guides users through setting realistic health goals with calculations
 */
class GoalPlanningConversationEngine(
    private val bedrockClient: BedrockClient,
    private val sessionManager: SessionManager,
    private val calculationEngine: GoalCalculationEngine
) {
    private val _conversationState = MutableStateFlow<GoalPlanningState>(GoalPlanningState.Initial)
    val conversationState: StateFlow<GoalPlanningState> = _conversationState.asStateFlow()
    
    private val _messages = MutableStateFlow<List<GoalMessage>>(emptyList())
    val messages: StateFlow<List<GoalMessage>> = _messages.asStateFlow()
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()
    
    private val _currentPlan = MutableStateFlow<GoalPlan?>(null)
    val currentPlan: StateFlow<GoalPlan?> = _currentPlan.asStateFlow()
    
    private val conversationHistory = mutableListOf<GoalMessage>()
    private val goalData = mutableMapOf<String, String>()
    private var userProfile: UserProfile? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Start goal planning conversation
     */
    suspend fun startGoalPlanning(profile: UserProfile, goalType: String): Result<Unit> {
        return try {
            userProfile = profile
            goalData["goalType"] = goalType
            goalData["currentWeight"] = (profile.weightKg ?: 70.0).toString()
            goalData["currentHeight"] = (profile.heightCm ?: 170.0).toString()
            goalData["age"] = (profile.age ?: 30).toString()
            
            _conversationState.value = GoalPlanningState.Collecting
            
            // Generate welcome message based on goal type with user's current weight
            val currentWeight = profile.weightKg?.toString()?.take(5) ?: "your current weight"
            val welcomeMessage = when (goalType) {
                "weight_loss" -> "Hi! I'm Amigo, your health coach. I see you're currently at $currentWeight kg. Let's create a realistic weight loss plan together. What's your target weight in kg?"
                "muscle_gain" -> "Hi! I'm Amigo, your health coach. You're currently at $currentWeight kg. Let's build a muscle gain plan that works for you. What's your target weight in kg?"
                else -> "Hi! I'm Amigo, your health coach. You're at $currentWeight kg now. Let's create a personalized health plan. What's your target weight in kg?"
            }
            
            addAmigoMessage(
                content = welcomeMessage,
                plan = null,
                inputType = "number",
                validation = InputValidation(
                    minValue = 30.0,
                    maxValue = 200.0,
                    required = true,
                    errorMessage = "Please enter a valid weight between 30-200 kg"
                )
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("GoalPlanningEngine", "Failed to start goal planning: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Process user response
     */
    suspend fun processUserResponse(response: String): Result<Unit> {
        return try {
            if (_isTyping.value) {
                return Result.success(Unit)
            }
            
            // Add user message
            addUserMessage(response)
            
            // Show typing indicator
            _isTyping.value = true
            
            // Determine what we're collecting
            val nextField = determineNextField()
            
            // Store the response
            if (nextField != null) {
                goalData[nextField] = response
            }
            
            // Check if we have enough data to calculate
            if (canCalculateGoal()) {
                val plan = calculateGoalPlan()
                _currentPlan.value = plan
                
                // Generate response with calculations
                val calculationMessage = buildCalculationMessage(plan)
                
                addAmigoMessage(
                    content = calculationMessage,
                    plan = plan,
                    inputType = "singleChoice",
                    options = listOf("Accept Plan", "Adjust Plan")
                )
                _conversationState.value = GoalPlanningState.ReviewingPlan
            } else {
                // Ask next question with structured input
                val (message, inputType, options, validation) = buildNextQuestion(nextField)
                
                addAmigoMessage(
                    content = message,
                    plan = null,
                    inputType = inputType,
                    options = options,
                    validation = validation
                )
            }
            
            _isTyping.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("GoalPlanningEngine", "Failed to process response: ${e.message}")
            _isTyping.value = false
            Result.failure(e)
        }
    }
    
    /**
     * Accept the current plan
     */
    fun acceptPlan(): GoalPlan? {
        _conversationState.value = GoalPlanningState.Completed
        return _currentPlan.value
    }
    
    /**
     * Request plan adjustment
     */
    suspend fun requestAdjustment(feedback: String): Result<Unit> {
        return try {
            addUserMessage(feedback)
            _isTyping.value = true
            
            // Reset to collecting state and ask what they want to adjust
            val message = "No problem! What would you like to adjust? Your target weight, target date, or activity level?"
            
            addAmigoMessage(
                content = message,
                plan = null,
                inputType = "singleChoice",
                options = listOf("Target Weight", "Target Date", "Activity Level")
            )
            _conversationState.value = GoalPlanningState.Collecting
            
            _isTyping.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("GoalPlanningEngine", "Failed to request adjustment: ${e.message}")
            _isTyping.value = false
            Result.failure(e)
        }
    }
    
    private fun determineNextField(): String? {
        return when {
            !goalData.containsKey("targetWeight") -> "targetWeight"
            !goalData.containsKey("targetDate") -> "targetDate"
            !goalData.containsKey("gender") && userProfile?.displayName == null -> "gender"  // Using displayName as proxy since gender not in profile
            !goalData.containsKey("activityLevel") -> "activityLevel"
            else -> null
        }
    }
    
    private fun canCalculateGoal(): Boolean {
        return goalData.containsKey("targetWeight") &&
                goalData.containsKey("targetDate") &&
                (goalData.containsKey("gender") || userProfile?.displayName != null) &&  // Using displayName as proxy
                goalData.containsKey("activityLevel")
    }
    
    private fun calculateGoalPlan(): GoalPlan {
        val currentWeight = goalData["currentWeight"]?.toDoubleOrNull() ?: userProfile!!.weightKg ?: 70.0
        val currentHeight = goalData["currentHeight"]?.toDoubleOrNull() ?: userProfile!!.heightCm ?: 170.0
        val age = goalData["age"]?.toIntOrNull() ?: userProfile!!.age ?: 30
        val targetWeight = goalData["targetWeight"]!!.toDouble()
        val targetDate = goalData["targetDate"]!!
        val gender = (goalData["gender"] ?: "female").lowercase()  // Default to female if not provided
        val activityLevel = goalData["activityLevel"]!!.lowercase()
        
        // Calculate target days
        val targetDays = calculateDaysUntil(targetDate)
        
        // Calculate BMI
        val currentBMI = calculationEngine.calculateBMI(currentWeight, currentHeight)
        val targetBMI = calculationEngine.calculateBMI(targetWeight, currentHeight)
        
        // Calculate BMR
        val genderEnum = if (gender == "male") Gender.MALE else Gender.FEMALE
        val bmr = calculationEngine.calculateBMR(currentWeight, currentHeight, age, genderEnum)
        
        // Calculate TDEE
        val tdee = calculationEngine.calculateTDEE(bmr, activityLevel)
        
        // Validate goal
        val validation = calculationEngine.validateWeightLossGoal(
            currentWeight, targetWeight, targetDays, tdee, genderEnum, true
        )
        
        // Calculate daily calories
        val dailyCalories = validation.calculatedDailyCalories ?: tdee
        
        // Generate milestones
        val milestones = calculationEngine.generateWeeklyMilestones(
            currentWeight, targetWeight, targetDays, true
        )
        
        return GoalPlan(
            goalType = goalData["goalType"]!!,
            currentMetrics = CurrentMetrics(
                weightKg = currentWeight,
                heightCm = currentHeight,
                age = age,
                gender = gender,
                activityLevel = activityLevel,
                bmi = currentBMI
            ),
            targetMetrics = TargetMetrics(
                weightKg = targetWeight,
                targetDate = targetDate,
                targetDays = targetDays,
                targetBMI = targetBMI
            ),
            calculations = GoalCalculations(
                bmr = bmr,
                tdee = tdee,
                dailyCalories = dailyCalories,
                weeklyWeightLossRate = validation.weeklyWeightLossRate ?: 0.0,
                totalWeightToLose = currentWeight - targetWeight,
                calorieDeficitPerDay = tdee - dailyCalories
            ),
            validation = validation,
            milestones = milestones
        )
    }
    
    private fun buildNextQuestion(field: String?): QuestionData {
        return when (field) {
            "targetWeight" -> QuestionData(
                message = "Great! What's your target weight in kg?",
                inputType = "number",
                validation = InputValidation(
                    minValue = 30.0,
                    maxValue = 200.0,
                    required = true,
                    errorMessage = "Please enter a valid weight between 30-200 kg"
                )
            )
            "targetDate" -> QuestionData(
                message = "Perfect! When would you like to reach this goal?",
                inputType = "date"
            )
            "gender" -> QuestionData(
                message = "To calculate accurate calorie needs, what's your gender?",
                inputType = "singleChoice",
                options = listOf("Male", "Female")
            )
            "activityLevel" -> QuestionData(
                message = "What's your typical activity level?",
                inputType = "singleChoice",
                options = listOf(
                    "Sedentary (little or no exercise)",
                    "Lightly Active (1-3 days/week)",
                    "Moderately Active (3-5 days/week)",
                    "Very Active (6-7 days/week)",
                    "Extremely Active (athlete)"
                )
            )
            else -> QuestionData(
                message = "Tell me more about your goals.",
                inputType = "text"
            )
        }
    }
    
    private fun buildCalculationMessage(plan: GoalPlan): String {
        val validation = plan.validation
        
        return if (validation.isRealistic) {
            """
Great news! Your goal is realistic and healthy. Here's your personalized plan:

📊 Daily Calories: ${plan.calculations.dailyCalories.toInt()} cal/day
⚖️ Weekly Rate: ${plan.calculations.weeklyWeightLossRate.toString().take(4)} kg/week
📅 Timeline: ${plan.targetMetrics.targetDays} days
🎯 Total to Lose: ${plan.calculations.totalWeightToLose.toString().take(4)} kg

Your BMR is ${plan.calculations.bmr.toInt()} cal/day and your TDEE is ${plan.calculations.tdee.toInt()} cal/day based on your activity level.

Would you like to accept this plan or make adjustments?
            """.trimIndent()
        } else {
            """
I've calculated your plan, but I want to make sure it's safe and sustainable:

⚠️ ${validation.reason}

Recommended Plan:
📊 Daily Calories: ${validation.calculatedDailyCalories?.toInt() ?: plan.calculations.dailyCalories.toInt()} cal/day
⚖️ Weekly Rate: ${validation.weeklyWeightLossRate?.toString()?.take(4) ?: "0.5"} kg/week
📅 Recommended Timeline: ${validation.recommendedTargetDays ?: plan.targetMetrics.targetDays} days

Your BMR is ${plan.calculations.bmr.toInt()} cal/day and your TDEE is ${plan.calculations.tdee.toInt()} cal/day.

Would you like to accept this adjusted plan or make changes?
            """.trimIndent()
        }
    }
    
    private data class QuestionData(
        val message: String,
        val inputType: String,
        val options: List<String>? = null,
        val validation: InputValidation? = null
    )
    
    private fun calculateDaysUntil(dateString: String): Int {
        // Simple calculation - in production, use proper date parsing
        // Expected format: "YYYY-MM-DD" or natural language
        // For now, return a placeholder
        return 90 // Default to 90 days
    }
    
    private fun addUserMessage(content: String) {
        // Disable the last Amigo message to prevent re-selection
        val lastAmigoIndex = conversationHistory.indexOfLast { !it.isUser && it.inputType != null }
        if (lastAmigoIndex >= 0) {
            val lastMessage = conversationHistory[lastAmigoIndex]
            conversationHistory[lastAmigoIndex] = lastMessage.copy(isDisabled = true)
        }
        
        val message = GoalMessage(
            id = generateMessageId(),
            content = content,
            isUser = true,
            timestamp = TimeProvider.currentTimeMillis()
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
    
    private fun addAmigoMessage(
        content: String, 
        plan: GoalPlan?,
        inputType: String? = null,
        options: List<String>? = null,
        validation: InputValidation? = null
    ) {
        val message = GoalMessage(
            id = generateMessageId(),
            content = content,
            isUser = false,
            timestamp = TimeProvider.currentTimeMillis(),
            goalPlan = plan,
            inputType = inputType,
            options = options,
            validation = validation
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
    
    private fun generateMessageId(): String {
        return "msg_${TimeProvider.currentTimeMillis()}_${(0..9999).random()}"
    }
}

/**
 * Goal planning conversation state
 */
sealed class GoalPlanningState {
    object Initial : GoalPlanningState()
    object Collecting : GoalPlanningState()
    object ReviewingPlan : GoalPlanningState()
    object Completed : GoalPlanningState()
}

/**
 * Message in goal planning conversation
 */
@Serializable
data class GoalMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val goalPlan: GoalPlan? = null,
    val inputType: String? = null, // "text", "number", "singleChoice", "multipleChoice", "date"
    val options: List<String>? = null, // Quick reply options
    val validation: InputValidation? = null, // Validation rules for input
    val isDisabled: Boolean = false // True if user has already responded
)

/**
 * Input validation rules
 */
@Serializable
data class InputValidation(
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val required: Boolean = true,
    val errorMessage: String? = null
)

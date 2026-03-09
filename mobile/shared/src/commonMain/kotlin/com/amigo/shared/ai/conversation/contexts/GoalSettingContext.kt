package com.amigo.shared.ai.conversation.contexts

import com.amigo.shared.ai.conversation.*
import com.amigo.shared.ai.currentTimeMillis
import com.amigo.shared.ai.tools.AmigoToolRegistry
import com.amigo.shared.data.models.UserProfile
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Session-based goal setting conversation context
 * Deterministically collects required fields, then calculates derived fields, then completes
 */
class GoalSettingContext(
    private val userProfile: UserProfile,
    private val toolRegistry: AmigoToolRegistry
) : ConversationContext {
    
    override val contextId: String = "goal_setting"
    override val contextName: String = "Goal Setting"
    
    override fun getRequiredFields(): List<CollectableField> {
        return listOf(
            CollectableField(
                fieldName = "goalType",
                description = "Type of health goal",
                inputType = SessionAmigoResponse.InputType.QUICK_PILLS,
                options = listOf("Weight Loss", "Muscle Gain", "Maintenance", "Improved Energy", "Better Sleep"),
                priority = 10
            ),
            CollectableField(
                fieldName = "gender",
                description = "Your biological sex (for accurate calculations)",
                inputType = SessionAmigoResponse.InputType.QUICK_PILLS,
                options = listOf("Male", "Female"),
                priority = 20
            ),
            CollectableField(
                fieldName = "targetWeight",
                description = "Target weight in kilograms",
                inputType = SessionAmigoResponse.InputType.NUMBER,
                priority = 30
            ),
            CollectableField(
                fieldName = "activityLevel",
                description = "Typical daily activity level",
                inputType = SessionAmigoResponse.InputType.QUICK_PILLS,
                options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active"),
                priority = 40
            ),
            CollectableField(
                fieldName = "targetDate",
                description = "Target date to reach your goal",
                inputType = SessionAmigoResponse.InputType.DATE,
                priority = 50
            )
        )
    }
    
    override fun getCalculatedFields(): List<CollectableField> {
        return listOf(
            CollectableField(
                fieldName = "bmr",
                description = "Basal Metabolic Rate (calories burned at rest)",
                required = true,
                depends_on = listOf("weight", "height", "age", "gender"),
                priority = 60
            ),
            CollectableField(
                fieldName = "tdee",
                description = "Total Daily Energy Expenditure",
                required = true,
                depends_on = listOf("bmr", "activityLevel"),
                priority = 70
            ),
            CollectableField(
                fieldName = "dailyCalories",
                description = "Recommended daily calorie intake",
                required = true,
                depends_on = listOf("bmr", "tdee", "targetWeight"),
                priority = 80
            ),
            CollectableField(
                fieldName = "isRealistic",
                description = "Goal validation result",
                required = true,
                depends_on = listOf("currentWeight", "targetWeight", "targetDate", "tdee"),
                priority = 90
            )
        )
    }
    
    override fun getToolMetadata(): List<ToolMetadata> {
        return listOf(
            ToolMetadata(
                toolName = "calculate_bmr",
                description = "Calculate Basal Metabolic Rate from personal metrics",
                parameters = mapOf(
                    "weight_kg" to ToolParameterMetadata(
                        name = "weight_kg",
                        type = "number",
                        description = "Weight in kilograms",
                        required = true,
                        sourceField = "currentWeight"
                    ),
                    "height_cm" to ToolParameterMetadata(
                        name = "height_cm",
                        type = "number",
                        description = "Height in centimeters",
                        required = true,
                        sourceField = "currentHeight"
                    ),
                    "age" to ToolParameterMetadata(
                        name = "age",
                        type = "number",
                        description = "Age in years",
                        required = true,
                        sourceField = "age"
                    ),
                    "gender" to ToolParameterMetadata(
                        name = "gender",
                        type = "string",
                        description = "Gender (male or female)",
                        required = true,
                        sourceField = "gender"
                    )
                ),
                provides = listOf("bmr")
            ),
            ToolMetadata(
                toolName = "calculate_tdee",
                description = "Calculate Total Daily Energy Expenditure from BMR and activity level",
                parameters = mapOf(
                    "bmr" to ToolParameterMetadata(
                        name = "bmr",
                        type = "number",
                        description = "Basal Metabolic Rate",
                        required = true,
                        sourceField = "bmr"
                    ),
                    "activity_level" to ToolParameterMetadata(
                        name = "activity_level",
                        type = "string",
                        description = "Activity level",
                        required = true,
                        sourceField = "activityLevel"
                    )
                ),
                provides = listOf("tdee")
            ),
            ToolMetadata(
                toolName = "validate_goal",
                description = "Validate if goal is realistic and safe",
                parameters = mapOf(
                    "current_weight_kg" to ToolParameterMetadata(
                        name = "current_weight_kg",
                        type = "number",
                        description = "Current weight",
                        required = true,
                        sourceField = "currentWeight"
                    ),
                    "target_weight_kg" to ToolParameterMetadata(
                        name = "target_weight_kg",
                        type = "number",
                        description = "Target weight",
                        required = true,
                        sourceField = "targetWeight"
                    ),
                    "target_days" to ToolParameterMetadata(
                        name = "target_days",
                        type = "number",
                        description = "Days until target",
                        required = true,
                        sourceField = null // Calculated from targetDate
                    ),
                    "tdee" to ToolParameterMetadata(
                        name = "tdee",
                        type = "number",
                        description = "Total daily energy expenditure",
                        required = true,
                        sourceField = "tdee"
                    ),
                    "gender" to ToolParameterMetadata(
                        name = "gender",
                        type = "string",
                        description = "Gender",
                        required = true,
                        sourceField = "gender"
                    )
                ),
                provides = listOf("dailyCalories", "isRealistic")
            )
        )
    }
    
    override fun buildSystemPrompt(session: ConversationSession): String {
        val requiredFieldsText = buildString {
            appendLine("## User Data to Collect")
            getRequiredFields().forEach { field ->
                appendLine("- ${field.fieldName}: ${field.description}")
            }
        }
        
        val calculatedFieldsText = buildString {
            appendLine("## Calculated Fields (via tools)")
            getCalculatedFields().forEach { field ->
                appendLine("- ${field.fieldName}: ${field.description}")
                if (field.depends_on.isNotEmpty()) {
                    appendLine("  Depends on: ${field.depends_on.joinToString(", ")}")
                }
            }
        }
        
        val toolsText = buildString {
            appendLine("## Available Tools")
            getToolMetadata().forEach { tool ->
                appendLine("- ${tool.toolName}: ${tool.description}")
                appendLine("  Provides: ${tool.provides.joinToString(", ")}")
                appendLine("  Requires: ${tool.parameters.keys.joinToString(", ")}")
            }
        }
        
        return """
            You are Amigo, an AI health coach helping users set realistic health goals.
            
            Your STRICT Instructions:
            1. YOU WILL NEVER ask the same question twice
            2. YOU WILL NEVER ask the user for data we already have
            3. ONLY ask for fields listed below in "User Data to Collect"
            4. NEVER ask the user for calculated fields - those come from tools
            5. NEVER ask for calorie intake, maintenance calories, or daily targets
            6. Always be warm, supportive, and conversational - avoid robotic phrases like "let me think", "got it let me", etc.
            7. Ask ONE question at a time, naturally and authentically
            8. When acknowledging tool results, be direct and positive (e.g., "Your metabolic rate is...") rather than just saying you calculated something
            9. Return ONLY valid JSON, no markdown, no prose
            
            $requiredFieldsText
            
            $calculatedFieldsText
            
            $toolsText
            
            ## Handling Tool Results
            When you see tool execution results:
            - These show calculated fields like BMR, TDEE, goal validation, etc.
            - Acknowledge these calculations to the user in a friendly way
            - Use them to make informed decisions about next steps
            - Determine if all required data is now collected and calculated
            - If yes, set nextAction to "completed"
            - If more user input needed, ask for the next missing field
            
            ## Current Session State
            Already collected: ${session.collectedData.keys.joinToString(", ").ifBlank { "nothing yet" }}
            Still need to collect: ${getMissingFields(session).joinToString(", ").ifBlank { "all fields already collected" }}
            Calculated values: ${session.calculatedFields.keys.joinToString(", ").ifBlank { "none yet" }}
            
            ## Response Format (MUST be valid JSON only)
            {
              "message": "Your message to user",
              "takesInput": true/false,
              "inputType": "quick_pills" | "yes_no" | "dropdown" | "date" | "number" | "text" | "none",
              "options": ["option1", "option2"] or [],
              "toolInvocation": false,
              "toolInvocationDetails": null,
              "dataCollected": { collected fields from user response },
              "nextAction": "user_input" | "tool_invocation" | "completed"
            }
            
            CRITICAL: Always return valid JSON with all fields. No external text.
        """.trimIndent()
    }
    
    override fun buildUserPrompt(
        userInput: String,
        session: ConversationSession
    ): String {
        val nextField = getNextFieldToCollect(session)
        val nextFieldDesc = nextField?.let {
            "Next field to collect: ${it.fieldName}\nExpected input type: ${it.inputType}"
        } ?: "All required fields collected"
        
        return """
            User message: "$userInput"
            
            $nextFieldDesc
            
            Collected data so far:
            ${session.collectedData.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "None yet" }}
        """.trimIndent()
    }
    
    override fun getNextFieldToCollect(session: ConversationSession): CollectableField? {
        val required = getRequiredFields()
            .sortedBy { it.priority }
            .firstOrNull { field ->
                session.collectedData[field.fieldName].isNullOrBlank()
            }
        return required
    }
    
    override fun getToolRegistry(): AmigoToolRegistry {
        return toolRegistry
    }
    
    override fun parseUserInput(field: CollectableField, userInput: String): String? {
        if (userInput.isBlank()) return null
        
        return when (field.fieldName) {
            "goalType" -> normalizeGoalType(userInput)
            "gender" -> normalizeGender(userInput)
            "targetWeight" -> {
                userInput.replace("kg", "", ignoreCase = true).trim()
                    .toDoubleOrNull()?.toString()
            }
            "activityLevel" -> normalizeActivityLevel(userInput)
            "targetDate" -> {
                val isoPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
                if (isoPattern.matches(userInput.trim())) userInput.trim() else null
            }
            else -> userInput
        }
    }
    
    override fun isComplete(session: ConversationSession): Boolean {
        // All required fields collected
        val allRequired = getRequiredFields().all { field ->
            session.collectedData[field.fieldName]?.isNotBlank() == true
        }
        
        // All calculated fields available
        val allCalculated = getCalculatedFields().all { field ->
            session.calculatedFields[field.fieldName]?.isNotBlank() == true
        }
        
        return allRequired && allCalculated
    }
    
    override fun getToolsToExecute(session: ConversationSession): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val metadata = getToolMetadata()
        val executedTools = session.toolHistory.map { it.toolName }.toSet()
        
        // Try to execute tools in order if dependencies are met
        for (toolMeta in metadata) {
            if (executedTools.contains(toolMeta.toolName)) {
                continue // Already executed
            }
            
            // Check if all parameters can be resolved
            val params = mutableMapOf<String, String>()
            var canExecute = true
            
            for ((paramName, paramMeta) in toolMeta.parameters) {
                val value = when {
                    paramMeta.sourceField != null -> {
                        // Try to get from collected or calculated
                        session.collectedData[paramMeta.sourceField]
                            ?: session.calculatedFields[paramMeta.sourceField]
                            ?: if (paramName == "target_days") {
                                calculateTargetDays(session.collectedData["targetDate"])?.toString()
                            } else {
                                null
                            }
                    }
                    else -> null
                }
                
                if (paramMeta.required && value.isNullOrBlank()) {
                    canExecute = false
                    break
                }
                
                if (!value.isNullOrBlank()) {
                    params[paramName] = value
                }
            }
            
            if (canExecute && params.isNotEmpty()) {
                calls.add(ToolCall(toolMeta.toolName, params))
            }
        }
        
        return calls
    }
    
    override fun mergeToolResults(
        session: ConversationSession,
        toolName: String,
        result: Map<String, Any>
    ): ConversationSession {
        val metadata = getToolMetadata().find { it.toolName == toolName } ?: return session
        val newCalculatedFields = session.calculatedFields.toMutableMap()
        
        metadata.provides.forEach { fieldName ->
            result[fieldName]?.toString()?.let { value ->
                newCalculatedFields[fieldName] = value
            }
        }
        
        val newHistory = session.toolHistory + ToolExecution(
            toolName,
            session.collectedData,
            result,
            currentTimeMillis()
        )
        
        return session.copy(
            calculatedFields = newCalculatedFields,
            toolHistory = newHistory
        )
    }
    
    override fun initializeSession(): ConversationSession {
        val initial = mutableMapOf<String, String>()
        
        // Pre-fill from user profile
        userProfile.weightKg?.let { initial["currentWeight"] = it.toString() }
        userProfile.heightCm?.let { initial["currentHeight"] = it.toString() }
        userProfile.age?.let { initial["age"] = it.toString() }
        
        return ConversationSession(
            sessionId = "goal_${currentTimeMillis()}",
            contextId = contextId,
            collectedData = initial,
            pendingFields = getRequiredFields().map { it.fieldName }
        )
    }
    
    private fun getMissingFields(session: ConversationSession): List<String> {
        return getRequiredFields()
            .filter { session.collectedData[it.fieldName].isNullOrBlank() }
            .map { it.fieldName }
    }
    
    private fun normalizeGoalType(input: String): String? {
        return when (input.lowercase().trim()) {
            "weight loss", "lose weight" -> "WEIGHT_LOSS"
            "muscle gain", "gain muscle", "build muscle" -> "MUSCLE_GAIN"
            "maintenance", "maintain" -> "MAINTENANCE"
            "improved energy", "energy", "more energy" -> "IMPROVED_ENERGY"
            "better sleep", "sleep improvement" -> "BETTER_SLEEP"
            else -> null
        }
    }
    
    private fun normalizeGender(input: String): String? {
        return when (input.lowercase().trim()) {
            "male", "m", "man" -> "male"
            "female", "f", "woman" -> "female"
            else -> null
        }
    }
    
    private fun normalizeActivityLevel(input: String): String? {
        return when (input.lowercase().trim().replace(" ", "_")) {
            "sedentary" -> "sedentary"
            "lightly_active", "lightly active" -> "lightly_active"
            "moderately_active", "moderately active" -> "moderately_active"
            "very_active", "very active", "active" -> "very_active"
            "extremely_active" -> "extremely_active"
            else -> null
        }
    }
    
    private fun calculateTargetDays(targetDate: String?): Int? {
        if (targetDate.isNullOrBlank()) return null
        return try {
            val target = LocalDate.parse(targetDate)
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val days = target.toEpochDays() - today.toEpochDays()
            if (days > 0) days else null
        } catch (_: Exception) {
            null
        }
    }
}

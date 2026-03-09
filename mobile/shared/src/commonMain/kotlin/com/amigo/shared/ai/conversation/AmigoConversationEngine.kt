package com.amigo.shared.ai.conversation

import com.amigo.shared.ai.BedrockClient
import com.amigo.shared.ai.currentTimeMillis
import com.amigo.shared.ai.tools.ParameterType
import com.amigo.shared.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Session-based conversation engine following deterministic flow
 */
class AmigoConversationEngine(
    private val bedrockClient: BedrockClient,
    private val context: ConversationContext
) {
    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()
    
    private val _messages = MutableStateFlow<List<AmigoMessage>>(emptyList())
    val messages: StateFlow<List<AmigoMessage>> = _messages.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()
    
    private var currentSession: ConversationSession = ConversationSession(
        sessionId = "",
        contextId = ""
    )
    
    private val conversationHistory = mutableListOf<AmigoMessage>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun startConversation(): Result<Unit> {
        return try {
            _conversationState.value = ConversationState.Active
            _isProcessing.value = true
            conversationHistory.clear()
            
            // Initialize session with user profile data
            currentSession = context.initializeSession()
            
            // Get welcome message
            val response = generateWelcomeMessage()
            handleResponse(response)
            
            _isProcessing.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SessionAmigoEngine", "Failed to start: ${e.message}")
            _conversationState.value = ConversationState.Error(e.message ?: "Unknown error")
            _isProcessing.value = false
            Result.failure(e)
        }
    }
    
    suspend fun processUserResponse(userInput: String): Result<Unit> {
        return try {
            if (_isProcessing.value) {
                Logger.w("SessionAmigoEngine", "Already processing")
                return Result.success(Unit)
            }
            
            _isProcessing.value = true
            
            // Disable previous user input options
            disablePreviousInputOptions()
            
            // Add user message
            addUserMessage(userInput)
            
            // Try to parse input for expected field
            val nextField = context.getNextFieldToCollect(currentSession)
            if (nextField != null) {
                context.parseUserInput(nextField, userInput)?.let { parsed ->
                    currentSession = currentSession.copy(
                        collectedData = currentSession.collectedData + (nextField.fieldName to parsed)
                    )
                    Logger.d("SessionAmigoEngine", "Parsed field ${nextField.fieldName} = $parsed")
                }
            }
            
            // Determine next action
            val nextResponse = determineNextAction()
            handleResponse(nextResponse)
            
            // Check if complete
            if (context.isComplete(currentSession)) {
                _conversationState.value = ConversationState.Complete
                Logger.i("SessionAmigoEngine", "Conversation complete - all fields collected and calculated")
            }
            
            _isProcessing.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SessionAmigoEngine", "Failed to process: ${e.message}")
            _isProcessing.value = false
            Result.failure(e)
        }
    }
    
    private suspend fun determineNextAction(): SessionAmigoResponse {
        // If still missing required fields, ask for the next one
        val nextField = context.getNextFieldToCollect(currentSession)
        if (nextField != null) {
            return generateFieldQuestion(nextField)
        }
        
        // If all required fields collected but missing calculated fields, invoke tools silently
        val toolsToRun = context.getToolsToExecute(currentSession)
        if (toolsToRun.isNotEmpty()) {
            Logger.i("SessionAmigoEngine", "Executing tools silently: ${toolsToRun.joinToString(", ")}")
            
            // Execute tools and merge results - NO MESSAGE TO USER
            val toolResults = executeTools(toolsToRun)
            for ((toolName, result) in toolResults) {
                currentSession = context.mergeToolResults(currentSession, toolName, result)
                Logger.d("SessionAmigoEngine", "Merged $toolName results into session")
            }
            
            // Now check again - we may have calculated fields or we're complete
            val nextFieldAfterTools = context.getNextFieldToCollect(currentSession)
            if (nextFieldAfterTools != null) {
                // Tools calculated something, now ask next required field (naturally, with calculated context)
                return generateFieldQuestion(nextFieldAfterTools)
            }
        }
        
        // If everything collected and calculated, show summary and confirm
        if (context.isComplete(currentSession)) {
            return SessionAmigoResponse(
                message = buildCompletionSummary(),
                takesInput = true,
                inputType = SessionAmigoResponse.InputType.YES_NO,
                options = listOf("Confirm", "Edit"),
                nextAction = SessionAmigoResponse.NextAction.CONFIRMATION,
                dataCollected = currentSession.collectedData
            )
        }
        
        // Should not reach here - there's always something to collect or we're complete
        Logger.w("SessionAmigoEngine", "Unexpected state - no missing fields but not complete")
        return SessionAmigoResponse(
            message = "Let's continue setting up your health plan.",
            takesInput = false,
            nextAction = SessionAmigoResponse.NextAction.USER_INPUT
        )
    }
    
    private fun buildCompletionSummary(): String {
        val goalType = currentSession.collectedData["goalType"] ?: "your goal"
        val targetWeight = currentSession.collectedData["targetWeight"] ?: ""
        val targetDate = currentSession.collectedData["targetDate"] ?: ""
        val bmr = currentSession.calculatedFields["bmr"] ?: ""
        val tdee = currentSession.calculatedFields["tdee"] ?: ""
        
        return """
Great! Here's your personalized health plan:
• Goal: $goalType
• Target weight: $targetWeight kg
• Target date: $targetDate
• Your daily calorie needs: ${tdee.toString().take(5)} kcal

Does this look good?
        """.trimIndent()
    }
    
    private suspend fun generateWelcomeMessage(): SessionAmigoResponse {
        val response = callAI("START_CONVERSATION")
        Logger.d("SessionAmigoEngine", "Welcome response: $response")
        return response
    }
    
    private fun generateFieldQuestion(field: CollectableField): SessionAmigoResponse {
        val message = when (field.fieldName) {
            "goalType" -> "What kind of health goal would you like to set?"
            "gender" -> "For accurate calculations, what is your biological sex?"
            "targetWeight" -> "What is your target weight (in kg)?"
            "activityLevel" -> "How active are you on most days?"
            "targetDate" -> "By when would you like to reach this goal?"
            else -> "What is your ${field.fieldName}?"
        }
        
        return SessionAmigoResponse(
            message = message,
            takesInput = true,
            inputType = field.inputType,
            options = field.options,
            nextAction = SessionAmigoResponse.NextAction.USER_INPUT
        )
    }
    
    private suspend fun executeToolsAndRespond(toolCalls: List<ToolCall>): SessionAmigoResponse {
        val registry = context.getToolRegistry() ?: return sessionResponse(
            "Unable to execute calculations",
            SessionAmigoResponse.NextAction.USER_INPUT
        )
        
        // Execute all tools
        for (toolCall in toolCalls) {
            Logger.d("SessionAmigoEngine", "Executing tool: ${toolCall.toolName}")
            try {
                val tool = registry.getTool(toolCall.toolName)
                val typedParams = coerceParams(toolCall.parameters, tool)
                val result = registry.executeTool(toolCall.toolName, typedParams)
                
                when (result) {
                    is com.amigo.shared.ai.tools.ToolResult.Success -> {
                        currentSession = context.mergeToolResults(
                            currentSession,
                            toolCall.toolName,
                            result.data
                        )
                        Logger.d("SessionAmigoEngine", "Tool ${toolCall.toolName} succeeded")
                    }
                    is com.amigo.shared.ai.tools.ToolResult.Error -> {
                        Logger.e("SessionAmigoEngine", "Tool ${toolCall.toolName} failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e("SessionAmigoEngine", "Exception executing ${toolCall.toolName}: ${e.message}")
            }
        }
        
        // Get AI commentary on tool results
        val aiResponse = callAI("Tools executed. Continue with next step.")
        return aiResponse
    }
    
    private suspend fun executeTools(toolCalls: List<ToolCall>): Map<String, Map<String, Any>> {
        val registry = context.getToolRegistry() ?: return emptyMap()
        val allResults = mutableMapOf<String, Map<String, Any>>()
        
        for (toolCall in toolCalls) {
            Logger.d("SessionAmigoEngine", "Executing tool: ${toolCall.toolName}")
            try {
                val tool = registry.getTool(toolCall.toolName)
                val typedParams = coerceParams(toolCall.parameters, tool)
                val result = registry.executeTool(toolCall.toolName, typedParams)
                
                when (result) {
                    is com.amigo.shared.ai.tools.ToolResult.Success -> {
                        allResults[toolCall.toolName] = result.data
                        Logger.d("SessionAmigoEngine", "Tool ${toolCall.toolName} succeeded: ${result.data.keys}")
                    }
                    is com.amigo.shared.ai.tools.ToolResult.Error -> {
                        Logger.e("SessionAmigoEngine", "Tool ${toolCall.toolName} failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e("SessionAmigoEngine", "Exception executing ${toolCall.toolName}: ${e.message}")
            }
        }
        
        return allResults
    }
    
    private suspend fun callAI(userInput: String): SessionAmigoResponse {
        val systemPrompt = context.buildSystemPrompt(currentSession)
        val userPrompt = context.buildUserPrompt(userInput, currentSession)
        
        Logger.i("SessionAmigoEngine", "Calling AI with context=${context.contextId}")
        Logger.d("SessionAmigoEngine", "System prompt length: ${systemPrompt.length}")
        Logger.d("SessionAmigoEngine", "User input: ${userInput.take(100)}")
        
        val result = bedrockClient.invokeModel(
            modelId = context.getModelId(),
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            maxTokens = context.getMaxTokens(),
            temperature = context.getTemperature()
        )
        
        return if (result.isSuccess) {
            val completion = result.getOrNull()?.completion ?: ""
            Logger.i("SessionAmigoEngine", "AI response (${completion.length} chars)")
            Logger.d("SessionAmigoEngine", "Response excerpt: ${completion.take(200)}")
            parseSessionResponse(completion)
        } else {
            // If AI fails, retry or defer - don't show error to user
            Logger.e("SessionAmigoEngine", "AI call failed: ${result.exceptionOrNull()?.message}")
            // Generate fallback question using context
            val nextField = context.getNextFieldToCollect(currentSession)
            if (nextField != null) {
                generateFieldQuestion(nextField)
            } else {
                // Last resort - ask to confirm
                SessionAmigoResponse(
                    message = "Let me confirm - does this look right to you?",
                    takesInput = true,
                    inputType = SessionAmigoResponse.InputType.YES_NO,
                    options = listOf("Yes", "No"),
                    nextAction = SessionAmigoResponse.NextAction.CONFIRMATION
                )
            }
        }
    }
    
    private fun parseSessionResponse(completion: String): SessionAmigoResponse {
        val cleaned = stripMarkdown(completion).trim()
        
        return try {
            val jsonStart = cleaned.indexOf("{")
            val jsonEnd = cleaned.lastIndexOf("}") + 1
            
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                // If no JSON found, treat as raw message
                return SessionAmigoResponse(
                    message = cleaned.take(500),
                    takesInput = true,
                    nextAction = SessionAmigoResponse.NextAction.USER_INPUT
                )
            }
            
            val jsonStr = cleaned.substring(jsonStart, jsonEnd)
            val parsed = json.decodeFromString<SessionAmigoResponse>(jsonStr)
            parsed
        } catch (e: Exception) {
            Logger.e("SessionAmigoEngine", "Failed to parse response: ${e.message}")
            // If parsing fails, return a safe response based on session state
            val nextField = context.getNextFieldToCollect(currentSession)
            if (nextField != null) {
                generateFieldQuestion(nextField)
            } else {
                SessionAmigoResponse(
                    message = "Let's confirm your health plan.",
                    takesInput = true,
                    inputType = SessionAmigoResponse.InputType.YES_NO,
                    options = listOf("Confirm", "Edit"),
                    nextAction = SessionAmigoResponse.NextAction.CONFIRMATION
                )
            }
        }
    }
    
    private fun coerceParams(
        rawParams: Map<String, String>,
        tool: com.amigo.shared.ai.tools.AmigoTool?
    ): Map<String, Any> {
        if (tool == null) return rawParams.mapValues { it.value as Any }
        
        val coerced = mutableMapOf<String, Any>()
        val expectedByNormalized = tool.parameters.keys.associateBy { normalize(it) }
        
        rawParams.forEach { (name, value) ->
            val canonical = resolveCanonical(name, expectedByNormalized)
            val type = tool.parameters[canonical]?.type
            coerced[canonical] = coerceValue(value, type)
        }
        
        return coerced
    }
    
    private fun resolveCanonical(
        incoming: String,
        expectedByNormalized: Map<String, String>
    ): String {
        val norm = normalize(incoming)
        expectedByNormalized[norm]?.let { return it }
        
        val fuzzy = expectedByNormalized
            .filterKeys { it.startsWith(norm) || it.contains(norm) }
            .values.distinct()
        
        return if (fuzzy.size == 1) fuzzy.first() else incoming
    }
    
    private fun normalize(value: String): String {
        return value.lowercase().replace("_", "").replace("-", "").replace(" ", "")
    }
    
    private fun coerceValue(rawValue: String, type: ParameterType?): Any {
        val value = rawValue.trim()
        return when (type) {
            ParameterType.NUMBER -> value.toDoubleOrNull() ?: value
            ParameterType.BOOLEAN -> when (value.lowercase()) {
                "true", "yes", "y", "1" -> true
                "false", "no", "n", "0" -> false
                else -> value
            }
            else -> value
        }
    }
    
    private fun stripMarkdown(input: String): String {
        var output = input.trim()
        if (output.contains("```json")) {
            output = output.substringAfter("```json").substringBefore("```").trim()
        } else if (output.contains("```")) {
            output = output.substringAfter("```").substringBefore("```").trim()
        }
        return output
    }
    
    private fun handleResponse(response: SessionAmigoResponse) {
        if (response.toolInvocation && response.toolInvocationDetails != null) {
            // Tool invocation message (different style)
            addAmigoMessage(
                text = response.message,
                isToolInvocation = true
            )
        } else if (response.takesInput) {
            // User input request
            addAmigoMessage(
                text = response.message,
                replyType = response.inputType.toReplyType(),
                replies = response.options,
                isToolInvocation = false
            )
        } else {
            // Just a message
            addAmigoMessage(
                text = response.message,
                isToolInvocation = false
            )
        }
    }
    
    private fun addAmigoMessage(
        text: String,
        replyType: ReplyType = ReplyType.TEXT,
        replies: List<String> = emptyList(),
        isToolInvocation: Boolean = false
    ) {
        val message = AmigoMessage(
            id = "msg_${currentTimeMillis()}_${(0..9999).random()}",
            text = text,
            isFromAmigo = true,
            timestamp = currentTimeMillis(),
            replyType = replyType,
            replies = replies.ifEmpty { null },
            metadata = if (isToolInvocation) mapOf("toolInvocation" to "true") else emptyMap()
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
    
    private fun addUserMessage(text: String) {
        val message = AmigoMessage(
            id = "msg_${currentTimeMillis()}_${(0..9999).random()}",
            text = text,
            isFromAmigo = false,
            timestamp = currentTimeMillis(),
            replyType = ReplyType.TEXT
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
    
    private fun disablePreviousInputOptions() {
        val lastInputIndex = conversationHistory.indexOfLast { 
            it.isFromAmigo && it.replies != null 
        }
        if (lastInputIndex >= 0) {
            val msg = conversationHistory[lastInputIndex]
            conversationHistory[lastInputIndex] = msg.copy(isDisabled = true)
            _messages.value = conversationHistory.toList()
        }
    }
    
    private fun sessionResponse(
        message: String,
        nextAction: SessionAmigoResponse.NextAction
    ): SessionAmigoResponse {
        return SessionAmigoResponse(
            message = message,
            takesInput = nextAction == SessionAmigoResponse.NextAction.USER_INPUT,
            nextAction = nextAction,
            dataCollected = currentSession.collectedData
        )
    }
    
    suspend fun processQuickReply(option: String): Result<Unit> {
        return processUserResponse(option)
    }
    
    fun getCollectedData(): Map<String, String> = currentSession.collectedData.toMap()
    
    fun isComplete(): Boolean = context.isComplete(currentSession)
    
    fun getSession(): ConversationSession = currentSession
}

fun SessionAmigoResponse.InputType.toReplyType(): ReplyType {
    return when (this) {
        SessionAmigoResponse.InputType.QUICK_PILLS -> ReplyType.QUICK_PILLS
        SessionAmigoResponse.InputType.YES_NO -> ReplyType.YES_NO
        SessionAmigoResponse.InputType.DROPDOWN -> ReplyType.LIST
        SessionAmigoResponse.InputType.DATE -> ReplyType.DATE
        SessionAmigoResponse.InputType.NUMBER -> ReplyType.NUMBER
        SessionAmigoResponse.InputType.TEXT -> ReplyType.TEXT
        SessionAmigoResponse.InputType.NONE -> ReplyType.TEXT
    }
}

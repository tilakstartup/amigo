package com.amigo.shared.ai

import com.amigo.shared.auth.SessionManager
import com.amigo.shared.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

/**
 * AI-powered conversational onboarding engine
 * Fully driven by AI with structured responses
 */
class OnboardingConversationEngine(
    private val bedrockClient: BedrockClient,
    private val sessionManager: SessionManager
) {
    private val _conversationState = MutableStateFlow<OnboardingState>(OnboardingState.Initial)
    val conversationState: StateFlow<OnboardingState> = _conversationState.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()
    
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val profileData = mutableMapOf<String, String>()
    private var lastAskedField: String? = null
    private var hasAskedPermissions: Boolean = false
    private val shownFeatureKeys = mutableSetOf<String>()
    private val minimumFeatureIntros = 3
    private val requiredFeatureKey = "meal_logging"
    private val baseRequiredProfileFields = listOf(
        "goalType",
        "goalDetail",
        "goalByWhen",
        "name",
        "age",
        "height",
        "weight",
        "activityLevel",
        "dietaryPreferences"
    )
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Start the onboarding conversation
     */
    suspend fun startOnboarding(): Result<Unit> {
        return try {
            _conversationState.value = OnboardingState.Collecting
            hasAskedPermissions = false
            shownFeatureKeys.clear()
            
            // Generate welcome message
            val response = generateAIResponse("START_ONBOARDING")
            handleAIResponse(response)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("OnboardingEngine", "Failed to start onboarding: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Process user response
     */
    suspend fun processUserResponse(response: String): Result<Unit> {
        return try {
            if (_isTyping.value) {
                Logger.w("OnboardingEngine", "Ignoring user input while AI response is in progress")
                return Result.success(Unit)
            }

            val expectedField = resolveExpectedFieldForCurrentTurn()

            // Disable the last Amigo message's reply options
            disableLastAmigoMessage()
            
            // Add user message
            addUserMessage(response)
            
            // Show typing indicator
            _isTyping.value = true
            
            // Get AI response
            val aiResponse = generateAIResponse(response, expectedField)
            handleAIResponse(aiResponse, userResponse = response, expectedField = expectedField)
            
            _isTyping.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _isTyping.value = false
            Logger.e("OnboardingEngine", "Failed to process response: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Process quick reply selection
     */
    suspend fun processQuickReply(option: String): Result<Unit> {
        return processUserResponse(option)
    }
    
    /**
     * Disable the last Amigo message to prevent re-selection
     */
    private fun disableLastAmigoMessage() {
        val lastAmigoIndex = conversationHistory.indexOfLast { it.isFromAmigo && it.replyType != "text" }
        if (lastAmigoIndex >= 0) {
            val lastMessage = conversationHistory[lastAmigoIndex]
            conversationHistory[lastAmigoIndex] = lastMessage.copy(isDisabled = true)
            _messages.value = conversationHistory.toList()
        }
    }
    
    /**
     * Generate AI response with structured format
     */
    private suspend fun generateAIResponse(userInput: String, expectedField: String? = null): AIResponse {
        val conversationContext = buildConversationHistory()
        val collectedData = buildCollectedDataSummary()
        val currentProfileData = buildProfileDataJson()
        val missingFields = missingRequiredFields().joinToString(", ").ifBlank { "none" }
        
        val prompt = if (userInput == "START_ONBOARDING") {
            """
            Start onboarding as Amigo.
            
            Current profile JSON:
            $currentProfileData
            
            Missing required fields: $missingFields
            
            Introduce yourself briefly as Amigo in the first sentence (once only), then ask ONE best first question.
            Keep tone warm and concise.
            Return JSON only.
            """.trimIndent()
        } else {
            """
            User said: "$userInput"
            
            Conversation context:
            $conversationContext
            
            Current profile JSON:
            $currentProfileData
            
            Collected data summary:
            $collectedData
            
            Missing required fields: $missingFields
            Expected field from latest question: ${expectedField ?: "unknown"}
            
            Continue the same conversation naturally. Do NOT restart or re-introduce yourself.
            Start with a short contextual acknowledgment of what the user just said, then ask exactly ONE next question.
            Extract ANY fields you can confidently infer from the user's latest message.
            Do NOT overwrite previously non-null fields unless the user clearly corrected them.
            Return JSON only.
            """.trimIndent()
        }
        
        Logger.i("OnboardingEngine", "PROMPT_START")
        Logger.i("OnboardingEngine", prompt)
        Logger.i("OnboardingEngine", "PROMPT_END")
        
        val result = bedrockClient.invokeModel(
            modelId = "amazon.nova-micro-v1:0", // Amazon Nova Micro - 88% cheaper than Claude, same quality
            prompt = prompt,
            systemPrompt = buildSystemPrompt(),
            maxTokens = 800,
            temperature = 0.3 // Lower temperature for more consistent, deterministic responses
        )
        
        return if (result.isSuccess) {
            val completion = result.getOrNull()?.completion ?: ""
            Logger.i("OnboardingEngine", "AI response received (${completion.length} chars)")
            parseAIResponse(completion)
        } else {
            Logger.e("OnboardingEngine", "AI call failed: ${result.exceptionOrNull()?.message}")
            // Fallback response
            AIResponse(
                message = "I'm having trouble connecting right now. Let's try again - what's your main health goal?",
                replyType = "quick_pills",
                replies = listOf("Weight Loss", "Muscle Gain", "Maintenance", "Improved Energy", "Better Sleep")
            )
        }
    }
    
    /**
     * Build current profile data as JSON string for prompt
     */
    private fun buildProfileDataJson(): String {
        return """
            {
              "goalType": ${profileData["goalType"]?.let { "\"$it\"" } ?: "null"},
                            "goalDetail": ${profileData["goalDetail"]?.let { "\"$it\"" } ?: "null"},
              "goalNumber": ${profileData["goalNumber"]?.let { "\"$it\"" } ?: "null"},
              "goalByWhen": ${profileData["goalByWhen"]?.let { "\"$it\"" } ?: "null"},
              "name": ${profileData["name"]?.let { "\"$it\"" } ?: "null"},
              "age": ${profileData["age"]?.let { "\"$it\"" } ?: "null"},
              "height": ${profileData["height"]?.let { "\"$it\"" } ?: "null"},
              "weight": ${profileData["weight"]?.let { "\"$it\"" } ?: "null"},
              "activityLevel": ${profileData["activityLevel"]?.let { "\"$it\"" } ?: "null"},
              "dietaryPreferences": ${profileData["dietaryPreferences"]?.let { "\"$it\"" } ?: "null"}
            }
        """.trimIndent()
    }
    
    /**
     * Parse AI response from JSON
     */
    private fun parseAIResponse(completion: String): AIResponse {
        return try {
            var jsonString = completion.trim()
            
            Logger.i("OnboardingEngine", "RESPONSE_START")
            Logger.i("OnboardingEngine", jsonString)
            Logger.i("OnboardingEngine", "RESPONSE_END")
            
            // Remove markdown code blocks if present
            if (jsonString.contains("```json")) {
                jsonString = jsonString.substringAfter("```json").substringBefore("```").trim()
            } else if (jsonString.contains("```")) {
                jsonString = jsonString.substringAfter("```").substringBefore("```").trim()
            }
            
            // Remove any text before the first { and after the last }
            val jsonStart = jsonString.indexOf("{")
            val jsonEnd = jsonString.lastIndexOf("}") + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonString = jsonString.substring(jsonStart, jsonEnd)
                
                val parsed = json.decodeFromString<AIResponse>(jsonString)
                Logger.i("OnboardingEngine", "Parsed JSON response successfully")
                parsed
            } else {
                // No JSON found, treat as plain text
                Logger.w("OnboardingEngine", "⚠️ No JSON found in response, using as plain text")
                AIResponse(message = completion.trim(), replyType = "text")
            }
        } catch (e: Exception) {
            Logger.e("OnboardingEngine", "❌ Failed to parse AI response: ${e.message}")
            Logger.e("OnboardingEngine", "Raw response: $completion")
            // Return the text as-is with text input
            AIResponse(
                message = "I apologize, I'm having trouble formatting my response. Could you please tell me: what's your main health goal?", 
                replyType = "quick_pills", 
                replies = listOf("Weight Loss", "Muscle Gain", "Maintenance", "Improved Energy", "Better Sleep")
            )
        }
    }
    
    /**
     * Send guidance to AI after each response to correct and direct next question
     */
    private suspend fun sendGuidanceAndGetNextQuestion(nextField: String?): AIResponse? {
        if (nextField == null) {
            return null
        }
        
        val fieldDisplayName = when(nextField) {
            "goalType" -> "their main health goal"
            "goalDetail" -> "their specific goal outcome (like reduce target weight or become athletic)"
            "goalNumber" -> "their target weight number"
            "goalByWhen" -> "when they want to achieve this goal by"
            "name" -> "their name"
            "age" -> "their age"
            "height" -> "their height in cm"
            "weight" -> "their current weight"
            "activityLevel" -> "their activity level"
            "dietaryPreferences" -> "their dietary preferences"
            else -> "the next required information"
        }
        
        val guidanceMessage = "Now ask the user about $fieldDisplayName. Be clear, friendly, and concise. Focus only on this one field."
        
        Logger.i("OnboardingEngine", "GUIDANCE_START")
        Logger.i("OnboardingEngine", "Guiding AI to ask: $nextField")
        Logger.i("OnboardingEngine", "GUIDANCE_END")
        
        return try {
            val currentProfileData = buildProfileDataJson()
            val missingFields = missingRequiredFields().joinToString(", ").ifBlank { "none" }
            val latestUserReply = conversationHistory.lastOrNull { !it.isFromAmigo }?.text ?: "none"
            
            val prompt = """
                Guidance: $guidanceMessage
                
                Current profile JSON:
                $currentProfileData
                
                Missing required fields: $missingFields
                Latest user reply: "$latestUserReply"
                
                Generate ONE clear, engaging question to ask about this field.
                Keep continuity with the latest user reply using a short acknowledgment phrase first.
                Do not re-introduce yourself.
                Return JSON only.
            """.trimIndent()
            
            Logger.i("OnboardingEngine", "GUIDANCE_PROMPT_START")
            Logger.i("OnboardingEngine", prompt)
            Logger.i("OnboardingEngine", "GUIDANCE_PROMPT_END")
            
            val result = bedrockClient.invokeModel(
                modelId = "amazon.nova-micro-v1:0",
                prompt = prompt,
                systemPrompt = buildSystemPrompt(),
                maxTokens = 800,
                temperature = 0.3
            )
            
            if (result.isSuccess) {
                val completion = result.getOrNull()?.completion ?: ""
                val guidedResponse = parseAIResponse(completion)
                Logger.i("OnboardingEngine", "Received guided AI response for field: $nextField")
                guidedResponse
            } else {
                Logger.e("OnboardingEngine", "Failed to get guided response: ${result.exceptionOrNull()?.message}")
                null
            }
        } catch (e: Exception) {
            Logger.e("OnboardingEngine", "Error in guidance loop: ${e.message}")
            null
        }
    }
    
    /**
     * Handle AI response and update state
     */
    private suspend fun handleAIResponse(response: AIResponse, userResponse: String? = null, expectedField: String? = null) {
        var fieldCapturedByFallback = false
        
        // Store collected data from profileData
        response.profileData?.let { rawData ->
            val data = normalizeProfileData(rawData)
            
            // Store each field if not null
            data.goalType?.let { profileData["goalType"] = it }
            data.goalDetail?.let { profileData["goalDetail"] = it }
            data.goalNumber?.let { profileData["goalNumber"] = it }
            data.goalByWhen?.let { profileData["goalByWhen"] = it }
            data.name?.let { profileData["name"] = it }
            data.age?.let { profileData["age"] = it }
            data.height?.let { profileData["height"] = it }
            data.weight?.let { profileData["weight"] = it }
            data.activityLevel?.let { profileData["activityLevel"] = it }
            data.dietaryPreferences?.let { profileData["dietaryPreferences"] = it }
        }

        val fallbackField = expectedField?.takeIf { it.isNotBlank() }
        val canApplyFallback = !userResponse.isNullOrBlank() &&
            fallbackField != null &&
            profileData[fallbackField].isNullOrBlank() &&
            (lastAskedField == null || lastAskedField == fallbackField)

        if (canApplyFallback) {
            if (fallbackField != null && userResponse != null) {
                normalizeForField(fallbackField, userResponse)?.let { normalized ->
                    profileData[fallbackField] = normalized
                    fieldCapturedByFallback = true
                    Logger.i("OnboardingEngine", "Fallback captured field '$fallbackField' from user reply")
                }
            }
        }

        // Check if all fields are now filled after fallback capture
        val allFieldsFilled = missingRequiredFields().isEmpty()
        
        if (allFieldsFilled && !hasAskedPermissions) {
            // All done! Ask for permission to access health data.
            // If feature intro goals are not met, introduce one final feature first.
            Logger.i("OnboardingEngine", "All fields filled - preparing permissions question")
            hasAskedPermissions = true
            val permissionResponse = AIResponse(
                message = "Great! To help you track your progress, can I access your health data?",
                replyType = "yes_no",
                replies = listOf("Yes", "No"),
                profileData = null,
                nextAction = "ask_permissions"
            )

            val finalIntroBeforePermissions = if (featureGoalsSatisfied()) {
                null
            } else {
                buildFinalFeatureIntroBeforePermissions(permissionResponse)
            }

            if (finalIntroBeforePermissions?.featureIntro != null) {
                val intro = finalIntroBeforePermissions.featureIntro
                if (intro != null) {
                    trackFeatureShown(intro.feature)
                    addAmigoMessage(
                        text = intro.pretext,
                        replyType = "text",
                        replies = null,
                        feature = null
                    )
                    addAmigoMessage(
                        text = "",
                        replyType = "text",
                        replies = null,
                        feature = intro.feature,
                        isFeatureIntro = true
                    )
                    addAmigoMessage(
                        text = intro.followUpMessage,
                        replyType = intro.followUpReplyType,
                        replies = intro.followUpReplies,
                        feature = null,
                        delayAfterPrevious = 3000
                    )
                }
            } else {
                addAmigoMessage(
                    text = permissionResponse.message,
                    replyType = permissionResponse.replyType ?: "yes_no",
                    replies = permissionResponse.replies,
                    feature = null
                )
            }

            _conversationState.value = OnboardingState.Collecting
            return
        }
        
        // Check if user is responding to permission question
        if (allFieldsFilled && hasAskedPermissions && userResponse != null) {
            val normalizedResponse = userResponse.trim().lowercase()
            when {
                normalizedResponse.contains("yes") -> {
                    Logger.i("OnboardingEngine", "User accepted permissions - opening permissions modal")
                    _conversationState.value = OnboardingState.RequestingPermissions
                    return
                }
                normalizedResponse.contains("no") -> {
                    Logger.i("OnboardingEngine", "User declined permissions - completing onboarding")
                    _conversationState.value = OnboardingState.Complete
                    return
                }
            }
        }
        
        var finalResponse = response
        if (fieldCapturedByFallback && shouldReplaceDuplicateQuestion(response.message, fallbackField)) {
            val nextField = missingRequiredFields().firstOrNull()
            if (nextField != null) {
                finalResponse = buildFallbackQuestionForField(nextField)
                Logger.i("OnboardingEngine", "Replaced duplicate AI question with next missing field '$nextField'")
            }
        }

        val askedFieldInResponse = inferAskedField(finalResponse.message, finalResponse.replyType)
        if (!askedFieldInResponse.isNullOrBlank() && !profileData[askedFieldInResponse].isNullOrBlank()) {
            val nextField = missingRequiredFields().firstOrNull { it != askedFieldInResponse }
            if (nextField != null) {
                finalResponse = buildFallbackQuestionForField(nextField)
                Logger.i("OnboardingEngine", "AI asked already-filled field '$askedFieldInResponse'; advanced to '$nextField'")
            } else if (missingRequiredFields().isEmpty() && !hasAskedPermissions) {
                hasAskedPermissions = true
                finalResponse = AIResponse(
                    message = "Great! To help you track your progress, can I access your health data?",
                    replyType = "yes_no",
                    replies = listOf("Yes", "No"),
                    profileData = currentProfileDataSnapshot(),
                    nextAction = "ask_permissions"
                )
                Logger.i("OnboardingEngine", "AI repeated filled field at completion; switched to permissions question")
            }
        }

        val nextMissingAfterDedup = missingRequiredFields().firstOrNull()
        if (finalResponse.featureIntro == null && nextMissingAfterDedup != null && shouldInjectDeterministicFeatureIntro(nextMissingAfterDedup)) {
            buildDeterministicFeatureIntro(nextMissingAfterDedup)?.let { introResponse ->
                finalResponse = introResponse
                Logger.i("OnboardingEngine", "Injected deterministic feature intro before field '$nextMissingAfterDedup'")
            }
        }

        val inferredAskedField = inferAskedField(finalResponse.message, finalResponse.replyType)
        if (inferredAskedField != null) {
            lastAskedField = inferredAskedField
            Logger.i("OnboardingEngine", "Tracking asked field '$inferredAskedField'")
        }
        
        // Preserve feature intros from the original AI response.
        // Guidance is only used when there is no feature intro.
        val hasFeatureIntro = finalResponse.featureIntro != null
        val nextMissingField = missingRequiredFields().firstOrNull()
        val shouldSendGuidance = nextMissingField != null && !hasFeatureIntro

        val guidedResponse = if (shouldSendGuidance) {
            Logger.i("OnboardingEngine", "Sending guidance for field: $nextMissingField")
            sendGuidanceAndGetNextQuestion(nextMissingField)
        } else {
            if (hasFeatureIntro) {
                Logger.i("OnboardingEngine", "Preserving original response with feature intro")
            } else {
                Logger.i("OnboardingEngine", "No missing fields - skipping guidance")
            }
            null
        }

        // Use guided response if available, otherwise keep original response (which may include featureIntro)
        val responseToShow = guidedResponse ?: finalResponse
        
        // Update tracked field based on guided response
        if (guidedResponse != null) {
            val guidedAskedField = inferAskedField(guidedResponse.message, guidedResponse.replyType)
            if (guidedAskedField != null) {
                lastAskedField = guidedAskedField
                Logger.i("OnboardingEngine", "Updated to guided field '$guidedAskedField'")
            }
        }
        
        // Handle feature introduction
        if (responseToShow.featureIntro != null) {
            val intro = responseToShow.featureIntro ?: return
            trackFeatureShown(intro.feature)
            
            val hasPriorAmigoMessage = conversationHistory.any { it.isFromAmigo }
            val introPretext = if (!hasPriorAmigoMessage) {
                ensureAmigoIntroduction(intro.pretext)
            } else {
                intro.pretext
            }
            
            // Add pretext message
            addAmigoMessage(
                text = introPretext,
                replyType = "text", // Pretext doesn't need interaction
                replies = null,
                feature = null
            )
            
            // Add feature card (will be rendered with 3-second delay before follow-up)
            addAmigoMessage(
                text = "", // Empty text, just showing feature
                replyType = "text", // Feature card doesn't need interaction
                replies = null,
                feature = intro.feature,
                isFeatureIntro = true
            )
            
            // Update lastAskedField to track what the follow-up message asks
            val followUpAskedField = inferAskedField(intro.followUpMessage, intro.followUpReplyType)
            if (followUpAskedField != null) {
                lastAskedField = followUpAskedField
                Logger.i("OnboardingEngine", "Updated to feature follow-up field '$followUpAskedField'")
            }
            
            // Add follow-up question (will be shown after 3-second delay)
            addAmigoMessage(
                text = intro.followUpMessage,
                replyType = intro.followUpReplyType,
                replies = intro.followUpReplies,
                feature = null,
                delayAfterPrevious = 3000 // 3 seconds delay
            )
        } else {
            // Standard message
            // Ensure replyType is set, default to "text" if missing
            val finalReplyType = responseToShow.replyType ?: "text"
            val hasPriorAmigoMessage = conversationHistory.any { it.isFromAmigo }
            val messageText = if (!hasPriorAmigoMessage) {
                ensureAmigoIntroduction(responseToShow.message)
            } else {
                responseToShow.message
            }
            
            addAmigoMessage(
                text = messageText,
                replyType = finalReplyType,
                replies = responseToShow.replies,
                feature = null
            )
        }
        
        // Handle next action with strict gating by required data completeness
        val hasAllFields = missingRequiredFields().isEmpty()
        when {
            responseToShow.nextAction == "request_permissions" -> {
                Logger.i("OnboardingEngine", "User confirmed permissions -> opening permission dialog")
                _conversationState.value = OnboardingState.RequestingPermissions
            }
            hasAllFields && responseToShow.nextAction == "complete" -> {
                Logger.i("OnboardingEngine", "All fields collected and nextAction=complete")
                _conversationState.value = OnboardingState.Complete
            }
            hasAllFields && responseToShow.nextAction == "ask_permissions" -> {
                Logger.i("OnboardingEngine", "All fields collected -> asking about permissions")
                _conversationState.value = OnboardingState.Collecting
            }
            hasAllFields -> {
                Logger.i("OnboardingEngine", "All fields collected -> continue")
                _conversationState.value = OnboardingState.Collecting
            }
            else -> {
                Logger.i("OnboardingEngine", "Missing required fields -> continue collecting (${missingRequiredFields().joinToString(",")})")
                _conversationState.value = OnboardingState.Collecting
            }
        }
    }
    
    /**
     * Build conversation history for context
     */
    private fun buildConversationHistory(): String {
        if (conversationHistory.isEmpty()) {
            return "This is the start of the conversation."
        }
        
        val history = StringBuilder("Previous conversation:\n")
        conversationHistory.takeLast(10).forEach { message ->
            val speaker = if (message.isFromAmigo) "Amigo" else "User"
            history.append("$speaker: ${message.text}\n")
        }
        return history.toString()
    }

    private fun ensureAmigoIntroduction(message: String): String {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return "Hi! I’m Amigo, your AI health coach. What’s your main health goal?"

        val lower = trimmed.lowercase()
        val alreadyIntroduced = lower.contains("i'm amigo") || lower.contains("i am amigo") || lower.contains("i’m amigo")
        if (alreadyIntroduced) return trimmed

        return "Hi, I’m Amigo, your AI health coach. $trimmed"
    }
    
    /**
     * Build summary of collected data
     */
    private fun buildCollectedDataSummary(): String {
        if (profileData.isEmpty()) {
            return "None"
        }
        
        val summary = StringBuilder()
        profileData.forEach { (key, value) ->
            summary.append("- $key: $value\n")
        }
        
        // List what's still needed
        val missingFields = missingRequiredFields()
        
        if (missingFields.isNotEmpty()) {
            summary.append("\nStill need to collect: ${missingFields.joinToString(", ")}")
        } else {
            summary.append("\nAll required data collected!")
        }
        
        return summary.toString()
    }
    
    /**
     * Build comprehensive system prompt
     */
    private fun buildSystemPrompt(): String {
        return """
            You are Amigo, an AI health coach. Be warm, enthusiastic, and engaging. Create curiosity and excitement about the user's health journey.
            
            ## Your Communication Style
            - Be conversational and natural, not robotic
            - Show genuine interest in the user's responses
            - Acknowledge their inputs warmly before moving forward
            - Build momentum and excitement as you learn about them
            - Use encouraging language and positive reinforcement
            
            ## Data to Collect (10 fields)
            1. goalType: "weight", "fitness", "health", "energy", "sleep"
            2. goalDetail: goal direction/details based on goalType (examples below)
            3. goalNumber: numeric target only when applicable (e.g., target weight)
            4. goalByWhen: date YYYY-MM-DD (use "date" replyType)
            5. name: first name
            6. age: 13-120
            7. height: "5'10\"", "178cm", "1.78m"
            8. weight: "180lbs", "82kg"
            9. activityLevel: "Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extremely Active"
            10. dietaryPreferences: "No Restrictions", "Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo"
            
            ## Available Features (IMPORTANT: Use these to create interest!)
            Introduce features at strategic moments to build excitement. Don't just collect data - show what makes this app special!
            
            **When to introduce features:**
            - After collecting goalType/goalNumber/goalByWhen: Introduce "Progress Charts" - show how they'll track their journey
            - After collecting dietaryPreferences: Introduce "Meal Logging" - relevant to their food choices
            - After collecting activityLevel and before weight: Introduce "Water Tracking" or "Fasting Timer" based on their goal
            - Introduce "AI Coach" when transitioning to personal details (name/age)
            
            **Features:**
            1. Meal Logging: {"name": "Meal Logging", "description": "Snap a photo of your meal and I'll analyze the nutrition instantly. No manual entry needed!", "icon": "📸"}
            2. Water Tracking: {"name": "Water Tracking", "description": "Stay hydrated with quick tap tracking. I'll remind you throughout the day!", "icon": "💧"}
            3. Fasting Timer: {"name": "Fasting Timer", "description": "Track your fasting windows with beautiful visuals and smart notifications.", "icon": "⏰"}
            4. AI Coach: {"name": "AI Health Coach", "description": "I'm here 24/7 to answer questions, give personalized advice, and celebrate your wins!", "icon": "🤖"}
            5. Progress Charts: {"name": "Progress Charts", "description": "Beautiful charts show your progress over time. Watch your success unfold!", "icon": "📊"}
            
            ## JSON Response (MANDATORY FORMAT)
            ### Standard Response:
            {
              "message": "your message",
              "replyType": "quick_pills" | "list" | "yes_no" | "text" | "date",
              "replies": ["option1", "option2"] or null,
              "profileData": {
                "goalType": "value" or null,
                                "goalDetail": "value" or null,
                "goalNumber": "value" or null,
                "goalByWhen": "value" or null,
                "name": "value" or null,
                "age": "value" or null,
                "height": "value" or null,
                "weight": "value" or null,
                "activityLevel": "value" or null,
                "dietaryPreferences": "value" or null
              },
              "nextAction": "continue" | "ask_permissions" | "request_permissions"
            }
            
                        ### Feature Intro Response (use this in the SAME response schema):
            {
              "featureIntro": {
                "pretext": "Exciting transition message that relates to what they just shared",
                "feature": {"name": "...", "description": "...", "icon": "..."},
                "followUpMessage": "Natural question that flows from the feature",
                "followUpReplyType": "quick_pills" | "text" | etc,
                "followUpReplies": ["option1", "option2"] or null
              },
              "profileData": {...},
              "nextAction": "continue"
            }
            
            ## Reply Types
            - quick_pills: buttons (max 6 options)
            - list: dropdown (>6 options)
            - yes_no: Yes/No buttons
            - text: user types
            - date: date picker
            
            ## Critical Rules - Creating an Engaging Experience
            1. **Be conversational**: Acknowledge their responses warmly before asking next question
               - Good: "Love it! 65kg is a great target. When would you like to reach this goal?"
               - Bad: "What's your target date?"
            
                2. **Use feature intros strategically** (goal: AT LEAST 3 total, and Meal Logging is MANDATORY):
               - After goalByWhen (3rd field): Introduce "Progress Charts"
               - After dietaryPreferences (10th field): Introduce "Meal Logging"
               - Before or after weight: Introduce "Water Tracking" or "Fasting Timer"

                2a. Let the timing feel natural based on context, but make sure you still hit the goal above.
                2b. When using featureIntro, keep the question in the same response via followUpMessage/followUpReplyType.
            
            3. **Check current data FIRST** - never ask for non-null fields
            
            4. **ALWAYS include complete profileData** with ALL 10 fields in every response
            
            5. **Extract fields from user's message** when explicitly stated
            
            6. **Keep non-null values unchanged** unless user clearly corrects them
            
            7. **Use canonical values**:
               - goalType: weight | fitness | health | energy | sleep
               - activityLevel: Sedentary | Lightly Active | Moderately Active | Very Active | Extremely Active
            
                8. **Permission handling**: When user says "Yes" to health data question, set nextAction="request_permissions"
                    If feature goals are not yet met near the end, introduce a feature first, then ask permission via followUpMessage.
            
            9. **Output ONLY JSON** (no text before/after, no markdown blocks)
            
            ## Example Feature Intro Timing
            - After user sets goal date → "Awesome! March 31st gives us great time to work with. Let me show you something cool..."
            - After dietary preferences → "Perfect! Since you mentioned [diet], here's a feature you'll love..."
            - Before asking weight → "Before we dive into current stats, check out this tracking feature..."
            
            ## Reply Type Selection
            - goalType: quick_pills ["Weight Loss", "Muscle Gain", "Better Health", "More Energy", "Better Sleep"]
            - goalDetail: quick_pills based on goalType
                - weight: ["Reduce to target weight", "Maintain weight", "Lose body fat"]
                - fitness: ["Become athletic", "Build muscle", "Improve endurance"]
                - health: ["Improve routine", "Lower stress", "Better metabolism"]
                - energy: ["Boost daily energy", "Reduce fatigue", "Improve focus"]
                - sleep: ["Fall asleep faster", "Sleep deeper", "Consistent schedule"]
            - goalNumber: text ONLY for numeric goals (e.g., target weight)
            - goalByWhen: date
            - name: text
            - age: text
            - height: text
            - weight: text
            - activityLevel: quick_pills (5 options)
            - dietaryPreferences: quick_pills (6 options)
        """.trimIndent()
    }

    private fun missingRequiredFields(): List<String> {
        val requiredFields = baseRequiredProfileFields.toMutableList()
        if (requiresGoalNumber()) {
            requiredFields.add(2, "goalNumber")
        }
        return requiredFields.filter { field ->
            profileData[field].isNullOrBlank()
        }
    }

    private fun requiresGoalNumber(): Boolean {
        val goalType = profileData["goalType"]?.lowercase().orEmpty()
        val goalDetail = profileData["goalDetail"]?.lowercase().orEmpty()

        if (goalType == "weight") return true

        return goalDetail.contains("target") ||
            goalDetail.contains("kg") ||
            goalDetail.contains("lb") ||
            goalDetail.contains("number")
    }

    private fun normalizeProfileData(data: ProfileData): ProfileData {
        return ProfileData(
            goalType = normalizeGoalType(data.goalType),
            goalDetail = normalizeGoalDetail(data.goalDetail),
            goalNumber = normalizeFreeText(data.goalNumber),
            goalByWhen = normalizeFreeText(data.goalByWhen),
            name = normalizeName(data.name),
            age = normalizeAge(data.age),
            height = normalizeHeight(data.height),
            weight = normalizeWeight(data.weight),
            activityLevel = normalizeActivityLevel(data.activityLevel),
            dietaryPreferences = normalizeDietaryPreference(data.dietaryPreferences)
        )
    }

    private fun normalizeFreeText(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun normalizeName(value: String?): String? {
        val normalized = normalizeFreeText(value) ?: return null
        return normalized.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeAge(value: String?): String? {
        val normalized = normalizeFreeText(value) ?: return null
        val ageValue = normalized.filter { it.isDigit() }.toIntOrNull() ?: return null
        return if (ageValue in 13..120) ageValue.toString() else null
    }

    private fun normalizeGoalType(value: String?): String? {
        val normalized = normalizeFreeText(value)?.lowercase() ?: return null
        return when {
            "weight" in normalized || "fat" in normalized || "loss" in normalized -> "weight"
            "muscle" in normalized || "fit" in normalized || "strength" in normalized -> "fitness"
            "sleep" in normalized -> "sleep"
            "energy" in normalized || "stamina" in normalized -> "energy"
            "health" in normalized || "well" in normalized -> "health"
            else -> null
        }
    }

    private fun normalizeGoalDetail(value: String?): String? {
        val normalized = normalizeFreeText(value)?.lowercase() ?: return null
        return when {
            normalized.contains("reduce") && normalized.contains("weight") -> "reduce_target_weight"
            normalized.contains("maintain") && normalized.contains("weight") -> "maintain_weight"
            normalized.contains("body fat") || normalized.contains("fat") -> "lose_body_fat"
            normalized.contains("athlete") || normalized.contains("athletic") -> "become_athlete"
            normalized.contains("muscle") -> "build_muscle"
            normalized.contains("endurance") || normalized.contains("stamina") -> "improve_endurance"
            normalized.contains("routine") -> "improve_routine"
            normalized.contains("stress") -> "lower_stress"
            normalized.contains("metabolism") -> "better_metabolism"
            normalized.contains("energy") || normalized.contains("fatigue") || normalized.contains("focus") -> "boost_energy"
            normalized.contains("sleep") || normalized.contains("asleep") || normalized.contains("deeper") -> "better_sleep_quality"
            normalized.contains("kg") || normalized.contains("kilo") -> "reduce_target_weight"
            normalized.contains("lb") || normalized.contains("pound") -> "reduce_target_weight"
            normalized.contains("beginner") -> "improve_routine"
            normalized.contains("advanced") -> "become_athlete"
            else -> normalizeFreeText(value)
        }
    }

    private fun normalizeActivityLevel(value: String?): String? {
        val normalized = normalizeFreeText(value)?.lowercase() ?: return null
        return when {
            "sedentary" in normalized -> "Sedentary"
            "light" in normalized -> "Lightly Active"
            "moderate" in normalized -> "Moderately Active"
            "very" in normalized -> "Very Active"
            "extreme" in normalized || "athlete" in normalized -> "Extremely Active"
            else -> normalizeFreeText(value)
        }
    }

    private fun normalizeDietaryPreference(value: String?): String? {
        val normalized = normalizeFreeText(value)?.lowercase() ?: return null
        return when {
            "no" in normalized && "restriction" in normalized -> "No Restrictions"
            "vegetarian" in normalized -> "Vegetarian"
            "vegan" in normalized -> "Vegan"
            "gluten" in normalized -> "Gluten-Free"
            "keto" in normalized -> "Keto"
            "paleo" in normalized -> "Paleo"
            else -> normalizeFreeText(value)
        }
    }

    private fun normalizeHeight(value: String?): String? {
        val normalized = normalizeFreeText(value) ?: return null
        val lower = normalized.lowercase()
        val hasUnit = lower.contains("cm") || lower.contains("m") || lower.contains("ft") || lower.contains("'") || lower.contains("in") || lower.contains("\"")
        val numeric = lower.filter { it.isDigit() || it == '.' }
        val number = numeric.toDoubleOrNull()

        if (hasUnit) return normalized
        if (number != null && number in 100.0..250.0) return normalized
        return null
    }

    private fun normalizeWeight(value: String?): String? {
        val normalized = normalizeFreeText(value) ?: return null
        val lower = normalized.lowercase()
        val hasUnit = lower.contains("kg") || lower.contains("lb") || lower.contains("lbs")
        val numeric = lower.filter { it.isDigit() || it == '.' }
        val number = numeric.toDoubleOrNull()

        if (hasUnit) return normalized
        if (number != null && number in 20.0..500.0) return normalized
        return null
    }

    private fun normalizeForField(field: String, value: String): String? {
        return when (field) {
            "goalType" -> normalizeGoalType(value)
            "goalDetail" -> normalizeGoalDetail(value)
            "goalNumber" -> normalizeFreeText(value)
            "goalByWhen" -> normalizeFreeText(value)
            "name" -> normalizeName(value)
            "age" -> normalizeAge(value)
            "height" -> normalizeHeight(value)
            "weight" -> normalizeWeight(value)
            "activityLevel" -> normalizeActivityLevel(value)
            "dietaryPreferences" -> normalizeDietaryPreference(value)
            else -> normalizeFreeText(value)
        }
    }

    private fun resolveExpectedFieldForCurrentTurn(): String? {
        val currentMissing = missingRequiredFields()
        if (currentMissing.isEmpty()) return null

        val tracked = lastAskedField
        if (!tracked.isNullOrBlank() && currentMissing.contains(tracked)) {
            return tracked
        }

        return currentMissing.firstOrNull()
    }

    private fun inferAskedField(message: String, replyType: String?): String? {
        val text = message.trim().lowercase()
        if (text.isEmpty()) return null

        return when {
            "dietary" in text -> "dietaryPreferences"
            "activity" in text -> "activityLevel"
            "current weight" in text || text.contains("what's your weight") || text.contains("what’s your weight") -> "weight"
            "height" in text -> "height"
            text.contains("what's your age") || text.contains("what’s your age") -> "age"
            text.contains("what's your first name") || text.contains("what's your name") || text.contains("what should i call you") || text.contains("what’s your first name") || text.contains("what’s your name") -> "name"
            text.contains("when do you want") || text.contains("when would you") || replyType == "date" -> "goalByWhen"
            text.contains("goal number") || text.contains("target number") || text.contains("how much") -> "goalNumber"
            text.contains("goal outcome") || text.contains("goal detail") || text.contains("athlete") || text.contains("muscle") || text.contains("maintain") || text.contains("target weight") -> "goalDetail"
            text.contains("primary health goal") || text.contains("health goal") || text.contains("main health goal") -> "goalType"
            else -> null
        }
    }

    private fun shouldReplaceDuplicateQuestion(message: String, expectedField: String?): Boolean {
        if (expectedField.isNullOrBlank()) return false
        val normalizedMessage = message.trim().lowercase()
        if (normalizedMessage.isEmpty()) return false

        val asksExpectedField = when (expectedField) {
            "dietaryPreferences" -> normalizedMessage.contains("dietary")
            "activityLevel" -> normalizedMessage.contains("activity")
            "weight" -> normalizedMessage.contains("weight")
            "height" -> normalizedMessage.contains("height")
            "age" -> normalizedMessage.contains("age")
            "name" -> normalizedMessage.contains("name")
            "goalByWhen" -> normalizedMessage.contains("when") || normalizedMessage.contains("date")
            "goalNumber" -> normalizedMessage.contains("goal") || normalizedMessage.contains("target")
            "goalDetail" -> normalizedMessage.contains("goal") || normalizedMessage.contains("outcome") || normalizedMessage.contains("athlete") || normalizedMessage.contains("muscle")
            "goalType" -> normalizedMessage.contains("goal")
            else -> false
        }
        if (!asksExpectedField) return false

        val lastAmigoMessage = conversationHistory.lastOrNull { it.isFromAmigo }?.text?.trim()?.lowercase()
        return lastAmigoMessage == normalizedMessage
    }

    private fun buildFallbackQuestionForField(field: String): AIResponse {
        return when (field) {
            "goalType" -> AIResponse(
                message = "What’s your primary health goal?",
                replyType = "quick_pills",
                replies = listOf("Weight Loss", "Muscle Gain", "Better Health", "More Energy", "Better Sleep"),
                nextAction = "continue"
            )
            "goalDetail" -> AIResponse(
                message = when (profileData["goalType"]) {
                    "weight" -> "What kind of weight goal do you want to focus on?"
                    "fitness" -> "What fitness outcome matters most to you right now?"
                    "health" -> "What health outcome would you like to improve first?"
                    "energy" -> "What energy-related improvement are you aiming for?"
                    "sleep" -> "What sleep goal feels most important to you?"
                    else -> "What outcome do you want to focus on first?"
                },
                replyType = "quick_pills",
                replies = when (profileData["goalType"]) {
                    "weight" -> listOf("Reduce to target weight", "Maintain weight", "Lose body fat")
                    "fitness" -> listOf("Become athletic", "Build muscle", "Improve endurance")
                    "health" -> listOf("Improve routine", "Lower stress", "Better metabolism")
                    "energy" -> listOf("Boost daily energy", "Reduce fatigue", "Improve focus")
                    "sleep" -> listOf("Fall asleep faster", "Sleep deeper", "Consistent schedule")
                    else -> listOf("Reduce to target weight", "Build muscle", "Become athletic")
                },
                nextAction = "continue"
            )
            "goalNumber" -> AIResponse(
                message = "What target number would you like to reach?",
                replyType = "text",
                nextAction = "continue"
            )
            "goalByWhen" -> AIResponse(
                message = "When would you like to achieve this goal?",
                replyType = "date",
                nextAction = "continue"
            )
            "name" -> AIResponse(
                message = "What should I call you?",
                replyType = "text",
                nextAction = "continue"
            )
            "age" -> AIResponse(
                message = "What’s your age?",
                replyType = "text",
                nextAction = "continue"
            )
            "height" -> AIResponse(
                message = "What’s your current height?",
                replyType = "text",
                nextAction = "continue"
            )
            "weight" -> AIResponse(
                message = "What’s your current weight?",
                replyType = "text",
                nextAction = "continue"
            )
            "activityLevel" -> AIResponse(
                message = "What’s your activity level?",
                replyType = "quick_pills",
                replies = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extremely Active"),
                nextAction = "continue"
            )
            "dietaryPreferences" -> AIResponse(
                message = "What are your dietary preferences?",
                replyType = "quick_pills",
                replies = listOf("No Restrictions", "Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo"),
                nextAction = "continue"
            )
            else -> AIResponse(
                message = "Tell me a bit more so I can personalize your plan.",
                replyType = "text",
                nextAction = "continue"
            )
        }
    }

    private fun currentProfileDataSnapshot(): ProfileData {
        return ProfileData(
            goalType = profileData["goalType"],
            goalDetail = profileData["goalDetail"],
            goalNumber = profileData["goalNumber"],
            goalByWhen = profileData["goalByWhen"],
            name = profileData["name"],
            age = profileData["age"],
            height = profileData["height"],
            weight = profileData["weight"],
            activityLevel = profileData["activityLevel"],
            dietaryPreferences = profileData["dietaryPreferences"]
        )
    }

    private fun normalizeFeatureKey(name: String): String {
        val normalized = name.trim().lowercase()
        return when {
            normalized.contains("meal") -> "meal_logging"
            normalized.contains("chart") || normalized.contains("progress") -> "progress_charts"
            normalized.contains("water") -> "water_tracking"
            normalized.contains("fast") -> "fasting_timer"
            normalized.contains("coach") || normalized.contains("ai") -> "ai_coach"
            else -> normalized.replace(" ", "_")
        }
    }

    private fun trackFeatureShown(feature: FeatureIntro?) {
        if (feature == null) return
        val key = normalizeFeatureKey(feature.name)
        shownFeatureKeys += key
        Logger.i("OnboardingEngine", "Tracked feature intro '$key' (${shownFeatureKeys.size}/$minimumFeatureIntros)")
    }

    private fun featureGoalsSatisfied(): Boolean {
        return shownFeatureKeys.size >= minimumFeatureIntros && shownFeatureKeys.contains(requiredFeatureKey)
    }

    private fun shouldInjectDeterministicFeatureIntro(nextField: String): Boolean {
        val missingCount = missingRequiredFields().size
        val featureCount = shownFeatureKeys.size
        val remainingGoal = (minimumFeatureIntros - featureCount).coerceAtLeast(0)
        val mealMissing = !shownFeatureKeys.contains(requiredFeatureKey)
        val contextualOpportunity = nextField in setOf("goalByWhen", "name", "activityLevel", "dietaryPreferences")
        val mustForceMealNow = mealMissing && nextField == "dietaryPreferences"

        if (!contextualOpportunity) return false
        if (mustForceMealNow) return true
        if (remainingGoal > 0) return true

        return false
    }

    private fun buildFinalFeatureIntroBeforePermissions(permissionResponse: AIResponse): AIResponse? {
        val followUpMessage = permissionResponse.message
        val followUpReplyType = permissionResponse.replyType ?: "yes_no"
        val followUpReplies = permissionResponse.replies

        val feature = when {
            !shownFeatureKeys.contains(requiredFeatureKey) -> FeatureIntro(
                name = "Meal Logging",
                description = "Snap a photo of your meals and get instant nutrition insights automatically.",
                icon = "📸"
            )
            !shownFeatureKeys.contains("progress_charts") -> FeatureIntro(
                name = "Progress Charts",
                description = "Track your momentum with visual trends and milestone progress.",
                icon = "📊"
            )
            !shownFeatureKeys.contains("ai_coach") -> FeatureIntro(
                name = "AI Health Coach",
                description = "Get personalized support anytime with smart recommendations.",
                icon = "🤖"
            )
            !shownFeatureKeys.contains("water_tracking") -> FeatureIntro(
                name = "Water Tracking",
                description = "Quick hydration logging with gentle reminders to stay consistent.",
                icon = "💧"
            )
            !shownFeatureKeys.contains("fasting_timer") -> FeatureIntro(
                name = "Fasting Timer",
                description = "Visual fasting windows with reminders and streak tracking.",
                icon = "⏰"
            )
            else -> return null
        }

        return AIResponse(
            featureIntro = FeatureIntroResponse(
                pretext = "Before we finish setup, here’s one feature you’ll love using from day one.",
                feature = feature,
                followUpMessage = followUpMessage,
                followUpReplyType = followUpReplyType,
                followUpReplies = followUpReplies
            ),
            profileData = currentProfileDataSnapshot(),
            nextAction = "ask_permissions"
        )
    }

    private fun buildDeterministicFeatureIntro(nextField: String): AIResponse? {
        val goalByWhen = profileData["goalByWhen"]
        val name = profileData["name"]
        val activityLevel = profileData["activityLevel"]
        val goalType = profileData["goalType"]
        val dietaryPreferences = profileData["dietaryPreferences"]

        val followUp = buildFallbackQuestionForField(nextField)

        if ((nextField == "dietaryPreferences" || !dietaryPreferences.isNullOrBlank()) && requiredFeatureKey !in shownFeatureKeys) {
            shownFeatureKeys += requiredFeatureKey
            return AIResponse(
                featureIntro = FeatureIntroResponse(
                    pretext = "Perfect — nutrition preferences are where personalization gets really powerful.",
                    feature = FeatureIntro(
                        name = "Meal Logging",
                        description = "Take a quick photo of your meal and get instant nutrition insights without manual tracking.",
                        icon = "📸"
                    ),
                    followUpMessage = followUp.message,
                    followUpReplyType = followUp.replyType ?: "text",
                    followUpReplies = followUp.replies
                ),
                profileData = currentProfileDataSnapshot(),
                nextAction = "continue"
            )
        }

        if (!goalByWhen.isNullOrBlank() && "progress_charts" !in shownFeatureKeys) {
            shownFeatureKeys += "progress_charts"
            return AIResponse(
                featureIntro = FeatureIntroResponse(
                    pretext = "Awesome timeline — this makes your plan much more focused. Let me show you something that keeps motivation high.",
                    feature = FeatureIntro(
                        name = "Progress Charts",
                        description = "Beautiful charts show your progress over time so you can see every win.",
                        icon = "📊"
                    ),
                    followUpMessage = followUp.message,
                    followUpReplyType = followUp.replyType ?: "text",
                    followUpReplies = followUp.replies
                ),
                profileData = currentProfileDataSnapshot(),
                nextAction = "continue"
            )
        }

        if (!name.isNullOrBlank() && "ai_coach" !in shownFeatureKeys) {
            shownFeatureKeys += "ai_coach"
            return AIResponse(
                featureIntro = FeatureIntroResponse(
                    pretext = "Great to meet you, $name. You’ll have a coach with you at every step.",
                    feature = FeatureIntro(
                        name = "AI Health Coach",
                        description = "Get personalized suggestions, fast answers, and encouragement whenever you need it.",
                        icon = "🤖"
                    ),
                    followUpMessage = followUp.message,
                    followUpReplyType = followUp.replyType ?: "text",
                    followUpReplies = followUp.replies
                ),
                profileData = currentProfileDataSnapshot(),
                nextAction = "continue"
            )
        }

        if (!activityLevel.isNullOrBlank() && "hydration_or_fasting" !in shownFeatureKeys) {
            shownFeatureKeys += "hydration_or_fasting"
            val feature = if (goalType == "weight") {
                FeatureIntro(
                    name = "Fasting Timer",
                    description = "Track fasting windows with smart reminders and clear visual progress.",
                    icon = "⏰"
                )
            } else {
                FeatureIntro(
                    name = "Water Tracking",
                    description = "Quick tap hydration logging with gentle reminders to stay consistent.",
                    icon = "💧"
                )
            }
            return AIResponse(
                featureIntro = FeatureIntroResponse(
                    pretext = "Nice, that activity level helps personalize things really well. Here’s a feature you’ll likely use daily.",
                    feature = feature,
                    followUpMessage = followUp.message,
                    followUpReplyType = followUp.replyType ?: "text",
                    followUpReplies = followUp.replies
                ),
                profileData = currentProfileDataSnapshot(),
                nextAction = "continue"
            )
        }

        return null
    }
    
    private fun addAmigoMessage(
        text: String, 
        replyType: String,
        replies: List<String>? = null,
        feature: FeatureIntro? = null,
        isFeatureIntro: Boolean = false,
        delayAfterPrevious: Long = 0
    ) {
        val message = ConversationMessage(
            id = generateMessageId(),
            text = text,
            isFromAmigo = true,
            timestamp = currentTimeMillis(),
            replyType = replyType,
            replies = replies,
            feature = feature,
            isFeatureIntro = isFeatureIntro,
            delayAfterPrevious = delayAfterPrevious
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
    
    private fun addUserMessage(text: String) {
        val message = ConversationMessage(
            id = generateMessageId(),
            text = text,
            isFromAmigo = false,
            timestamp = currentTimeMillis(),
            replyType = "text" // User messages don't have reply options
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
    
    private fun generateMessageId(): String {
        return "msg_${currentTimeMillis()}_${(0..9999).random()}"
    }
    
    /**
     * Get collected profile data
     */
    fun getProfileData(): Map<String, String> {
        return profileData.toMap()
    }
    
    /**
     * Check if onboarding is complete
     */
    fun isOnboardingComplete(): Boolean {
        return _conversationState.value is OnboardingState.Complete
    }
}

/**
 * AI Response structure
 */
@Serializable
data class AIResponse(
    val message: String = "",
    val replyType: String? = null, // "quick_pills", "list", "yes_no", "text", "date" - nullable for feature intros
    val replies: List<String>? = null,
    val featureIntro: FeatureIntroResponse? = null,
    val profileData: ProfileData? = null,
    val nextAction: String? = null
)

@Serializable
data class ProfileData(
    val goalType: String? = null, // "weight", "fitness", "health", etc.
    @JsonNames("goalDetail", "goalUnit")
    val goalDetail: String? = null, // "reduce_target_weight", "build_muscle", "become_athlete", etc.
    val goalNumber: String? = null, // "75", "10", etc.
    val goalByWhen: String? = null, // Date in YYYY-MM-DD format
    val name: String? = null,
    val age: String? = null,
    val height: String? = null,
    val weight: String? = null,
    val activityLevel: String? = null,
    val dietaryPreferences: String? = null
)

@Serializable
data class FeatureIntroResponse(
    val pretext: String, // Context for introducing the feature
    val feature: FeatureIntro,
    val followUpMessage: String, // The actual question after feature intro
    val followUpReplyType: String, // REQUIRED reply type
    val followUpReplies: List<String>? = null
)

@Serializable
data class FeatureIntro(
    val name: String,
    val description: String,
    val icon: String
)

/**
 * Onboarding conversation states
 */
sealed class OnboardingState {
    object Initial : OnboardingState()
    object Collecting : OnboardingState()
    object RequestingPermissions : OnboardingState()
    object Complete : OnboardingState()
}

/**
 * Conversation message model
 */
@Serializable
data class ConversationMessage(
    val id: String,
    val text: String,
    val isFromAmigo: Boolean,
    val timestamp: Long,
    val replyType: String, // REQUIRED: "quick_pills", "list", "yes_no", "text", "date"
    val replies: List<String>? = null,
    val feature: FeatureIntro? = null,
    val isFeatureIntro: Boolean = false, // True if this is just a feature card
    val delayAfterPrevious: Long = 0, // Milliseconds to wait before showing this message
    val isDisabled: Boolean = false // True if user has already responded to this message
)

// Platform-specific time function
expect fun currentTimeMillis(): Long

package com.amigo.shared.ai

import com.amigo.shared.ai.actions.ActionContext
import com.amigo.shared.ai.actions.ActionGroupRegistry
import com.amigo.shared.ai.models.ConversationMessage
import com.amigo.shared.ai.models.OnboardingState
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.config.AppConfig
import com.amigo.shared.utils.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.ktor.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.amigo.shared.utils.TimeProvider

class AmigoAgentConversation(
    private val bedrockClient: BedrockClient,
    private val sessionManager: SessionManager,
    private val supabaseClient: SupabaseClient? = null
) {
    private companion object {
        // Agent IDs are resolved server-side by the Lambda — no client-side IDs needed
        const val JSON_CONTRACT_HINT = "Respond ONLY in valid JSON with keys: type, version, session_context{cap,responsibilities,collect_data,collect_metrics}, aimofchat{name,status}, ui{render{type,text,data[]},tone,next_question}, input{type,options}, data{collected,metrics}, missing_fields, error. Do NOT include actions. Input rules: if options count <= 5 use input.type=quick_pills; use input.type=list only when options count > 5. For yes/no style questions use input.type=yes_no and provide exactly 2 option labels (labels may vary, e.g., Want/Don't want, Like/Unlike), and each yes/no label must be under 20 characters. For date fields use input.type=date and expect value in yyyy-MM-dd. For weight fields use input.type=weight and expect numeric value in kg."
        const val MAX_INFO_AUTO_ACK_CHAIN = 2
    }

    private val _conversationState = MutableStateFlow<OnboardingState>(OnboardingState.Initial)
    val conversationState: StateFlow<OnboardingState> = _conversationState.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val profileData = mutableMapOf<String, String>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var agentSessionId: String = ""
    private var sessionCap: String = "onboarding"
    private var sessionContextJson: String = "{}"
    // New: holds the SessionConfigPayload for the first message; null after first call
    private var pendingSessionConfig: SessionConfigPayload? = null
    private var invocationRecursionDepth: Int = 0
    private val MAX_INVOCATION_RECURSION_DEPTH = 5
    // Accumulated data_collected across turns (sent back to Lambda each turn)
    private var accumulatedDataCollected: kotlinx.serialization.json.JsonElement? = null
    // Subscription status received from Lambda (defaults to "free")
    var subscriptionStatus: String = "free"
        private set

    /**
     * Start a session using a predefined SessionConfig.
     * This is the recommended way to start sessions.
     */
    suspend fun startSessionWithConfig(config: SessionConfig): Result<Unit> {
        return startSession(
            hat = config.hat,
            responsibilities = config.responsibilities,
            data_to_be_collected = config.data_to_be_collected,
            data_to_be_calculated = config.data_to_be_calculated,
            initial_message = config.initial_message
        )
    }

    /**
     * Start a session using a config name (e.g., "onboarding", "goal_setting").
     * Loads the config from SessionConfigs.
     */
    suspend fun startSessionByName(configName: String): Result<Unit> {
        val config = SessionConfigs.getConfig(configName)
            ?: return Result.failure(IllegalArgumentException("Unknown session config: $configName"))
        return startSessionWithConfig(config)
    }

    suspend fun startSession(
        hat: String,
        responsibilities: List<String>,
        data_to_be_collected: List<String>,
        data_to_be_calculated: List<String> = emptyList(),
        initial_message: String = "Let's start this session."
    ): Result<Unit> {
        return try {
            Logger.i("AmigoAgentConversation", "🚀 ========== START SESSION ==========")
            Logger.i("AmigoAgentConversation", "🚀 Hat: $hat")
            Logger.i("AmigoAgentConversation", "🚀 Responsibilities: ${responsibilities.size} items")
            Logger.i("AmigoAgentConversation", "🚀 Collect data: ${data_to_be_collected.size} fields")
            Logger.i("AmigoAgentConversation", "🚀 Collect metrics: ${data_to_be_calculated.size} metrics")
            Logger.i("AmigoAgentConversation", "🚀 Initial message: $initial_message")
            
            _conversationState.value = OnboardingState.Collecting
            conversationHistory.clear()
            profileData.clear()
            _messages.value = emptyList()
            accumulatedDataCollected = null

            sessionCap = hat
            agentSessionId = "${hat}-${TimeProvider.currentTimeMillis()}"
            Logger.i("AmigoAgentConversation", "🚀 Generated session ID: $agentSessionId")

            // Build SessionConfigPayload for the first message
            pendingSessionConfig = SessionConfigPayload(
                hat = hat,
                responsibilities = responsibilities,
                dataToBeCollected = data_to_be_collected,
                dataToBeCalculated = data_to_be_calculated,
                notes = emptyList(),
                initial_message = initial_message
            )
            Logger.i("AmigoAgentConversation", "🚀 SessionConfigPayload built for hat=$hat")

            // Keep legacy sessionContextJson for backward compat with buildSessionContext
            sessionContextJson = buildSessionContext(
                hat = hat,
                responsibilities = responsibilities,
                data_to_be_collected = data_to_be_collected,
                data_to_be_calculated = data_to_be_calculated
            )
            Logger.i("AmigoAgentConversation", "🚀 Session context JSON length: ${sessionContextJson.length} chars")

            Logger.i("AmigoAgentConversation", "🚀 Calling requestAgent...")
            val response = requestAgent(
                userMessage = initial_message,
                includeSessionPrefix = true
            )
            Logger.i("AmigoAgentConversation", "🚀 requestAgent returned, handling response...")
            handleAgentResponse(response)
            Logger.i("AmigoAgentConversation", "🚀 ========== START SESSION SUCCESS ==========")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("AmigoAgentConversation", "❌ Failed to start session: ${e.message}")
            Logger.e("AmigoAgentConversation", "❌ Exception type: ${e::class.simpleName}")
            Logger.e("AmigoAgentConversation", "❌ Stack trace: ${e.stackTraceToString()}")
            Logger.e("AmigoAgentConversation", "🚀 ========== START SESSION FAILED ==========")
            Result.failure(e)
        }
    }

    suspend fun processUserResponse(response: String): Result<Unit> {
        return try {
            if (_isTyping.value) {
                return Result.success(Unit)
            }

            disableLastAmigoMessage()
            addUserMessage(response)
            _isTyping.value = true

            val agent = requestAgent(userMessage = response)
            handleAgentResponse(agent)

            _isTyping.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _isTyping.value = false
            Logger.e("AmigoAgentConversation", "Failed to process response: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun processQuickReply(option: String): Result<Unit> {
        return processUserResponse(option)
    }

    fun getProfileData(): Map<String, String> = profileData.toMap()

    fun getMessagesSnapshot(): List<ConversationMessage> = conversationHistory.toList()

    private suspend fun requestAgent(
        userMessage: String,
        includeSessionPrefix: Boolean = false
    ): AmigoAgentApiResponse {
        val agentMessage = userMessage

        val firstCompletion = invokeAgentForCompletion(agentMessage)
            ?: return AmigoAgentApiResponse(
                ui = AmigoAgentUi(
                    render = AmigoAgentRender(
                        type = "message",
                        text = "I’m having trouble connecting right now. Please try again."
                    )
                ),
                input = AmigoAgentInput(type = "text"),
                error = "Agent call failed"
            )

        val firstParsed = parseAgentResponse(firstCompletion)
        if (firstParsed != null) {
            return firstParsed
        }

        Logger.w("AmigoAgentConversation", "Agent response was not valid JSON. Requesting JSON-only retry.")
        val jsonOnlyMessage = "Your last response was not valid JSON. Please respond with a JSON object only."
        val retryCompletion = invokeAgentForCompletion(jsonOnlyMessage)
        val retryParsed = retryCompletion?.let { parseAgentResponse(it) }
        if (retryParsed != null) {
            return retryParsed
        }

        return AmigoAgentApiResponse(
            ui = AmigoAgentUi(
                render = AmigoAgentRender(
                    type = "message",
                    text = "I apologize, I had trouble reading that response. Could you rephrase?"
                )
            ),
            input = AmigoAgentInput(type = "text"),
            error = "Invalid non-JSON agent response"
        )
    }

    private suspend fun invokeAgentForCompletion(agentMessage: String, invocationId: String? = null, invocationResults: List<InvocationResult>? = null): String? {
        Logger.i("AmigoAgentConversation", "📞 ========== INVOKE AGENT FOR COMPLETION ==========")
        
        // If we have invocation results, send empty message and let Lambda handle returnControlInvocationResults
        val messageToSend = if (invocationId != null && invocationResults != null) {
            Logger.i("AmigoAgentConversation", "📋 Sending invocation results via returnControlInvocationResults (empty message)")
            ""
        } else {
            Logger.i("AmigoAgentConversation", "📋 Sending regular message")
            agentMessage
        }
        
        logLargePayload("PROMPT", messageToSend.ifEmpty { "[empty - using returnControlInvocationResults]" })

        Logger.i("AmigoAgentConversation", "📞 Calling bedrockClient.invokeAgent...")
        Logger.i("AmigoAgentConversation", "📞 Session ID: $agentSessionId")
        Logger.i("AmigoAgentConversation", "📞 Hat: $sessionCap")

        // Build returnControlInvocationResults if we have results to send back
        val returnControlResults = if (invocationId != null && invocationResults != null && invocationResults.isNotEmpty()) {
            Logger.i("AmigoAgentConversation", "📋 Building returnControlInvocationResults for invocationId: $invocationId with ${invocationResults.size} results")
            val functionResults = invocationResults.map { result ->
                com.amigo.shared.ai.FunctionResult(
                    actionGroup = result.actionGroup,
                    functionName = result.functionName,
                    success = result.success,
                    result = result.result,
                    error = result.error
                )
            }
            listOf(com.amigo.shared.ai.ReturnControlResult(
                invocationId = invocationId,
                functionResults = functionResults
            ))
        } else {
            if (invocationId != null && (invocationResults == null || invocationResults.isEmpty())) {
                Logger.w("AmigoAgentConversation", "⚠️ WARNING: invocationId present but no results to send (invocationResults is ${if (invocationResults == null) "null" else "empty"})")
            }
            null
        }

        // Consume pendingSessionConfig on first call; null on subsequent calls
        val sessionConfigToSend = pendingSessionConfig
        if (sessionConfigToSend != null) {
            pendingSessionConfig = null
            Logger.i("AmigoAgentConversation", "📞 Sending sessionConfig (first message) for hat=${sessionConfigToSend.hat}")
        }

        val result = bedrockClient.invokeAgent(
            message = messageToSend,
            sessionId = agentSessionId,
            agentId = "",
            agentAliasId = "",
            sessionConfig = sessionConfigToSend,
            returnControlInvocationResults = returnControlResults,
            dataCollected = if (sessionConfigToSend == null) accumulatedDataCollected else null
        )

        if (result.isFailure) {
            Logger.e("AmigoAgentConversation", "❌ Agent call failed: ${result.exceptionOrNull()?.message}")
            Logger.e("AmigoAgentConversation", "❌ Exception type: ${result.exceptionOrNull()?.let { it::class.simpleName }}")
            Logger.e("AmigoAgentConversation", "📞 ========== INVOKE AGENT FAILED ==========")
            return null
        }

        val bedrockResponse = result.getOrNull()
        if (bedrockResponse == null) {
            Logger.e("AmigoAgentConversation", "❌ Agent response is null")
            Logger.e("AmigoAgentConversation", "📞 ========== INVOKE AGENT NULL RESPONSE ==========")
            return null
        }

        Logger.i("AmigoAgentConversation", "✅ Agent call succeeded")

        // Check for error field in response
        if (bedrockResponse.error != null) {
            Logger.e("AmigoAgentConversation", "❌ Agent returned error: ${bedrockResponse.error}")
            return null
        }

        // Handle RETURN_CONTROL invocations if present
        if (!bedrockResponse.invocations.isNullOrEmpty()) {
            Logger.i("AmigoAgentConversation", "📋 Processing ${bedrockResponse.invocations.size} action invocations (recursion depth: $invocationRecursionDepth/$MAX_INVOCATION_RECURSION_DEPTH)")

            if (invocationRecursionDepth >= MAX_INVOCATION_RECURSION_DEPTH) {
                Logger.e("AmigoAgentConversation", "❌ Max invocation recursion depth exceeded! Stopping to prevent infinite loop.")
                invocationRecursionDepth = 0
                // Serialize completion JsonObject back to string for parseAgentResponse
                return bedrockResponse.completion?.let { json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), it) }
            }

            invocationRecursionDepth++
            val processedResults = processActionInvocations(bedrockResponse.invocations)

            Logger.i("AmigoAgentConversation", "📋 Sending invocation results back to agent via returnControlInvocationResults...")
            val recursiveResult = invokeAgentForCompletion(
                agentMessage = "",
                invocationId = bedrockResponse.invocationId,
                invocationResults = processedResults
            )
            invocationRecursionDepth--
            return recursiveResult
        }

        // Serialize completion JsonObject back to string for parseAgentResponse
        val completionString = bedrockResponse.completion?.let {
            json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), it)
        }

        // Update accumulated data_collected from Lambda response
        if (bedrockResponse.dataCollected != null) {
            accumulatedDataCollected = bedrockResponse.dataCollected
        }

        // Update subscription status from Lambda response
        if (bedrockResponse.subscriptionStatus != null) {
            subscriptionStatus = bedrockResponse.subscriptionStatus
            Logger.i("AmigoAgentConversation", "📋 Subscription status updated: $subscriptionStatus")
        }

        logLargePayload("RESPONSE", completionString ?: "<null completion>")
        Logger.i("AmigoAgentConversation", "📞 ========== INVOKE AGENT SUCCESS ==========")
        return completionString
    }

    /**
     * Process action invocations from Bedrock Agent using the ActionGroupRegistry.
     * 
     * This method:
     * 1. Ensures we have a valid access token (auto-refreshes if needed)
     * 2. Extracts the user ID from the Supabase session
     * 3. Creates an ActionContext with authentication information
     * 4. Executes each invocation through the ActionGroupRegistry
     * 5. Returns results for each invocation
     * 
     * @param invocations List of function invocations from the Bedrock Agent
     * @return List of invocation results (success or failure for each)
     */
    private suspend fun processActionInvocations(
        invocations: List<FunctionInvocation>
    ): List<InvocationResult> {
        // Get fresh access token - this will auto-refresh if needed
        Logger.i("AmigoAgentConversation", "📋 Getting fresh access token...")
        val accessToken = sessionManager.getAccessToken()
        
        if (accessToken == null) {
            Logger.e("AmigoAgentConversation", "❌ No valid access token - user not authenticated")
            return invocations.map { invocation ->
                InvocationResult(
                    actionGroup = invocation.actionGroup,
                    functionName = invocation.functionName,
                    success = false,
                    error = "Authentication required"
                )
            }
        }
        
        Logger.i("AmigoAgentConversation", "✅ Access token retrieved (first 20 chars): ${accessToken.take(20)}...")
        
        // Get user ID from SessionManager (which handles the SDK 3.x workaround internally)
        val userId = sessionManager.getCurrentUser()?.id
        
        // Consider authenticated if we have both userId and a valid access token
        val hasValidToken = accessToken.isNotBlank()
        val isAuthenticated = !userId.isNullOrBlank() && hasValidToken
        
        Logger.i("ProfileManager", "📋 User ID: ${userId ?: "null"}, hasValidToken: $hasValidToken, authenticated: $isAuthenticated")
        
        val context = ActionContext(
            userId = userId,
            sessionId = agentSessionId,
            isAuthenticated = isAuthenticated,
            supabaseClient = supabaseClient,
            additionalContext = mapOf(
                "cap" to sessionCap,
                "profileData" to profileData
            )
        )
        
        return invocations.map { invocation ->
            Logger.i("AmigoAgentConversation", "Executing: ${invocation.actionGroup}.${invocation.functionName}")
            
            val result = ActionGroupRegistry.executeInvocation(invocation, context)
            
            if (result.isSuccess) {
                Logger.i("AmigoAgentConversation", "✅ ${invocation.functionName} succeeded")
                InvocationResult(
                    actionGroup = invocation.actionGroup,
                    functionName = invocation.functionName,
                    success = true,
                    result = result.getOrNull()?.toString() ?: "{}"
                )
            } else {
                Logger.e("AmigoAgentConversation", "❌ ${invocation.functionName} failed: ${result.exceptionOrNull()?.message}")
                InvocationResult(
                    actionGroup = invocation.actionGroup,
                    functionName = invocation.functionName,
                    success = false,
                    error = result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Build a message containing invocation results to send back to the agent
     */
    private fun buildInvocationResultsMessage(results: List<InvocationResult>): String {
        val resultsJson = results.joinToString(",\n") { result ->
            if (result.success) {
                """
                {
                  "action_group": "${result.actionGroup}",
                  "function": "${result.functionName}",
                  "status": "success",
                  "result": ${result.result}
                }
                """.trimIndent()
            } else {
                """
                {
                  "action_group": "${result.actionGroup}",
                  "function": "${result.functionName}",
                  "status": "error",
                  "error": "${result.error}"
                }
                """.trimIndent()
            }
        }
        
        return """
            FUNCTION_RESULTS: [
              $resultsJson
            ]
            
            Continue the conversation based on these function results.
        """.trimIndent()
    }

    private fun logLargePayload(label: String, payload: String) {
        Logger.i("AmigoAgentConversation", "${label}_START")

        if (payload.isBlank()) {
            Logger.i("AmigoAgentConversation", "${label}_PART_1/1: <empty>")
            Logger.i("AmigoAgentConversation", "${label}_END")
            return
        }

        val chunks = payload.chunked(900)
        chunks.forEachIndexed { index, chunk ->
            Logger.i("AmigoAgentConversation", "${label}_PART_${index + 1}/${chunks.size}: $chunk")
        }

        Logger.i("AmigoAgentConversation", "${label}_END")
    }

    private fun parseAgentResponse(completion: String): AmigoAgentApiResponse? {
        var jsonString = completion.trim()

        if (jsonString.contains("```json")) {
            jsonString = jsonString.substringAfter("```json").substringBefore("```").trim()
        } else if (jsonString.contains("```")) {
            jsonString = jsonString.substringAfter("```").substringBefore("```").trim()
        }

        val jsonStart = jsonString.indexOf("{")
        val jsonEnd = jsonString.lastIndexOf("}") + 1
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return null
        }

        return try {
            val cleaned = jsonString.substring(jsonStart, jsonEnd)
            json.decodeFromString<AmigoAgentApiResponse>(cleaned)
        } catch (e: Exception) {
            Logger.e("AmigoAgentConversation", "Failed to parse agent response: ${e.message}")
            null
        }
    }

    private fun jsonElementToString(element: JsonElement): String? {
        runCatching { element.jsonPrimitive.contentOrNull }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val preferred = listOf("value", "text", "label", "name")
            .asSequence()
            .mapNotNull { key -> obj[key]?.let { jsonElementToString(it) } }
            .firstOrNull { it.isNotBlank() }

        return preferred
    }

    private suspend fun handleAgentResponse(
        response: AmigoAgentApiResponse,
        infoAutoAckDepth: Int = 0,
        inputTypeRetryCount: Int = 0
    ) {
        response.data?.collected?.forEach { (key, value) ->
            jsonElementToString(value)?.let { profileData[key] = it }
        }

        // Validate input.type - if "none" or invalid, ask agent to respond with valid input.type
        val inputType = response.input?.type?.lowercase().orEmpty()
        val validInputTypes = setOf("text", "quick_pills", "dropdown", "yes_no", "date", "weight")
        
        if (inputType !in validInputTypes && inputType.isNotEmpty()) {
            if (inputTypeRetryCount < 2) {
                Logger.w("AmigoAgentConversation", "⚠️ Agent sent invalid input.type='$inputType' (retry $inputTypeRetryCount/2) - asking agent to respond with valid input.type")
                val correctionMessage = "You sent input.type='$inputType' which is INVALID. You MUST respond again with a valid input.type. Valid values are: text, quick_pills, dropdown, yes_no, date, or weight. Respond immediately with the SAME message but with a valid input.type."
                val followUp = requestAgent(userMessage = correctionMessage)
                handleAgentResponse(followUp, infoAutoAckDepth, inputTypeRetryCount + 1)
                return
            } else {
                Logger.e("AmigoAgentConversation", "❌ Agent failed to provide valid input.type after 2 retries. Normalizing '$inputType' to 'text' to prevent infinite loop.")
                // Continue processing with normalized type - will be handled by normalizeReplyType below
            }
        }

        // Normalize render type: only "message" and "message_with_summary" are valid, everything else becomes "info"
        val rawRenderType = response.ui?.render?.type?.lowercase().orEmpty()
        val renderType = when (rawRenderType) {
            "message", "message_with_summary" -> rawRenderType
            else -> "info"
        }
        val messageText = response.ui?.render?.text?.takeIf { it.isNotBlank() }
            ?: "Let’s continue."

        val replies = response.input?.options
            ?.mapNotNull { option ->
                val obj = runCatching { option.jsonObject }.getOrNull()
                if (obj != null) {
                    jsonElementToString(obj["label"] ?: obj["text"] ?: obj["title"] ?: obj["name"] ?: obj["value"] ?: option)
                } else {
                    jsonElementToString(option)
                }
            }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }

        val collected = response.data?.collected ?: emptyMap()

        fun valueOf(field: String): String? {
            return collected[field]?.let { jsonElementToString(it) }
        }

        fun humanizeField(field: String): String {
            return field
                .replace("_", " ")
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { token ->
                    token.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                    }
                }
        }

        fun summaryLineOf(item: JsonElement): String? {
            runCatching { item.jsonPrimitive.contentOrNull }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }

            val obj = runCatching { item.jsonObject }.getOrNull() ?: return null

            val varName = obj["var_name_in_collected"]?.let { jsonElementToString(it) }?.trim().orEmpty()
            val explicitLabel = listOf("label", "title", "name")
                .asSequence()
                .mapNotNull { key -> obj[key]?.let { jsonElementToString(it) }?.trim() }
                .firstOrNull { it.isNotEmpty() }

            val resolvedLabel = explicitLabel
                ?: varName.takeIf { it.isNotEmpty() }?.let(::humanizeField)

            val directValue = listOf("value", "text", "content", "current_value")
                .asSequence()
                .mapNotNull { key -> obj[key]?.let { jsonElementToString(it) }?.trim() }
                .firstOrNull { it.isNotEmpty() }

            val valueFromCollected = varName
                .takeIf { it.isNotEmpty() }
                ?.let(::valueOf)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val resolvedValue = directValue ?: valueFromCollected

            return when {
                !resolvedLabel.isNullOrBlank() && !resolvedValue.isNullOrBlank() -> "$resolvedLabel: $resolvedValue"
                !resolvedLabel.isNullOrBlank() -> resolvedLabel
                !resolvedValue.isNullOrBlank() -> resolvedValue
                else -> null
            }
        }

        val renderItems = response.ui?.render?.data
            ?.mapNotNull(::summaryLineOf)
            ?.takeIf { it.isNotEmpty() }

        val expectedField = response.ui?.next_question ?: response.missing_fields?.firstOrNull()
        val baseReplyType = normalizeReplyType(
            inputType = response.input?.type,
            replies = replies,
            expectedField = expectedField,
            messageText = messageText
        )
        val replyType = if (renderType == "info") "none" else baseReplyType
        val normalizedReplies = normalizeReplies(replyType = replyType, replies = replies)

        addAmigoMessage(
            text = messageText,
            replyType = replyType,
            replies = normalizedReplies,
            renderType = renderType.ifBlank { null },
            renderItems = renderItems
        )

        // Check completion from new schema (status_of_aim) or legacy schema (aimofchat.status)
        val statusOfAim = response.statusOfAim?.lowercase().orEmpty()
        val legacyStatus = response.aimofchat?.status?.lowercase().orEmpty()
        val shouldComplete = statusOfAim == "completed" || legacyStatus in setOf("complete", "completed", "done", "closed")
        _conversationState.value = if (shouldComplete) OnboardingState.Complete else OnboardingState.Collecting

        if (renderType == "info" && !shouldComplete) {
            if (infoAutoAckDepth < MAX_INFO_AUTO_ACK_CHAIN) {
                val followUp = requestAgent(userMessage = "ok")
                handleAgentResponse(followUp, infoAutoAckDepth + 1)
            } else {
                // Max auto-ack chain hit — agent is stuck in info loop.
                // Show a user-facing error instead of silently hanging.
                Logger.w("AmigoAgentConversation", "⚠️ Max info auto-ack chain ($MAX_INFO_AUTO_ACK_CHAIN) hit — showing error to user")
                val errorResponse = AmigoAgentApiResponse(
                    ui = AmigoAgentUi(
                        render = AmigoAgentRender(
                            type = "message",
                            text = "I'm having trouble connecting right now. Please try again."
                        )
                    ),
                    input = AmigoAgentInput(type = "text"),
                    error = "Max info auto-ack chain exceeded"
                )
                addAmigoMessage(
                    text = errorResponse.ui?.render?.text ?: "I'm having trouble connecting right now. Please try again.",
                    replyType = "text",
                    replies = null,
                    renderType = "message"
                )
            }
        }
    }

    private fun normalizeReplyType(
        inputType: String?,
        replies: List<String>?,
        expectedField: String?,
        messageText: String
    ): String {
        val normalizedInputType = inputType?.lowercase().orEmpty()

        return when (normalizedInputType) {
            "none" -> "none"
            "quick_pills" -> "quick_pills"
            "dropdown", "list", "select" -> if ((replies?.size ?: 0) <= 5) "quick_pills" else "list"
            "yes_no" -> "yes_no"
            "date", "date_picker" -> "date"
            "weight", "weight_scale" -> "weight"
            else -> {
                if (shouldForceDateInput(normalizedInputType, expectedField, messageText)) {
                    "date"
                } else if (isBooleanOptions(replies)) {
                    "yes_no"
                } else {
                    when {
                        replies == null -> "text"
                        replies.size <= 5 -> "quick_pills"
                        else -> "list"
                    }
                }
            }
        }
    }

    private fun normalizeReplies(replyType: String, replies: List<String>?): List<String>? {
        return when (replyType) {
            "yes_no" -> {
                val cleaned = replies
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    .orEmpty()
                when {
                    cleaned.size >= 2 -> cleaned.take(2)
                    else -> listOf("Yes", "No")
                }
            }
            else -> replies
        }
    }

    private fun shouldForceDateInput(inputType: String, expectedField: String?, messageText: String): Boolean {
        if (inputType == "date" || inputType == "date_picker") {
            return true
        }

        if (inputType == "yes_no" || inputType == "quick_pills" || inputType == "dropdown" || inputType == "list" || inputType == "select") {
            return false
        }

        val normalizedField = expectedField?.lowercase().orEmpty()

        val fieldLooksLikeDate = normalizedField.contains("date") ||
            normalizedField.contains("by_when") ||
            normalizedField.contains("deadline") ||
            normalizedField.contains("dob") ||
            normalizedField.contains("birth")

        return fieldLooksLikeDate
    }

    private fun isBooleanOptions(replies: List<String>?): Boolean {
        val cleaned = replies
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

        if (cleaned.size != 2) {
            return false
        }

        val yesLike = setOf("yes", "y", "want", "like", "accept", "include", "enable", "true", "ok", "okay")
        val noLike = setOf("no", "n", "don't want", "do not want", "dont want", "unlike", "decline", "exclude", "disable", "false")

        val first = cleaned[0]
        val second = cleaned[1]

        val directPair = (first in yesLike && second in noLike) || (first in noLike && second in yesLike)
        if (directPair) {
            return true
        }

        return (first.contains("want") && second.contains("want") && first != second) ||
            (first.contains("like") && second.contains("like") && first != second)
    }

    private fun disableLastAmigoMessage() {
        val lastAmigoIndex = conversationHistory.indexOfLast { it.isFromAmigo && it.replyType != "text" }
        if (lastAmigoIndex >= 0) {
            val lastMessage = conversationHistory[lastAmigoIndex]
            conversationHistory[lastAmigoIndex] = lastMessage.copy(isDisabled = true)
            _messages.value = conversationHistory.toList()
        }
    }

    private fun addAmigoMessage(
        text: String,
        replyType: String,
        replies: List<String>? = null,
        renderType: String? = null,
        renderItems: List<String>? = null
    ) {
        val message = ConversationMessage(
            id = generateMessageId(),
            text = text,
            isFromAmigo = true,
            timestamp = TimeProvider.currentTimeMillis(),
            replyType = replyType,
            replies = replies,
            renderType = renderType,
            renderItems = renderItems
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }

    private fun addUserMessage(text: String) {
        val message = ConversationMessage(
            id = generateMessageId(),
            text = text,
            isFromAmigo = false,
            timestamp = TimeProvider.currentTimeMillis(),
            replyType = "text"
        )
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }

    private suspend fun buildSessionContext(
        hat: String,
        responsibilities: List<String>,
        data_to_be_collected: List<String>,
        data_to_be_calculated: List<String>
    ): String {
        // Get user ID from SessionManager (which handles the SDK 3.x workaround internally)
        val userId = sessionManager.getCurrentUser()?.id.orEmpty()
        
        val responsibilitiesJson = responsibilities.joinToString(",\n") { "\"$it\"" }
        val collectDataJson = data_to_be_collected.joinToString(",\n") { "\"$it\"" }
        val metricsJson = data_to_be_calculated.joinToString(",\n") { "\"$it\"" }
        val metricsBlock = if (data_to_be_calculated.isNotEmpty()) {
            ",\n  \"data_to_be_calculated\": [\n    $metricsJson\n  ]"
        } else {
            ""
        }

        return """
            {
              "hat": "$hat",
              "user_id": "$userId",
              "responsibilities": [
                $responsibilitiesJson
              ],
              "data_to_be_collected": [
                $collectDataJson
              ]$metricsBlock
            }
        """.trimIndent()
    }

    private fun generateMessageId(): String {
        return "msg_${TimeProvider.currentTimeMillis()}_${(0..9999).random()}"
    }
}

@Serializable
private data class AmigoAgentApiResponse(
    val type: String? = null,
    @SerialName("status_of_aim")
    val statusOfAim: String? = null,
    val ui: AmigoAgentUi? = null,
    val input: AmigoAgentInput? = null,
    val data: AmigoAgentData? = null,
    val aimofchat: AmigoAgentAimOfChat? = null,
    @SerialName("previous_field_collected")
    val previousFieldCollected: AmigoAgentFieldCollected? = null,
    val actions: List<JsonElement>? = null,
    val missing_fields: List<String>? = null,
    val error: String? = null
)

@Serializable
private data class AmigoAgentUi(
    val render: AmigoAgentRender? = null,
    val next_question: String? = null
)

@Serializable
private data class AmigoAgentRender(
    val type: String? = null,
    val text: String? = null,
    val data: List<JsonElement>? = null
)

@Serializable
private data class AmigoAgentInput(
    val type: String? = null,
    val options: List<JsonElement>? = null
)

@Serializable
private data class AmigoAgentData(
    val collected: Map<String, JsonElement> = emptyMap()
)

@Serializable
private data class AmigoAgentAimOfChat(
    val name: String? = null,
    val status: String? = null
)

@Serializable
private data class AmigoAgentFieldCollected(
    val field: String? = null,
    val label: String? = null,
    val value: String? = null
)

/**
 * Result of a function invocation
 */
private data class InvocationResult(
    val actionGroup: String,
    val functionName: String,
    val success: Boolean,
    val result: String? = null,
    val error: String? = null
)

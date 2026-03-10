package com.amigo.shared.ai

import com.amigo.shared.ai.actions.ActionContext
import com.amigo.shared.ai.actions.ActionGroupRegistry
import com.amigo.shared.ai.models.ConversationMessage
import com.amigo.shared.ai.models.OnboardingState
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.config.AppConfig
import com.amigo.shared.utils.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserInfo
import io.ktor.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val BEDROCK_AGENT_ID = AppConfig.BEDROCK_AGENT_ID
        val BEDROCK_AGENT_ALIAS_ID = AppConfig.BEDROCK_AGENT_ALIAS_ID
        const val JSON_CONTRACT_HINT = "Respond ONLY in valid JSON with keys: type, version, session_context{cap,responsibilities,collect_data,collect_metrics}, aimofchat{name,status}, ui{render{type,text,data[]},tone,next_question}, input{type,options}, data{collected,metrics}, missing_fields, error. Do NOT include actions. Input rules: if options count <= 5 use input.type=quick_pills; use input.type=list only when options count > 5. For yes/no style questions use input.type=yes_no and provide exactly 2 option labels (labels may vary, e.g., Want/Don't want, Like/Unlike), and each yes/no label must be under 20 characters. For date fields use input.type=date and expect value in yyyy-MM-dd. For weight fields use input.type=weight and expect numeric value in kg."
        const val MAX_INFO_AUTO_ACK_CHAIN = 6
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
    private var invocationRecursionDepth: Int = 0
    private val MAX_INVOCATION_RECURSION_DEPTH = 5

    /**
     * Start a session using a predefined SessionConfig.
     * This is the recommended way to start sessions.
     */
    suspend fun startSessionWithConfig(config: SessionConfig): Result<Unit> {
        return startSession(
            cap = config.cap,
            responsibilities = config.responsibilities,
            collectData = config.collectData,
            collectMetrics = config.collectMetrics,
            initialMessage = config.initialMessage
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
        cap: String,
        responsibilities: List<String>,
        collectData: List<String>,
        collectMetrics: List<String> = emptyList(),
        initialMessage: String = "Let's start this session."
    ): Result<Unit> {
        return try {
            Logger.i("AmigoAgentConversation", "🚀 ========== START SESSION ==========")
            Logger.i("AmigoAgentConversation", "🚀 Cap: $cap")
            Logger.i("AmigoAgentConversation", "🚀 Responsibilities: ${responsibilities.size} items")
            Logger.i("AmigoAgentConversation", "🚀 Collect data: ${collectData.size} fields")
            Logger.i("AmigoAgentConversation", "🚀 Collect metrics: ${collectMetrics.size} metrics")
            Logger.i("AmigoAgentConversation", "🚀 Initial message: $initialMessage")
            
            _conversationState.value = OnboardingState.Collecting
            conversationHistory.clear()
            profileData.clear()
            _messages.value = emptyList()

            sessionCap = cap
            agentSessionId = "${cap}-${TimeProvider.currentTimeMillis()}"
            Logger.i("AmigoAgentConversation", "🚀 Generated session ID: $agentSessionId")
            
            sessionContextJson = buildSessionContext(
                cap = cap,
                responsibilities = responsibilities,
                collectData = collectData,
                collectMetrics = collectMetrics
            )
            Logger.i("AmigoAgentConversation", "🚀 Session context JSON length: ${sessionContextJson.length} chars")

            Logger.i("AmigoAgentConversation", "🚀 Calling requestAgent...")
            val response = requestAgent(
                userMessage = initialMessage,
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
        val agentMessage = if (includeSessionPrefix) {
            "SESSION_CONTEXT:$sessionContextJson\n\n$JSON_CONTRACT_HINT\n\nUser message: $userMessage"
        } else {
            "$JSON_CONTRACT_HINT\n\nUser message: $userMessage"
        }

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
        val jsonOnlyMessage = "Your last response was not valid JSON. send json response only. $JSON_CONTRACT_HINT"
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
        Logger.i("AmigoAgentConversation", "📞 Agent ID: $BEDROCK_AGENT_ID")
        Logger.i("AmigoAgentConversation", "📞 Agent Alias ID: $BEDROCK_AGENT_ALIAS_ID")
        Logger.i("AmigoAgentConversation", "📞 Session ID: $agentSessionId")
        Logger.i("AmigoAgentConversation", "📞 Cap: $sessionCap")
        
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
        
        val result = bedrockClient.invokeAgent(
            message = messageToSend,
            sessionId = agentSessionId,
            agentId = BEDROCK_AGENT_ID,
            agentAliasId = BEDROCK_AGENT_ALIAS_ID,
            cap = sessionCap,
            sessionContext = sessionContextJson,
            returnControlInvocationResults = returnControlResults
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
        
        // Handle RETURN_CONTROL invocations if present
        if (!bedrockResponse.invocations.isNullOrEmpty()) {
            Logger.i("AmigoAgentConversation", "📋 Processing ${bedrockResponse.invocations.size} action invocations (recursion depth: $invocationRecursionDepth/$MAX_INVOCATION_RECURSION_DEPTH)")
            
            if (invocationRecursionDepth >= MAX_INVOCATION_RECURSION_DEPTH) {
                Logger.e("AmigoAgentConversation", "❌ Max invocation recursion depth exceeded! Stopping to prevent infinite loop.")
                invocationRecursionDepth = 0
                return bedrockResponse.completion
            }
            
            invocationRecursionDepth++
            val processedResults = processActionInvocations(bedrockResponse.invocations)
            
            Logger.i("AmigoAgentConversation", "📋 Sending invocation results back to agent via returnControlInvocationResults...")
            // Recursive call with returnControlInvocationResults
            val recursiveResult = invokeAgentForCompletion(
                agentMessage = "",  // Empty message - results go via returnControlInvocationResults
                invocationId = bedrockResponse.invocationId,
                invocationResults = processedResults
            )
            invocationRecursionDepth--
            return recursiveResult
        }

        val completion = bedrockResponse.completion
        logLargePayload("RESPONSE", completion)
        Logger.i("AmigoAgentConversation", "📞 ========== INVOKE AGENT SUCCESS ==========")
        return completion
    }

    /**
     * Process action invocations from Bedrock Agent using the ActionGroupRegistry.
     * 
     * This method:
     * 1. Ensures Supabase session is synced (imports session if needed)
     * 2. Extracts the user ID from the Supabase session (or JWT token if session.user is null)
     * 3. Creates an ActionContext with authentication information
     * 4. Executes each invocation through the ActionGroupRegistry
     * 5. Returns results for each invocation
     * 
     * @param invocations List of function invocations from the Bedrock Agent
     * @return List of invocation results (success or failure for each)
     * 
     * CRITICAL: This method MUST sync the Supabase session before executing actions,
     * otherwise database operations will fail with "No active session" errors due to RLS policies.
     */
    private suspend fun processActionInvocations(
        invocations: List<FunctionInvocation>
    ): List<InvocationResult> {
        // CRITICAL: Ensure Supabase session is synced BEFORE executing actions
        // This imports the session into Supabase so RLS policies work correctly
        Logger.i("AmigoAgentConversation", "📋 Syncing Supabase session before action execution...")
        val sessionSynced = sessionManager.ensureSessionSynced()
        Logger.i("AmigoAgentConversation", "📋 Session sync result: $sessionSynced")
        
        if (!sessionSynced) {
            Logger.w("AmigoAgentConversation", "⚠️ WARNING: Failed to sync Supabase session - database operations may fail")
        }
        
        // Get userId from Supabase client session
        Logger.i("AmigoAgentConversation", "📋 Checking Supabase session...")
        Logger.i("AmigoAgentConversation", "📋 supabaseClient is null: ${supabaseClient == null}")
        
        val userId = try {
            val session = supabaseClient?.auth?.currentSessionOrNull()
            Logger.i("AmigoAgentConversation", "📋 currentSessionOrNull() returned: ${if (session == null) "null" else "session exists"}")
            
            if (session != null) {
                Logger.i("AmigoAgentConversation", "📋 Session user id: ${session.user?.id ?: "null"}")
                Logger.i("AmigoAgentConversation", "📋 Session access token length: ${session.accessToken?.length ?: 0}")
                
                // If session.user.id is null, decode the JWT token to extract the user ID
                // This handles cases where Supabase session object is incomplete
                if (session.user?.id == null && session.accessToken != null) {
                    Logger.i("AmigoAgentConversation", "📋 Attempting to decode JWT token to extract userId...")
                    extractUserIdFromJWT(session.accessToken)
                } else {
                    session.user?.id
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.w("AmigoAgentConversation", "Could not get user from Supabase: ${e.message}")
            Logger.w("AmigoAgentConversation", "Exception type: ${e::class.simpleName}")
            null
        }
        val isAuthenticated = userId != null
        
        Logger.i("AmigoAgentConversation", "📋 Action context - userId: ${userId ?: "null"}, authenticated: $isAuthenticated")
        
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
            
            $JSON_CONTRACT_HINT
            
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
        infoAutoAckDepth: Int = 0
    ) {
        response.data?.collected?.forEach { (key, value) ->
            jsonElementToString(value)?.let { profileData[key] = it }
        }

        val renderType = response.ui?.render?.type?.lowercase().orEmpty()
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

        val status = response.aimofchat?.status?.lowercase().orEmpty()
        val shouldComplete = status in setOf("complete", "completed", "done", "closed")
        _conversationState.value = if (shouldComplete) OnboardingState.Complete else OnboardingState.Collecting

        if (renderType == "info" && !shouldComplete && infoAutoAckDepth < MAX_INFO_AUTO_ACK_CHAIN) {
            val followUp = requestAgent(userMessage = "ok")
            handleAgentResponse(followUp, infoAutoAckDepth + 1)
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

    private fun buildSessionContext(
        cap: String,
        responsibilities: List<String>,
        collectData: List<String>,
        collectMetrics: List<String>
    ): String {
        val userId = sessionManager.getCurrentUser()?.id.orEmpty()
        val responsibilitiesJson = responsibilities.joinToString(",\n") { "\"$it\"" }
        val collectDataJson = collectData.joinToString(",\n") { "\"$it\"" }
        val metricsJson = collectMetrics.joinToString(",\n") { "\"$it\"" }
        val metricsBlock = if (collectMetrics.isNotEmpty()) {
            ",\n  \"collect_metrics\": [\n    $metricsJson\n  ]"
        } else {
            ""
        }

        return """
            {
              "cap": "$cap",
              "user_id": "$userId",
              "responsibilities": [
                $responsibilitiesJson
              ],
              "collect_data": [
                $collectDataJson
              ]$metricsBlock
            }
        """.trimIndent()
    }

    private fun generateMessageId(): String {
        return "msg_${TimeProvider.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    /**
     * Extract user ID from a JWT access token.
     * 
     * JWT tokens have the format: header.payload.signature
     * The payload is base64url-encoded JSON containing claims including 'sub' (subject/user ID).
     * 
     * This method:
     * 1. Splits the token into its three parts
     * 2. Decodes the payload from base64url to a string
     * 3. Parses the JSON to extract the 'sub' claim
     * 
     * @param token The JWT access token
     * @return The user ID from the 'sub' claim, or null if extraction fails
     * 
     * Note: JWT uses base64url encoding (not standard base64), which uses '-' and '_'
     * instead of '+' and '/', and may omit padding. This method handles the conversion.
     */
    private fun extractUserIdFromJWT(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                Logger.w("AmigoAgentConversation", "JWT token doesn't have 3 parts: ${parts.size}")
                return null
            }
            
            val payload = parts[1]
            // Convert base64url to standard base64 and add padding if needed
            val base64 = payload.replace('-', '+').replace('_', '/')
            val paddedBase64 = when (base64.length % 4) {
                2 -> base64 + "=="
                3 -> base64 + "="
                else -> base64
            }
            
            val decodedString = paddedBase64.decodeBase64Bytes().decodeToString()
            Logger.i("AmigoAgentConversation", "📋 Decoded JWT payload: ${decodedString.take(200)}")
            
            // Parse JSON and extract 'sub' field (user ID)
            val jsonPayload = json.parseToJsonElement(decodedString).jsonObject
            val sub = jsonPayload["sub"]?.jsonPrimitive?.content
            Logger.i("AmigoAgentConversation", "📋 Extracted userId from JWT 'sub' field: $sub")
            sub
        } catch (e: Exception) {
            Logger.w("AmigoAgentConversation", "Failed to decode JWT: ${e.message}")
            null
        }
    }
}

@Serializable
private data class AmigoAgentApiResponse(
    val type: String? = null,
    val ui: AmigoAgentUi? = null,
    val input: AmigoAgentInput? = null,
    val data: AmigoAgentData? = null,
    val aimofchat: AmigoAgentAimOfChat? = null,
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

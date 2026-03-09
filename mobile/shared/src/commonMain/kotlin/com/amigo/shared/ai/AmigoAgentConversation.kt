package com.amigo.shared.ai

import com.amigo.shared.auth.SessionManager
import com.amigo.shared.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AmigoAgentConversation(
    private val bedrockClient: BedrockClient,
    private val sessionManager: SessionManager
) {
    private companion object {
        const val BEDROCK_AGENT_ID = "4XLAIQ6BUY"
        const val BEDROCK_AGENT_ALIAS_ID = "TSTALIASID"
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

    suspend fun startSession(
        cap: String,
        responsibilities: List<String>,
        collectData: List<String>,
        collectMetrics: List<String> = emptyList(),
        initialMessage: String = "Let's start this session."
    ): Result<Unit> {
        return try {
            _conversationState.value = OnboardingState.Collecting
            conversationHistory.clear()
            profileData.clear()
            _messages.value = emptyList()

            sessionCap = cap
            agentSessionId = "${cap}-${currentTimeMillis()}"
            sessionContextJson = buildSessionContext(
                cap = cap,
                responsibilities = responsibilities,
                collectData = collectData,
                collectMetrics = collectMetrics
            )

            val response = requestAgent(
                userMessage = initialMessage,
                includeSessionPrefix = true
            )
            handleAgentResponse(response)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("AmigoAgentConversation", "Failed to start session: ${e.message}")
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

    private suspend fun invokeAgentForCompletion(agentMessage: String): String? {
        logLargePayload("PROMPT", agentMessage)

        val result = bedrockClient.invokeAgent(
            message = agentMessage,
            sessionId = agentSessionId,
            agentId = BEDROCK_AGENT_ID,
            agentAliasId = BEDROCK_AGENT_ALIAS_ID,
            cap = sessionCap,
            sessionContext = sessionContextJson
        )

        if (result.isFailure) {
            Logger.e("AmigoAgentConversation", "Agent call failed: ${result.exceptionOrNull()?.message}")
            return null
        }

        val completion = result.getOrNull()?.completion.orEmpty()
        logLargePayload("RESPONSE", completion)
        return completion
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
            item.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

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
            timestamp = currentTimeMillis(),
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
            timestamp = currentTimeMillis(),
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
        return "msg_${currentTimeMillis()}_${(0..9999).random()}"
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

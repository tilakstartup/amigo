package com.amigo.shared.ai

import com.amigo.shared.utils.Logger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.pow

/**
 * Client for Amazon Bedrock API via Lambda proxy
 * Calls Lambda API endpoint with Supabase authentication
 */
class BedrockClient(
    private val apiEndpoint: String,
    private val getAuthToken: suspend () -> String?,
    private val maxRetries: Int = 3
) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Invoke Claude model with a text prompt via Lambda proxy
     */
    suspend fun invokeModel(
        modelId: String = "anthropic.claude-3-haiku-20240307-v1:0",
        prompt: String,
        maxTokens: Int = 2048,
        temperature: Double = 0.7,
        systemPrompt: String? = null
    ): Result<BedrockResponse> {
        return withRetry {
            Logger.i("BedrockClient", "🔵 Starting API call to $apiEndpoint")

            val authToken = getAuthToken()
            if (authToken == null) {
                Logger.e("BedrockClient", "❌ No authentication token available")
                return@withRetry Result.failure(Exception("No authentication token available"))
            }

            Logger.i("BedrockClient", "✅ Auth token obtained (length: ${authToken.length})")

            val requestBody = LambdaRequest(
                prompt = prompt,
                modelId = modelId,
                maxTokens = maxTokens,
                temperature = temperature,
                systemPrompt = systemPrompt
            )

            Logger.i("BedrockClient", "📤 Sending request - Model: $modelId, MaxTokens: $maxTokens, Temp: $temperature")

            val response = httpClient.post(apiEndpoint) {
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.Authorization, "Bearer $authToken")
                    append("X-Amigo-Auth", "Bearer $authToken")
                }
                setBody(json.encodeToString(LambdaRequest.serializer(), requestBody))
            }

            Logger.i("BedrockClient", "📥 Received response - Status: ${response.status}")

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val lambdaResponse = json.decodeFromString<LambdaModelResponse>(responseText)
                Result.success(BedrockResponse(
                    completion = null,
                    stopReason = lambdaResponse.stopReason,
                    usage = BedrockUsage(
                        inputTokens = lambdaResponse.usage.inputTokens,
                        outputTokens = lambdaResponse.usage.outputTokens
                    ),
                    completionText = lambdaResponse.completion
                ))
            } else {
                val errorText = response.bodyAsText()
                Logger.e("BedrockClient", "❌ API error - Status: ${response.status}, Body: $errorText")
                Result.failure(Exception("Lambda API error: ${response.status} - $errorText"))
            }
        }
    }

    /**
     * Invoke Bedrock Agent through Lambda proxy (with server-side return-control handling).
     *
     * @param message User message text
     * @param sessionId Session identifier
     * @param agentId Bedrock agent ID
     * @param agentAliasId Bedrock agent alias ID
     * @param sessionConfig Session configuration (only on first message; null for subsequent messages)
     * @param returnControlInvocationResults Function results from client (for return-control turns)
     */
    suspend fun invokeAgent(
        message: String,
        sessionId: String,
        agentId: String,
        agentAliasId: String,
        sessionConfig: SessionConfigPayload? = null,
        returnControlInvocationResults: List<ReturnControlResult>? = null,
        dataCollected: JsonElement? = null
    ): Result<BedrockResponse> {
        // Never retry return-control results — the invocationId is consumed on first attempt
        // and Bedrock will reject a second attempt with "no active invocationId found"
        val effectiveMaxRetries = if (!returnControlInvocationResults.isNullOrEmpty()) 1 else maxRetries
        return withRetry(maxAttempts = effectiveMaxRetries) {
            Logger.i("BedrockClient", "🔵 ========== AGENT API CALL START ==========")
            Logger.i("BedrockClient", "🔵 Endpoint: $apiEndpoint")
            Logger.i("BedrockClient", "🔵 AgentId: $agentId")
            Logger.i("BedrockClient", "🔵 AgentAliasId: $agentAliasId")
            Logger.i("BedrockClient", "🔵 SessionId: $sessionId")
            Logger.i("BedrockClient", "🔵 SessionConfig: ${if (sessionConfig != null) "present (hat=${sessionConfig.hat})" else "null"}")
            Logger.i("BedrockClient", "🔵 Message length: ${message.length} chars")

            val authToken = getAuthToken()
            Logger.i("BedrockClient", "🔑 Auth token: ${if (authToken == null) "NULL" else if (authToken.isEmpty()) "EMPTY" else "Present (${authToken.length} chars)"}")

            if (authToken == null) {
                Logger.e("BedrockClient", "❌ No authentication token available")
                return@withRetry Result.failure(Exception("No authentication token available"))
            }

            val hat = sessionConfig?.hat ?: ""
            if (authToken.isEmpty() && hat == "onboarding") {
                Logger.i("BedrockClient", "⚠️ Onboarding request without auth token (allowed)")
            }

            val requestBody = LambdaRequest(
                mode = "agent",
                message = message,
                sessionId = sessionId,
                agentId = agentId,
                agentAliasId = agentAliasId,
                sessionConfig = sessionConfig,
                returnControlInvocationResults = returnControlInvocationResults,
                dataCollected = dataCollected
            )

            val requestBodyJson = json.encodeToString(LambdaRequest.serializer(), requestBody)
            Logger.i("BedrockClient", "📤 Request body size: ${requestBodyJson.length} chars")

            try {
                val response = httpClient.post(apiEndpoint) {
                    headers {
                        append(HttpHeaders.ContentType, "application/json")
                        append(HttpHeaders.Authorization, "Bearer $authToken")
                        if (authToken.isNotEmpty()) {
                            append("X-Amigo-Auth", "Bearer $authToken")
                        }
                    }
                    setBody(requestBodyJson)
                }

                Logger.i("BedrockClient", "📥 Response received - Status: ${response.status.value} ${response.status.description}")

                if (response.status.isSuccess()) {
                    val responseText = response.bodyAsText()
                    Logger.i("BedrockClient", "📥 Response body size: ${responseText.length} chars")
                    Logger.i("BedrockClient", "📥 FULL RESPONSE: $responseText")

                    val lambdaResponse = json.decodeFromString<LambdaAgentResponse>(responseText)

                    // Check for error field
                    if (lambdaResponse.error != null) {
                        Logger.e("BedrockClient", "❌ Lambda returned error: ${lambdaResponse.error}")
                        Logger.i("BedrockClient", "🔵 ========== AGENT API CALL ERROR RESPONSE ==========")
                        return@withRetry Result.success(
                            BedrockResponse(
                                completion = null,
                                error = lambdaResponse.error,
                                invocationId = lambdaResponse.invocationId
                            )
                        )
                    }

                    if (!lambdaResponse.invocations.isNullOrEmpty()) {
                        Logger.i("BedrockClient", "📋 Received ${lambdaResponse.invocations.size} function invocations")
                        lambdaResponse.invocations.forEach { inv ->
                            Logger.i("BedrockClient", "  - ${inv.actionGroup}.${inv.functionName}(${inv.params.keys.joinToString()})")
                        }
                    }

                    Logger.i("BedrockClient", "🔵 ========== AGENT API CALL SUCCESS ==========")
                    Result.success(
                        BedrockResponse(
                            completion = lambdaResponse.completion,
                            dataCollected = lambdaResponse.dataCollected,
                            invocations = lambdaResponse.invocations,
                            invocationId = lambdaResponse.invocationId,
                            error = null,
                            subscriptionStatus = lambdaResponse.subscriptionStatus
                        )
                    )
                } else {
                    val errorText = response.bodyAsText()
                    Logger.e("BedrockClient", "❌ Agent API error - Status: ${response.status.value}")
                    Logger.e("BedrockClient", "❌ Error body: $errorText")
                    Logger.i("BedrockClient", "🔵 ========== AGENT API CALL FAILED ==========")
                    Result.failure(Exception("Lambda Agent API error: ${response.status} - $errorText"))
                }
            } catch (e: Exception) {
                Logger.e("BedrockClient", "❌ Exception during HTTP request: ${e.message}")
                Logger.e("BedrockClient", "❌ Exception type: ${e::class.simpleName}")
                Logger.i("BedrockClient", "🔵 ========== AGENT API CALL EXCEPTION ==========")
                throw e
            }
        }
    }

    /**
     * Analyze an image with Claude via Lambda proxy
     */
    suspend fun analyzeImage(
        imageData: ByteArray,
        prompt: String,
        modelId: String = "anthropic.claude-3-5-sonnet-20241022-v2:0",
        maxTokens: Int = 2048
    ): Result<BedrockResponse> {
        return Result.failure(Exception("Image analysis not yet supported via Lambda proxy"))
    }

    /**
     * Stream responses from Claude (for real-time chat)
     */
    suspend fun streamModel(
        modelId: String = "anthropic.claude-3-5-sonnet-20241022-v2:0",
        prompt: String,
        maxTokens: Int = 2048,
        temperature: Double = 0.7,
        systemPrompt: String? = null,
        onChunk: (String) -> Unit
    ): Result<BedrockResponse> {
        return invokeModel(modelId, prompt, maxTokens, temperature, systemPrompt)
    }

    /**
     * Retry logic with exponential backoff
     */
    private suspend fun <T> withRetry(maxAttempts: Int = maxRetries, block: suspend () -> Result<T>): Result<T> {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                Logger.i("BedrockClient", "🔄 Attempt ${attempt + 1}/$maxAttempts")
                val result = block()
                if (result.isSuccess) {
                    Logger.i("BedrockClient", "✅ Request succeeded on attempt ${attempt + 1}")
                    return result
                }
                lastException = result.exceptionOrNull() as? Exception
                Logger.w("BedrockClient", "⚠️ Attempt ${attempt + 1} failed: ${lastException?.message}")
            } catch (e: Exception) {
                lastException = e
                Logger.e("BedrockClient", "❌ Attempt ${attempt + 1} threw exception: ${e.message}")
            }

            if (attempt < maxAttempts - 1) {
                val delayMs = (2.0.pow(attempt) * 1000).toLong()
                Logger.i("BedrockClient", "⏳ Waiting ${delayMs}ms before retry...")
                delay(delayMs)
            }
        }

        Logger.e("BedrockClient", "❌ All $maxAttempts attempts failed. Last error: ${lastException?.message}")
        return Result.failure(lastException ?: Exception("Unknown error"))
    }

    fun close() {
        httpClient.close()
    }
}

// ─── Request models ───────────────────────────────────────────────────────────

/**
 * Unified Lambda request payload.
 * For agent mode: set mode="agent", message, sessionId, agentId, agentAliasId.
 * For model mode: set prompt, modelId, maxTokens, temperature, systemPrompt.
 */
@Serializable
private data class LambdaRequest(
    val mode: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val agentId: String? = null,
    val agentAliasId: String? = null,
    val sessionConfig: SessionConfigPayload? = null,
    val returnControlInvocationResults: List<ReturnControlResult>? = null,
    @SerialName("data_collected")
    val dataCollected: JsonElement? = null,
    val prompt: String = "",
    val modelId: String = "",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    val systemPrompt: String? = null
)

// ─── Response models ──────────────────────────────────────────────────────────

/** Lambda response for model (non-agent) invocations */
@Serializable
private data class LambdaModelResponse(
    val completion: String,
    val stopReason: String,
    val usage: LambdaUsage
)

/** Lambda response for agent invocations (new unified format) */
@Serializable
private data class LambdaAgentResponse(
    val completion: JsonObject? = null,
    @SerialName("data_collected")
    val dataCollected: JsonElement? = null,
    val invocations: List<FunctionInvocation>? = null,
    val invocationId: String? = null,
    val error: String? = null,
    val userId: String? = null,
    val timestamp: String? = null,
    @SerialName("subscription_status")
    val subscriptionStatus: String? = null
)

@Serializable
private data class LambdaUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

// ─── Public models ────────────────────────────────────────────────────────────

/**
 * Session configuration payload sent to Lambda on the first message of a session.
 * Includes all SessionConfig fields plus initial_message (which is @Transient in SessionConfig).
 */
@Serializable
data class SessionConfigPayload(
    val hat: String,
    val responsibilities: List<String>,
    @SerialName("data_to_be_collected")
    val dataToBeCollected: List<String>,
    @SerialName("data_to_be_calculated")
    val dataToBeCalculated: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val initial_message: String
)

/** Public response model returned by BedrockClient */
@Serializable
data class BedrockResponse(
    /** Agent JSON response object (null on error or for model-mode responses) */
    val completion: JsonObject? = null,
    /** Accumulated data collected across turns (null on error) */
    @SerialName("data_collected")
    val dataCollected: JsonElement? = null,
    /** Function invocations to execute client-side (null if none) */
    val invocations: List<FunctionInvocation>? = null,
    /** Invocation ID for subsequent returnControlInvocationResults */
    val invocationId: String? = null,
    /** Error message (null on success) */
    val error: String? = null,
    /** Subscription status from Lambda response (e.g. "free" or "pro") */
    @SerialName("subscription_status")
    val subscriptionStatus: String? = null,
    // Legacy fields for model-mode (invokeModel)
    val stopReason: String? = null,
    val usage: BedrockUsage? = null,
    /** Raw text completion for model-mode responses */
    val completionText: String? = null
)

@Serializable
data class BedrockUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

@Serializable
data class FunctionInvocation(
    @SerialName("action_group")
    val actionGroup: String,
    @SerialName("function_name")
    val functionName: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class ReturnControlResult(
    @SerialName("invocation_id")
    val invocationId: String,
    @SerialName("function_results")
    val functionResults: List<FunctionResult>
)

@Serializable
data class FunctionResult(
    @SerialName("action_group")
    val actionGroup: String,
    @SerialName("function_name")
    val functionName: String,
    val success: Boolean,
    val result: String? = null,
    val error: String? = null
)

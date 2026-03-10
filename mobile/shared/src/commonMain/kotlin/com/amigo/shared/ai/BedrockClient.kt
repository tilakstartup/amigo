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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
            Logger.i("BedrockClient", "📤 Prompt length: ${prompt.length} chars")
            Logger.i("BedrockClient", "📤 System prompt: ${if (systemPrompt != null) "${systemPrompt.length} chars" else "none"}")
            
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
                Logger.i("BedrockClient", "✅ Response body length: ${responseText.length} chars")
                
                val lambdaResponse = json.decodeFromString<LambdaResponse>(responseText)
                Logger.i("BedrockClient", "✅ Completion length: ${lambdaResponse.completion.length} chars")
                Logger.i("BedrockClient", "✅ Tokens - Input: ${lambdaResponse.usage.inputTokens}, Output: ${lambdaResponse.usage.outputTokens}")
                
                Result.success(BedrockResponse(
                    completion = lambdaResponse.completion,
                    stopReason = lambdaResponse.stopReason,
                    usage = BedrockUsage(
                        inputTokens = lambdaResponse.usage.inputTokens,
                        outputTokens = lambdaResponse.usage.outputTokens
                    )
                ))
            } else {
                val errorText = response.bodyAsText()
                Logger.e("BedrockClient", "❌ API error - Status: ${response.status}, Body: $errorText")
                Result.failure(Exception("Lambda API error: ${response.status} - $errorText"))
            }
        }
    }

    /**
     * Invoke Bedrock Agent through Lambda proxy (with server-side return-control handling)
     */
    suspend fun invokeAgent(
        message: String,
        sessionId: String,
        agentId: String,
        agentAliasId: String,
        cap: String = "onboarding",
        sessionContext: String? = null,
        returnControlInvocationResults: List<ReturnControlResult>? = null
    ): Result<BedrockResponse> {
        return withRetry {
            Logger.i("BedrockClient", "🔵 ========== AGENT API CALL START ==========")
            Logger.i("BedrockClient", "🔵 Endpoint: $apiEndpoint")
            Logger.i("BedrockClient", "🔵 AgentId: $agentId")
            Logger.i("BedrockClient", "🔵 AgentAliasId: $agentAliasId")
            Logger.i("BedrockClient", "🔵 SessionId: $sessionId")
            Logger.i("BedrockClient", "🔵 Cap: $cap")
            Logger.i("BedrockClient", "🔵 Message length: ${message.length} chars")

            val authToken = getAuthToken()
            Logger.i("BedrockClient", "🔑 Auth token retrieved: ${if (authToken == null) "NULL" else if (authToken.isEmpty()) "EMPTY" else "Present (${authToken.length} chars)"}")
            
            if (authToken == null) {
                Logger.e("BedrockClient", "❌ No authentication token available")
                return@withRetry Result.failure(Exception("No authentication token available"))
            }
            
            // For onboarding, empty token is allowed (Lambda will handle anonymous requests)
            if (authToken.isEmpty() && cap == "onboarding") {
                Logger.i("BedrockClient", "⚠️ Onboarding request without auth token (allowed)")
            }

            val requestBody = LambdaRequest(
                mode = "agent",
                message = message,
                sessionId = sessionId,
                agentId = agentId,
                agentAliasId = agentAliasId,
                cap = cap,
                sessionContext = sessionContext,
                returnControlInvocationResults = returnControlInvocationResults
            )

            Logger.i("BedrockClient", "📤 Serializing request body...")
            val requestBodyJson = json.encodeToString(LambdaRequest.serializer(), requestBody)
            Logger.i("BedrockClient", "📤 Request body size: ${requestBodyJson.length} chars")
            Logger.i("BedrockClient", "📤 Making HTTP POST request...")

            try {
                val response = httpClient.post(apiEndpoint) {
                    headers {
                        append(HttpHeaders.ContentType, "application/json")
                        // Always send Authorization header (even if empty for onboarding)
                        append(HttpHeaders.Authorization, "Bearer $authToken")
                        if (authToken.isNotEmpty()) {
                            append("X-Amigo-Auth", "Bearer $authToken")
                        }
                        Logger.i("BedrockClient", "📤 Headers set: Content-Type, Authorization")
                    }
                    setBody(requestBodyJson)
                }

                Logger.i("BedrockClient", "📥 Response received - Status: ${response.status.value} ${response.status.description}")
                Logger.i("BedrockClient", "📥 Response headers: ${response.headers.entries().joinToString { "${it.key}: ${it.value}" }}")

                if (response.status.isSuccess()) {
                    val responseText = response.bodyAsText()
                    Logger.i("BedrockClient", "📥 Response body size: ${responseText.length} chars")
                    Logger.i("BedrockClient", "📥 FULL RESPONSE: $responseText")
                    
                    Logger.i("BedrockClient", "📥 Parsing response JSON...")
                    val lambdaResponse = json.decodeFromString<LambdaResponse>(responseText)
                    Logger.i("BedrockClient", "✅ Response parsed successfully")
                    Logger.i("BedrockClient", "✅ Completion length: ${lambdaResponse.completion.length} chars")
                    Logger.i("BedrockClient", "✅ Completion text: '${lambdaResponse.completion}'")
                    Logger.i("BedrockClient", "✅ Stop reason: ${lambdaResponse.stopReason}")
                    Logger.i("BedrockClient", "✅ Tokens - Input: ${lambdaResponse.usage.inputTokens}, Output: ${lambdaResponse.usage.outputTokens}")
                    
                    // Log if we received invocations
                    if (!lambdaResponse.invocations.isNullOrEmpty()) {
                        Logger.i("BedrockClient", "📋 Received ${lambdaResponse.invocations.size} function invocations")
                        lambdaResponse.invocations.forEach { inv ->
                            Logger.i("BedrockClient", "  - ${inv.actionGroup}.${inv.functionName}(${inv.params.keys.joinToString()})")
                            Logger.i("BedrockClient", "    Params: ${inv.params}")
                        }
                    }
                    
                    Logger.i("BedrockClient", "🔵 ========== AGENT API CALL SUCCESS ==========")
                    Result.success(
                        BedrockResponse(
                            completion = lambdaResponse.completion,
                            stopReason = lambdaResponse.stopReason,
                            usage = BedrockUsage(
                                inputTokens = lambdaResponse.usage.inputTokens,
                                outputTokens = lambdaResponse.usage.outputTokens
                            ),
                            invocations = lambdaResponse.invocations,
                            invocationId = lambdaResponse.invocationId
                        )
                    )
                } else {
                    val errorText = response.bodyAsText()
                    Logger.e("BedrockClient", "❌ Agent API error - Status: ${response.status.value} ${response.status.description}")
                    Logger.e("BedrockClient", "❌ Error body: $errorText")
                    Logger.e("BedrockClient", "🔵 ========== AGENT API CALL FAILED ==========")
                    Result.failure(Exception("Lambda Agent API error: ${response.status} - $errorText"))
                }
            } catch (e: Exception) {
                Logger.e("BedrockClient", "❌ Exception during HTTP request: ${e.message}")
                Logger.e("BedrockClient", "❌ Exception type: ${e::class.simpleName}")
                Logger.e("BedrockClient", "❌ Stack trace: ${e.stackTraceToString()}")
                Logger.e("BedrockClient", "🔵 ========== AGENT API CALL EXCEPTION ==========")
                throw e
            }
        }
    }
    
    /**
     * Analyze an image with Claude via Lambda proxy
     * Note: Image analysis requires sending base64 image in prompt
     */
    suspend fun analyzeImage(
        imageData: ByteArray,
        prompt: String,
        modelId: String = "anthropic.claude-3-5-sonnet-20241022-v2:0",
        maxTokens: Int = 2048
    ): Result<BedrockResponse> {
        // TODO: Lambda function needs to be updated to support image content
        // For now, return an error
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
        // TODO: Implement streaming support in Lambda
        // For now, fall back to regular invoke
        return invokeModel(modelId, prompt, maxTokens, temperature, systemPrompt)
    }
    
    /**
     * Retry logic with exponential backoff
     */
    private suspend fun <T> withRetry(block: suspend () -> Result<T>): Result<T> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                Logger.i("BedrockClient", "🔄 Attempt ${attempt + 1}/$maxRetries")
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
            
            if (attempt < maxRetries - 1) {
                val delayMs = (2.0.pow(attempt) * 1000).toLong()
                Logger.i("BedrockClient", "⏳ Waiting ${delayMs}ms before retry...")
                delay(delayMs)
            }
        }
        
        Logger.e("BedrockClient", "❌ All $maxRetries attempts failed. Last error: ${lastException?.message}")
        return Result.failure(lastException ?: Exception("Unknown error"))
    }
    
    fun close() {
        httpClient.close()
    }
}

// Request/Response models for Lambda API
@Serializable
private data class LambdaRequest(
    val mode: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val agentId: String? = null,
    val agentAliasId: String? = null,
    val cap: String? = null,
    val sessionContext: String? = null,
    val returnControlInvocationResults: List<ReturnControlResult>? = null,
    val prompt: String = "",
    val modelId: String = "",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    val systemPrompt: String? = null
)

@Serializable
private data class LambdaResponse(
    val completion: String,
    val stopReason: String,
    val usage: LambdaUsage,
    val userId: String,
    val timestamp: String,
    val invocations: List<FunctionInvocation>? = null,
    val invocationId: String? = null
)

@Serializable
private data class LambdaUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

// Public response model
@Serializable
data class BedrockResponse(
    val completion: String,
    val stopReason: String,
    val usage: BedrockUsage? = null,
    val invocations: List<FunctionInvocation>? = null,
    val invocationId: String? = null
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



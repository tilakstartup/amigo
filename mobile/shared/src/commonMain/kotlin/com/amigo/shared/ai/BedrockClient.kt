package com.amigo.shared.ai

import com.amigo.shared.utils.Logger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
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
    val prompt: String,
    val modelId: String,
    val maxTokens: Int,
    val temperature: Double,
    val systemPrompt: String? = null
)

@Serializable
private data class LambdaResponse(
    val completion: String,
    val stopReason: String,
    val usage: LambdaUsage,
    val userId: String,
    val timestamp: String
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
    val usage: BedrockUsage? = null
)

@Serializable
data class BedrockUsage(
    val inputTokens: Int,
    val outputTokens: Int
)



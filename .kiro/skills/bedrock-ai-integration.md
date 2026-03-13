# AWS Bedrock AI Integration

Expert guidance for integrating AWS Bedrock (Claude) in the Amigo project.

## Architecture

```
Mobile App → BedrockClient (KMP) → Lambda Proxy → AWS Bedrock → Claude
                                         ↓
                                   Supabase Auth
```

## Components

### 1. BedrockClient (Shared Kotlin)
Location: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/BedrockClient.kt`

Responsibilities:
- HTTP client for Lambda proxy API
- Authentication token injection
- Retry logic with exponential backoff
- Request/response serialization

```kotlin
val client = BedrockClient(
    apiEndpoint = "https://your-lambda-url.amazonaws.com/bedrock",
    getAuthToken = { sessionManager.getAccessToken() }
)

// Direct model invocation
val response = client.invokeModel(
    modelId = "anthropic.claude-3-haiku-20240307-v1:0",
    prompt = "Hello, Claude!",
    systemPrompt = "You are a health coach"
)

// Agent invocation (with action groups)
val agentResponse = client.invokeAgent(
    message = "What's my daily calorie goal?",
    sessionId = sessionId,
    agentId = "AGENT_ID",
    agentAliasId = "ALIAS_ID",
    cap = "profile"
)
```

### 2. Lambda Proxy (Python)
Location: `infrastructure/lib/stacks/bedrock-proxy/lambda/index.py`

Responsibilities:
- Validate Supabase JWT tokens
- Call Bedrock API with AWS credentials
- Handle agent return-control flow
- Rate limiting and cost control

### 3. Bedrock Agent Stack (CDK)
Location: `infrastructure/lib/stacks/bedrock-agent/stack.ts`

Responsibilities:
- Define agent configuration
- Register action groups with OpenAPI schemas
- Set up IAM roles and permissions

## Action Groups

Action groups allow Claude to call functions in your app. Each action group has:
1. OpenAPI schema (JSON) in `infrastructure/lib/stacks/bedrock-agent/action-groups/schemas/`
2. Kotlin implementation in `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/actions/`

### Creating a New Action Group

1. Define OpenAPI schema:
```json
{
  "openapi": "3.0.0",
  "info": {
    "title": "My Action Group",
    "version": "1.0.0"
  },
  "paths": {
    "/myFunction": {
      "post": {
        "summary": "Does something useful",
        "parameters": [
          {
            "name": "param1",
            "in": "query",
            "required": true,
            "schema": { "type": "string" }
          }
        ]
      }
    }
  }
}
```

2. Implement in Kotlin:
```kotlin
object MyActionGroup : ActionGroup {
    override val name = "my-action-group"
    override val description = "Does useful things"
    
    override suspend fun executeFunction(
        functionName: String,
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        return when (functionName) {
            "myFunction" -> handleMyFunction(params, context)
            else -> Result.failure(IllegalArgumentException("Unknown function"))
        }
    }
    
    private suspend fun handleMyFunction(
        params: Map<String, String>,
        context: ActionContext
    ): Result<JsonObject> {
        val param1 = params["param1"] ?: return Result.failure(...)
        // Implementation
        return Result.success(buildJsonObject {
            put("result", "success")
        })
    }
}
```

3. Register in ActionGroupRegistry:
```kotlin
init {
    registerActionGroup(MyActionGroup)
}
```

4. Update CDK stack to include new schema

## Session Configurations

Session configs define conversation flows with specific system prompts and capabilities.

Location: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/sessions/`

```kotlin
object MySessionConfig : SessionConfig {
    override val name = "my-session"
    override val systemPrompt = """
        You are a helpful assistant that...
    """.trimIndent()
    
    override val capabilities = listOf("data-operations", "my-action-group")
    override val maxTokens = 2048
    override val temperature = 0.7
}
```

## Conversation Engines

Conversation engines manage multi-turn conversations with state management.

Pattern:
```kotlin
class MyConversationEngine(
    private val bedrockClient: BedrockClient,
    private val sessionConfig: SessionConfig
) {
    private val conversationHistory = mutableListOf<Message>()
    
    suspend fun sendMessage(message: String): Result<String> {
        conversationHistory.add(Message.User(message))
        
        val response = bedrockClient.invokeAgent(
            message = message,
            sessionId = sessionId,
            agentId = config.agentId,
            agentAliasId = config.agentAliasId
        )
        
        response.onSuccess { 
            conversationHistory.add(Message.Assistant(it.completion))
        }
        
        return response.map { it.completion }
    }
}
```

## Agent Instructions

The agent's base instructions are in `infrastructure/lib/stacks/bedrock-agent/instruction.md`

Key sections:
- Identity and role
- Capabilities and limitations
- Action group usage guidelines
- Response formatting rules

## Best Practices

1. **Authentication**: Always pass Supabase JWT token for authenticated requests
2. **Error Handling**: Use Result<T> pattern, handle network failures gracefully
3. **Retry Logic**: BedrockClient has built-in exponential backoff
4. **Logging**: Use Logger.i/w/e for debugging (see BedrockClient for examples)
5. **Token Limits**: Monitor usage.inputTokens and usage.outputTokens
6. **Cost Control**: Set appropriate maxTokens limits
7. **Session Management**: Use consistent sessionId for conversation continuity
8. **Action Group Security**: Validate user permissions in action group implementations

## Testing

```kotlin
// Mock BedrockClient for testing
class MockBedrockClient : BedrockClient(
    apiEndpoint = "mock",
    getAuthToken = { "mock-token" }
) {
    override suspend fun invokeAgent(...) = Result.success(
        BedrockResponse(completion = "Mock response", ...)
    )
}
```

## Common Issues

- **401 Unauthorized**: Check Supabase token is valid and not expired
- **429 Rate Limited**: Implement backoff or reduce request frequency
- **Empty Response**: Check Lambda logs, verify agent configuration
- **Action Group Not Found**: Ensure action group is registered in ActionGroupRegistry
- **Return Control Loop**: Lambda handles this server-side, but verify function results are properly formatted

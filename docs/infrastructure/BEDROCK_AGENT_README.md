# Amigo Bedrock Agent Setup

Complete AWS Bedrock Agent setup with Action Groups, Knowledge Base, and all tools for Amigo AI health coaching.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Bedrock Agent                          │
│                        (Amigo)                              │
│                                                             │
│  Foundation Model: Claude 3 Sonnet                          │
│  Instruction: AI Health Coach personality                   │
└──────────────────┬──────────────────────────────────────────┘
                   │
        ┌──────────┼──────────┬──────────┬──────────┐
        │          │          │          │          │
   ┌────▼───┐ ┌───▼────┐ ┌───▼────┐ ┌──▼─────┐ ┌──▼──────┐
   │ Health │ │  Data  │ │  Data  │ │ USDA   │ │ Pattern │
   │  Calc  │ │ Query  │ │  Mut   │ │ Search │ │ Analysis│
   └────────┘ └────────┘ └────────┘ └────────┘ └─────────┘
   Lambda     Lambda     Lambda     Lambda     Lambda
```

## Features

### Action Groups (Tools)

1. **Health Calculations**
   - `calculate_bmr`: Basal Metabolic Rate (Mifflin-St Jeor)
   - `calculate_tdee`: Total Daily Energy Expenditure
   - `validate_goal`: Check if goal is realistic and healthy
   - `calculate_macros`: Protein/carbs/fat targets

2. **Data Queries**
   - `get_user_profile`: Fetch user data from Supabase
   - `get_recent_meals`: Last N days of meal logs
   - `get_patterns`: Behavioral patterns
   - `get_goal_progress`: Current goal metrics

3. **Data Mutations**
   - `save_goal`: Persist new health goal
   - `update_preferences`: Update user preferences
   - `save_conversation`: Store AI interaction

4. **USDA Search**
   - `search_foods`: Query USDA FoodData Central
   - `get_food_details`: Get nutrition info

5. **Pattern Analysis**
   - `analyze_eating_patterns`: Meal timing, frequencies
   - `analyze_fasting_patterns`: Success rates, protocols
   - `generate_insights`: AI-powered insights

### Knowledge Base

- S3-backed document store
- Nutrition guides
- Health best practices
- Evidence-based recommendations

## Deployment

### Prerequisites

- AWS CLI configured
- AWS account with Bedrock access
- Supabase project with credentials

### Step 1: Deploy Infrastructure

```bash
# Set environment variables
export ENVIRONMENT=dev
export SUPABASE_URL=https://your-project.supabase.co
export SUPABASE_ANON_KEY=your-anon-key

# Deploy CloudFormation stack
aws cloudformation deploy \
  --template-file infrastructure/bedrock-agent.yaml \
  --stack-name amigo-bedrock-agent-${ENVIRONMENT} \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    Environment=${ENVIRONMENT} \
    SupabaseUrl=${SUPABASE_URL} \
    SupabaseAnonKey=${SUPABASE_ANON_KEY}
```

### Step 2: Create Action Groups

The agent needs action groups linked to Lambda functions. Register each:

```bash
# Get Agent ID from stack outputs
AGENT_ID=$(aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-agent-${ENVIRONMENT} \
  --query 'Stacks[0].Outputs[?OutputKey==`AgentId`].OutputValue' \
  --output text)

# Create Health Calculations action group
aws bedrock-agent create-agent-action-group \
  --agent-id ${AGENT_ID} \
  --agent-version DRAFT \
  --action-group-name health-calculations \
  --action-group-executor lambda=$(aws lambda get-function \
    --function-name amigo-health-calculations-${ENVIRONMENT} \
    --query 'Configuration.FunctionArn' --output text) \
  --api-schema file://infrastructure/action-groups/health-calculations-schema.json

# Repeat for other action groups...
```

### Step 3: Upload Knowledge Base Documents

```bash
# Get S3 bucket name
KB_BUCKET=$(aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-agent-${ENVIRONMENT} \
  --query 'Stacks[0].Outputs[?OutputKey==`KnowledgeBaseBucket`].OutputValue' \
  --output text)

# Upload documents
aws s3 sync ./knowledge-base/ s3://${KB_BUCKET}/
```

### Step 4: Prepare and Alias Agent

```bash
# Prepare agent (compiles everything)
aws bedrock-agent prepare-agent \
  --agent-id ${AGENT_ID}

# Create alias for stable endpoint
aws bedrock-agent create-agent-alias \
  --agent-id ${AGENT_ID} \
  --agent-alias-name prod \
  --description "Production alias for Amigo agent"
```

## Testing

### Test via AWS CLI

```bash
# Start session
SESSION_ID=$(aws bedrock-agent invoke-agent \
  --agent-id ${AGENT_ID} \
  --agent-alias-id $(aws bedrock-agent list-agent-aliases \
    --agent-id ${AGENT_ID} \
    --query 'agentAliasHistories[0].agentAliasId' --output text) \
  --session-id $(uuidgen) \
  --input-text "Hi Amigo, I want to set a weight loss goal" \
  --query 'completion' --output text)
```

### Test Action Groups Directly

```bash
# Test health calculation
aws lambda invoke \
  --function-name amigo-health-calculations-${ENVIRONMENT} \
  --payload '{
    "actionGroup": "health-calculations",
    "apiPath": "/calculate-bmr",
    "httpMethod": "POST",
    "parameters": [
      {"name": "weight_kg", "value": "75"},
      {"name": "height_cm", "value": "175"},
      {"name": "age", "value": "30"},
      {"name": "gender", "value": "male"}
    ]
  }' \
  response.json

cat response.json
```

## Mobile App Integration

### Kotlin Integration

```kotlin
// BedrockAgentClient.kt
import aws.sdk.kotlin.services.bedrockagentruntime.*
import aws.sdk.kotlin.services.bedrockagentruntime.model.*

class BedrockAgentClient(
    private val agentId: String,
    private val agentAliasId: String,
    private val region: String = "us-east-1"
) {
    private val client = BedrockAgentRuntimeClient {
        region = this@BedrockAgentClient.region
    }
    
    suspend fun sendMessage(
        message: String,
        sessionId: String
    ): AmigoResponse {
        val request = InvokeAgentRequest {
            this.agentId = this@BedrockAgentClient.agentId
            this.agentAliasId = this@BedrockAgentClient.agentAliasId
            this.sessionId = sessionId
            inputText = message
        }
        
        val response = client.invokeAgent(request)
        
        // Stream the response
        val fullResponse = StringBuilder()
        response.completion?.collect { event ->
            when (event) {
                is ResponseStream.Chunk -> {
                    fullResponse.append(event.value.bytes.decodeToString())
                }
            }
        }
        
        return parseAmigoResponse(fullResponse.toString())
    }
    
    private fun parseAmigoResponse(text: String): AmigoResponse {
        // Parse agent response
        return AmigoResponse(
            message = text,
            // ... parse other fields
        )
    }
}

// Usage
val agent = BedrockAgentClient(
    agentId = "YOUR_AGENT_ID",
    agentAliasId = "YOUR_ALIAS_ID"
)

val response = agent.sendMessage(
    message = "I want to lose weight",
    sessionId = UUID.randomUUID().toString()
)
```

### Swift Integration

```swift
// BedrockAgentClient.swift
import AWSBedrockAgentRuntime

class BedrockAgentClient {
    let agentId: String
    let agentAliasId: String
    let client: BedrockAgentRuntimeClient
    
    init(agentId: String, agentAliasId: String) {
        self.agentId = agentId
        self.agentAliasId = agentAliasId
        self.client = try! BedrockAgentRuntimeClient(region: "us-east-1")
    }
    
    func sendMessage(message: String, sessionId: String) async throws -> AmigoResponse {
        let request = InvokeAgentInput(
            agentId: agentId,
            agentAliasId: agentAliasId,
            inputText: message,
            sessionId: sessionId
        )
        
        let response = try await client.invokeAgent(input: request)
        
        // Process streaming response
        var fullResponse = ""
        for try await event in response.completion! {
            switch event {
            case .chunk(let chunk):
                if let text = String(data: chunk.bytes!, encoding: .utf8) {
                    fullResponse += text
                }
            default:
                break
            }
        }
        
        return parseAmigoResponse(fullResponse)
    }
}
```

## Cost Estimation

### Bedrock Agent
- Claude 3 Sonnet: ~$0.003 per 1K input tokens, ~$0.015 per 1K output tokens
- Average conversation: ~5-10K tokens = $0.10-0.20 per conversation

### Lambda
- Free tier: 1M requests/month, 400K GB-seconds/month
- After free tier: $0.20 per 1M requests + $0.0000166667 per GB-second

### S3 (Knowledge Base)
- Storage: $0.023 per GB/month
- Requests: Minimal (retrieval only)

### Estimated Monthly Cost (1000 active users)
- Bedrock Agent: ~$100-200 (based on conversation volume)
- Lambda: ~$10-20
- S3: ~$1
- **Total: ~$111-221/month**

## Monitoring

### CloudWatch Logs

All Lambda functions log to CloudWatch:

```bash
# View health calculations logs
aws logs tail /aws/lambda/amigo-health-calculations-${ENVIRONMENT} --follow

# View agent invocations
aws logs tail /aws/bedrock/agent/${AGENT_ID} --follow
```

### Metrics

Track key metrics:
- Agent invocation count
- Average conversation length
- Tool usage frequency
- Error rates
- Response latency

## Troubleshooting

### Agent not responding

1. Check agent status:
```bash
aws bedrock-agent get-agent --agent-id ${AGENT_ID}
```

2. Verify agent is prepared:
```bash
aws bedrock-agent list-agent-versions --agent-id ${AGENT_ID}
```

3. Check Lambda permissions:
```bash
aws lambda get-policy --function-name amigo-health-calculations-${ENVIRONMENT}
```

### Tool execution failing

1. Test Lambda directly (shown above)
2. Check CloudWatch logs for Lambda errors
3. Verify IAM permissions for Lambda execution role

### Knowledge base not working

1. Verify S3 bucket has documents
2. Check agent has S3 read permissions
3. Ensure documents are in supported formats (txt, md, pdf)

## Next Steps

1. **Enhance Lambda Functions**: Connect to Supabase for real data
2. **Add More Tools**: Meal logging, fasting tracking
3. **Expand Knowledge Base**: Add nutrition guidelines, recipes
4. **Implement Caching**: Reduce redundant API calls
5. **Add Analytics**: Track conversation quality, user satisfaction
6. **Multi-language Support**: Add translation capabilities

## Security

- All credentials stored in Lambda environment variables
- IAM roles follow least-privilege principle
- S3 bucket has encryption at rest
- No sensitive data in CloudWatch logs
- API calls use signed AWS requests

## Support

For issues or questions:
- Check CloudWatch logs first
- Review AWS Bedrock Agent documentation
- Test tools individually before debugging agent

## License

Proprietary - Amigo Health App

# Infrastructure Architecture (AWS CDK)

## Overview

The infrastructure is defined as code using AWS CDK with TypeScript. It deploys two main stacks: Bedrock Proxy (Lambda + API Gateway) and Bedrock Agent (Agent + Action Groups).

## Architecture Diagram

```
Mobile Apps (iOS/Android)
    ↓ HTTPS
API Gateway (/invoke)
    ↓
Lambda (Bedrock Proxy)
    ↓ Bedrock Runtime API
AWS Bedrock Agent
    ↓ RETURN_CONTROL
Lambda (returns function invocations)
    ↓
Mobile Apps (execute functions)
    ↓ returnControlInvocationResults
Lambda → Bedrock Agent
    ↓
Agent continues conversation
```

## Stack Structure

```
infrastructure/
├── bin/
│   └── app.ts                   # CDK app entry point
│
├── lib/stacks/
│   ├── bedrock-proxy/           # Lambda proxy stack
│   │   ├── lambda/
│   │   │   ├── index.py         # Lambda handler
│   │   │   └── requirements.txt
│   │   └── stack.ts             # CDK stack definition
│   │
│   └── bedrock-agent/           # Bedrock agent stack
│       ├── action-groups/
│       │   └── schemas/
│       │       ├── data-operations-schema.json
│       │       ├── health-calculations-schema.json
│       │       └── goal-management-schema.json
│       ├── instruction.md       # Agent system prompt
│       └── stack.ts             # CDK stack definition
│
├── cdk.out/                     # Synthesized CloudFormation
├── .env                         # Environment variables
├── package.json
└── tsconfig.json
```

## Stack 1: Bedrock Proxy Stack

### Purpose
Provides authenticated API endpoint for mobile apps to invoke Bedrock models and agents.

### Components

#### Lambda Function
- **Runtime**: Python 3.11
- **Handler**: `index.lambda_handler`
- **Timeout**: 30 seconds
- **Memory**: 512 MB
- **Environment Variables**:
  - `SUPABASE_URL`: Supabase project URL
  - `SUPABASE_ANON_KEY`: Supabase anonymous key
  - `SUPABASE_EDGE_BASE`: Edge functions base URL
  - `ENVIRONMENT`: Deployment environment (dev/staging/prod)

#### IAM Permissions
- `bedrock:InvokeModel` - Direct model invocation
- `bedrock:InvokeModelWithResponseStream` - Streaming responses
- `bedrock:Converse` - Converse API
- `bedrock:ConverseStream` - Streaming converse
- `bedrock-agent-runtime:InvokeAgent` - Agent invocation
- `bedrock:GetInferenceProfile` - Inference profile access

#### API Gateway
- **Type**: REST API
- **Stage**: Environment-specific (dev/staging/prod)
- **CORS**: Enabled for all origins
- **Endpoint**: `/invoke` (POST)
- **Authentication**: Custom via `Authorization` header

### Authentication Flow
1. Mobile app sends request with `Authorization: Bearer <supabase_jwt>`
2. Lambda extracts JWT from header
3. Lambda verifies JWT with Supabase `/auth/v1/user` endpoint
4. If valid, extracts user ID from JWT claims
5. Proceeds with Bedrock invocation
6. Returns response to mobile app

### Special Case: Onboarding
- Allows unauthenticated requests when `mode=agent` and `cap=onboarding`
- Generates anonymous user ID: `onboarding-anon-{timestamp}`
- Enables pre-signup AI conversations

## Stack 2: Bedrock Agent Stack

### Purpose
Deploys and configures Bedrock Agent with action groups for health coaching.

### Components

#### Bedrock Agent
- **Foundation Model**: Claude 3 Haiku (`anthropic.claude-3-haiku-20240307-v1:0`)
- **Instruction**: Loaded from `instruction.md`
- **Idle Session TTL**: 600 seconds (10 minutes)
- **Action Groups**: 3 (Data Operations, Health Calculations, Goal Management)

#### Action Groups (RETURN_CONTROL)
All action groups use `RETURN_CONTROL` execution mode:
- Agent identifies function to call
- Returns control to client with function details
- Client executes function locally
- Client returns results to agent
- Agent continues conversation

##### 1. Data Operations
- **Schema**: OpenAPI 3.0 (API schema)
- **Functions**:
  - `get-profile` - Fetch user profile
  - `save-onboarding-data` - Save onboarding profile
  - `get-onboarding-status` - Check onboarding completion

##### 2. Health Calculations
- **Schema**: OpenAPI 3.0 (API schema)
- **Functions**:
  - `calculate-bmr` - Basal Metabolic Rate
  - `calculate-tdee` - Total Daily Energy Expenditure
  - `calculate-daily-calories` - Goal-based calorie target
  - `validate-goal` - Goal feasibility check

##### 3. Goal Management
- **Schema**: OpenAPI 3.0 (API schema)
- **Functions**:
  - `save-goal` - Persist health goal to database

#### Custom Resources
CDK uses Lambda-backed custom resources for:
1. **Action Group Manager**: Creates/updates/deletes action groups
2. **Prepare Agent**: Calls `PrepareAgent` API after action group changes

#### S3 Bucket (Knowledge Base)
- **Purpose**: Store documents for RAG (future)
- **Encryption**: S3-managed
- **Versioning**: Enabled
- **Public Access**: Blocked

## Deployment

### Environment Configuration
```bash
# .env file
SUPABASE_URL=https://xxx.supabase.co
SUPABASE_ANON_KEY=eyJxxx...
AWS_REGION=us-east-1
ENVIRONMENT=dev
```

### Deployment Commands
```bash
cd infrastructure

# Install dependencies
npm install

# Build TypeScript
npm run build

# Synthesize CloudFormation
npm run synth

# View changes
npm run diff

# Deploy to dev
npm run deploy:dev

# Deploy to staging
npm run deploy:staging

# Deploy to prod
npm run deploy:prod
```

### Stack Outputs
- **BedrockApiEndpoint**: API Gateway URL for mobile apps
- **AgentId**: Bedrock Agent ID
- **AgentArn**: Bedrock Agent ARN
- **KnowledgeBaseBucket**: S3 bucket name

## Data Flow: RETURN_CONTROL

### 1. Initial Request
```
Mobile → Lambda: {
  "mode": "agent",
  "agentId": "xxx",
  "sessionId": "mobile-user123-1234567890",
  "message": "I want to set a weight loss goal"
}
```

### 2. Agent Response with RETURN_CONTROL
```
Lambda → Mobile: {
  "completion": "",
  "invocations": [{
    "action_group": "data_operations",
    "function_name": "get_profile",
    "params": {"user_id": "user123"}
  }],
  "invocationId": "inv-abc123"
}
```

### 3. Client Executes Function
```kotlin
// Mobile app executes locally
val profile = profileManager.getProfile(userId)
```

### 4. Client Returns Results
```
Mobile → Lambda: {
  "mode": "agent",
  "agentId": "xxx",
  "sessionId": "mobile-user123-1234567890",
  "returnControlInvocationResults": [{
    "invocation_id": "inv-abc123",
    "function_results": [{
      "action_group": "data_operations",
      "function_name": "get_profile",
      "success": true,
      "result": "{\"id\":\"user123\",\"weight_kg\":70,...}"
    }]
  }]
}
```

### 5. Agent Continues
```
Lambda → Mobile: {
  "completion": "I see you currently weigh 70kg. What's your target weight?",
  "stopReason": "end_turn"
}
```

## Security

### IAM Roles
- **Lambda Execution Role**: Minimal permissions (Bedrock + CloudWatch Logs)
- **Agent Role**: Bedrock model invocation + S3 read (knowledge base)
- **Custom Resource Roles**: Bedrock Agent management APIs

### API Security
- CORS enabled but authentication required
- JWT verification with Supabase
- No API keys exposed to clients
- Rate limiting via API Gateway (future)

### Secrets Management
- Supabase credentials in environment variables
- No secrets in code or CloudFormation outputs
- Use AWS Secrets Manager for production (future)

## Cost Optimization

### Lambda
- **Pricing**: Pay per request + compute time
- **Optimization**: 512 MB memory, 30s timeout
- **Estimated**: ~$0.20 per 1M requests

### Bedrock
- **Pricing**: Pay per token (input + output)
- **Model**: Claude 3 Haiku (cheapest Claude model)
- **Estimated**: ~$0.25 per 1M input tokens, ~$1.25 per 1M output tokens

### API Gateway
- **Pricing**: Pay per request
- **Estimated**: ~$3.50 per 1M requests

### S3
- **Pricing**: Pay per GB stored + requests
- **Estimated**: Minimal (knowledge base documents)

## Monitoring & Logging

### CloudWatch Logs
- Lambda execution logs
- API Gateway access logs
- Custom resource logs

### Metrics
- Lambda invocations, duration, errors
- API Gateway requests, latency, 4xx/5xx
- Bedrock token usage (via CloudWatch)

### Alarms (Future)
- Lambda error rate > 5%
- API Gateway 5xx rate > 1%
- Lambda duration > 25s (approaching timeout)

## Known Issues & TODOs

1. **Streaming Support**: Lambda doesn't support streaming responses yet
2. **Rate Limiting**: No rate limiting on API Gateway
3. **Secrets Management**: Environment variables instead of Secrets Manager
4. **Knowledge Base**: S3 bucket created but not integrated with agent
5. **Multi-Region**: Single region deployment only

## Future Enhancements

1. **Streaming**: Implement WebSocket API for streaming responses
2. **Rate Limiting**: Add API Gateway usage plans and API keys
3. **Secrets Manager**: Move credentials to AWS Secrets Manager
4. **Knowledge Base**: Integrate S3 documents with Bedrock Knowledge Base
5. **Multi-Region**: Deploy to multiple regions for lower latency
6. **CDN**: Add CloudFront for API caching
7. **WAF**: Add AWS WAF for DDoS protection
8. **Monitoring**: Add X-Ray tracing and custom metrics
9. **CI/CD**: Automate deployment with GitHub Actions
10. **Testing**: Add integration tests for Lambda functions

## Troubleshooting

### Lambda Timeout
- Increase timeout in `stack.ts`
- Optimize Bedrock model selection (use faster models)
- Add caching for repeated requests

### Agent Not Responding
- Check agent is prepared: `aws bedrock-agent get-agent --agent-id <id>`
- Verify action groups are enabled
- Check CloudWatch logs for errors

### Authentication Failures
- Verify Supabase URL and anon key
- Check JWT is valid and not expired
- Ensure user exists in Supabase

### Action Group Errors
- Verify schema is valid OpenAPI 3.0
- Check function names match schema
- Ensure RETURN_CONTROL is configured correctly

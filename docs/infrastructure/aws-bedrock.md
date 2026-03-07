# AWS Bedrock Setup with Lambda Proxy

## Architecture Overview

The Amigo app uses a secure Lambda proxy architecture to access AWS Bedrock:

```
Mobile App → API Gateway → Lambda Function → AWS Bedrock (Claude AI)
     ↓
Supabase JWT Token (for authentication)
```

This architecture ensures:
- No AWS credentials stored in mobile app
- Supabase JWT token authentication
- Rate limiting per user
- Centralized cost tracking
- Secure access control

## Prerequisites

- AWS Account with Bedrock access
- AWS CLI installed and configured
- Bedrock model access enabled (Claude models)
- Supabase project with JWT secret

## Enable Bedrock Model Access

1. Go to AWS Console → Amazon Bedrock
2. Navigate to "Model access" in the left sidebar
3. Click "Manage model access"
4. Enable access to:
   - Anthropic Claude 3.5 Sonnet (recommended)
   - Anthropic Claude 3 Haiku (optional, for faster responses)
5. Submit request and wait for approval (usually instant)

## Deploy CloudFormation Stack

### Option 1: AWS Console

1. Go to AWS CloudFormation console
2. Click "Create stack" → "With new resources"
3. Upload `infrastructure/bedrock.yaml`
4. Stack name: `amigo-bedrock-dev`
5. Parameters:
   - Environment: `dev`
   - SupabaseUrl: Your Supabase project URL (e.g., `https://xxx.supabase.co`)
   - SupabaseJwtSecret: Your Supabase JWT secret (from Supabase dashboard → Settings → API → JWT Secret)
6. Check "I acknowledge that AWS CloudFormation might create IAM resources"
7. Click "Create stack"

### Option 2: AWS CLI

```bash
aws cloudformation create-stack \
  --stack-name amigo-bedrock-dev \
  --template-body file://infrastructure/bedrock.yaml \
  --parameters \
    ParameterKey=Environment,ParameterValue=dev \
    ParameterKey=SupabaseUrl,ParameterValue=https://YOUR_PROJECT.supabase.co \
    ParameterKey=SupabaseJwtSecret,ParameterValue=YOUR_JWT_SECRET \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

## Retrieve API Endpoint

After stack creation completes (5-10 minutes):

```bash
# Get Lambda API endpoint
aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`BedrockApiEndpoint`].OutputValue' \
  --output text
```

Example output:
```
https://abc123xyz.execute-api.us-east-1.amazonaws.com/dev/invoke
```

## Configure Environment Variables

### Backend (.env file)

Add to `.env` file (DO NOT COMMIT):

```env
# AWS Configuration
AWS_REGION=us-east-1

# AWS Bedrock Lambda API (from CloudFormation output)
BEDROCK_API_ENDPOINT=https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke
```

Note: You no longer need AWS access keys in the mobile app!

### iOS App (Info.plist)

Update `mobile/ios/Amigo/Info.plist` with the API endpoint:

```xml
<key>BEDROCK_API_ENDPOINT</key>
<string>https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke</string>
```

Replace `YOUR_API_ID` with the actual API Gateway ID from CloudFormation output.

### Android App

The Android app reads from the `.env` file automatically through the build configuration.

## Test Lambda API

### Using curl

```bash
# Get a Supabase auth token first (from your app or Supabase dashboard)
TOKEN="your_supabase_jwt_token"

curl -X POST https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "prompt": "Hello, how are you?",
    "modelId": "anthropic.claude-3-5-sonnet-20241022-v2:0",
    "maxTokens": 100,
    "temperature": 0.7
  }'
```

Expected response:
```json
{
  "completion": "Hello! I'm doing well, thank you for asking...",
  "stopReason": "end_turn",
  "usage": {
    "inputTokens": 12,
    "outputTokens": 45
  },
  "userId": "user-uuid-from-token",
  "timestamp": "2024-03-07T12:34:56.789Z"
}
```

### Testing in iOS App

1. Open `mobile/ios/Amigo.xcodeproj` in Xcode
2. Update `Info.plist` with your API endpoint (see Configure Environment Variables section)
3. Build and run the app (Cmd+R)
4. Sign in with your test account
5. Navigate to "Test Bedrock Integration" from the main screen
6. Enter a prompt and tap "Send to Bedrock"
7. Verify you receive a response from Claude

The test screen (`BedrockTestView.swift`) shows:
- Prompt input field
- Send button with loading state
- Response display
- Error messages (if any)
- Token usage statistics

### Testing in Android App

1. Open `mobile` folder in Android Studio
2. Ensure `.env` file has the correct `BEDROCK_API_ENDPOINT`
3. Build and run the app
4. Sign in with your test account
5. Navigate to "Test Bedrock Integration" from the main screen
6. Enter a prompt and tap "Send to Bedrock"
7. Verify you receive a response from Claude

The test screen (`BedrockTestScreen.kt`) provides the same functionality as iOS.

## Lambda Function Details

The Lambda function (`infrastructure/lambda/bedrock-proxy/lambda_function.py`):

1. Verifies Supabase JWT token
2. Extracts user ID from token
3. Calls AWS Bedrock with IAM role (no hardcoded credentials)
4. Returns Claude AI response
5. Tracks token usage for cost monitoring

### Request Format

```json
{
  "prompt": "Your prompt text",
  "modelId": "anthropic.claude-3-5-sonnet-20241022-v2:0",
  "maxTokens": 2048,
  "temperature": 0.7,
  "systemPrompt": "Optional system prompt"
}
```

### Response Format

```json
{
  "completion": "AI response text",
  "stopReason": "end_turn",
  "usage": {
    "inputTokens": 123,
    "outputTokens": 456
  },
  "userId": "user-uuid",
  "timestamp": "2024-03-07T12:34:56.789Z"
}
```

## Cost Considerations

Claude 3.5 Sonnet Pricing (as of 2024):
- **Input**: $3 per 1M tokens
- **Output**: $15 per 1M tokens

Estimated costs for Amigo:
- Onboarding conversation: ~2000 tokens = $0.03
- Meal log coaching: ~500 tokens = $0.008
- Daily insight: ~1000 tokens = $0.015
- Pro tier chat: ~2000 tokens/message = $0.03

Monthly estimates:
- Free tier (onboarding only): ~$0.03/user
- Pro tier (active usage): ~$5-10/user

Additional AWS costs:
- Lambda: $0.20 per 1M requests + $0.0000166667 per GB-second
- API Gateway: $3.50 per 1M requests
- Total infrastructure: ~$0.01-0.05 per user/month

## Security Best Practices

1. **Never commit credentials** to git
2. **Rotate Supabase JWT secret** if compromised
3. **Monitor Lambda logs** in CloudWatch
4. **Set billing alerts** in AWS
5. **Implement rate limiting** per user (TODO in Lambda)
6. **Use HTTPS only** (enforced by API Gateway)

## Monitoring

### CloudWatch Logs

View Lambda logs:
```bash
aws logs tail /aws/lambda/amigo-bedrock-proxy-dev --follow
```

### Cost Tracking

View Bedrock usage:
```bash
aws ce get-cost-and-usage \
  --time-period Start=2024-03-01,End=2024-03-31 \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --filter file://bedrock-filter.json
```

## Cleanup

To delete the stack:

```bash
aws cloudformation delete-stack --stack-name amigo-bedrock-dev
```

## Troubleshooting

### 401 Unauthorized Error

- Verify Supabase JWT token is valid
- Check SupabaseJwtSecret parameter in CloudFormation
- Ensure token has `authenticated` audience

### 500 Internal Server Error

- Check Lambda logs in CloudWatch
- Verify Bedrock model access is enabled
- Check IAM role permissions

### Rate Limiting

- Implement exponential backoff in BedrockClient
- Add user-level rate limiting in Lambda (TODO)
- Consider caching responses

### CORS Errors

- API Gateway has CORS enabled for all origins
- Check browser console for specific error
- Verify Authorization header is included

## Next Steps

1. Deploy CloudFormation stack
2. Update `.env` with API endpoint
3. Test Lambda API with curl
4. Implement BedrockClient in mobile app
5. Add rate limiting to Lambda function
6. Set up CloudWatch alarms for costs
7. Implement request caching

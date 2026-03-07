# AWS Bedrock Lambda Proxy - Deployment Checklist

This checklist guides you through deploying the complete Bedrock integration from infrastructure to mobile apps.

## Prerequisites ✓

- [ ] AWS Account with Bedrock access
- [ ] AWS CLI installed and configured
- [ ] Supabase project URL and JWT secret
- [ ] Xcode installed (for iOS testing)
- [ ] Android Studio installed (for Android testing)

## Step 1: Enable Bedrock Model Access

- [ ] Go to AWS Console → Amazon Bedrock
- [ ] Navigate to "Model access"
- [ ] Enable "Anthropic Claude 3.5 Sonnet"
- [ ] Wait for approval (usually instant)

## Step 2: Get Supabase JWT Secret

- [ ] Go to Supabase Dashboard
- [ ] Navigate to Settings → API
- [ ] Copy "JWT Secret" (not the anon key!)
- [ ] Save it securely for CloudFormation deployment

## Step 3: Deploy CloudFormation Stack

### Option A: AWS Console

- [ ] Go to AWS CloudFormation console
- [ ] Click "Create stack" → "With new resources"
- [ ] Upload `infrastructure/bedrock.yaml`
- [ ] Stack name: `amigo-bedrock-dev`
- [ ] Enter parameters:
  - Environment: `dev`
  - SupabaseUrl: `https://YOUR_PROJECT.supabase.co`
  - SupabaseJwtSecret: (paste from Step 2)
- [ ] Check "I acknowledge that AWS CloudFormation might create IAM resources"
- [ ] Click "Create stack"
- [ ] Wait 5-10 minutes for completion

### Option B: AWS CLI

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

## Step 4: Get API Endpoint

- [ ] Wait for stack creation to complete
- [ ] Get the API endpoint:

```bash
aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`BedrockApiEndpoint`].OutputValue' \
  --output text
```

- [ ] Copy the endpoint URL (e.g., `https://abc123.execute-api.us-east-1.amazonaws.com/dev/invoke`)

## Step 5: Configure Environment Variables

### Backend (.env file)

- [ ] Open `.env` file in project root
- [ ] Update `BEDROCK_API_ENDPOINT` with the URL from Step 4
- [ ] Verify it looks like: `https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev/invoke`
- [ ] Save the file (DO NOT COMMIT)

### iOS (Info.plist)

- [ ] Open `mobile/ios/Amigo/Info.plist`
- [ ] Find the `BEDROCK_API_ENDPOINT` key
- [ ] Replace the placeholder with the URL from Step 4
- [ ] Save the file

### Android

- [ ] Android reads from `.env` automatically
- [ ] No additional configuration needed

## Step 6: Test Lambda API with curl

- [ ] Get a Supabase auth token:
  - Sign in to your app
  - Or use Supabase dashboard to generate a test token
- [ ] Run the test command:

```bash
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

- [ ] Verify you receive a JSON response with `completion` field
- [ ] Check for any errors in the response

## Step 7: Test iOS App

- [ ] Open `mobile/ios/Amigo.xcodeproj` in Xcode
- [ ] Select a simulator or device
- [ ] Build and run (Cmd+R)
- [ ] Sign in with a test account
- [ ] Navigate to "Test Bedrock Integration"
- [ ] Enter a prompt: "Hello, how are you?"
- [ ] Tap "Send to Bedrock"
- [ ] Verify you receive a response
- [ ] Check token usage is displayed

### Troubleshooting iOS

If you see errors:
- [ ] Check Xcode console for error messages
- [ ] Verify `Info.plist` has correct API endpoint
- [ ] Ensure you're signed in (check access token exists)
- [ ] Check Lambda logs in CloudWatch

## Step 8: Test Android App

- [ ] Open `mobile` folder in Android Studio
- [ ] Select a device or emulator
- [ ] Build and run
- [ ] Sign in with a test account
- [ ] Navigate to "Test Bedrock Integration"
- [ ] Enter a prompt: "Hello, how are you?"
- [ ] Tap "Send to Bedrock"
- [ ] Verify you receive a response
- [ ] Check token usage is displayed

### Troubleshooting Android

If you see errors:
- [ ] Check Logcat for error messages
- [ ] Verify `.env` has correct API endpoint
- [ ] Ensure you're signed in (check access token exists)
- [ ] Check Lambda logs in CloudWatch

## Step 9: Monitor and Verify

- [ ] Check CloudWatch Logs for Lambda invocations:

```bash
aws logs tail /aws/lambda/amigo-bedrock-proxy-dev --follow
```

- [ ] Verify successful requests in logs
- [ ] Check for any error messages
- [ ] Monitor token usage in responses

## Step 10: Set Up Cost Monitoring (Optional)

- [ ] Go to AWS Billing Dashboard
- [ ] Set up billing alerts for Bedrock usage
- [ ] Recommended thresholds:
  - Alert at $10/month
  - Alert at $50/month
  - Alert at $100/month

## Common Issues

### 401 Unauthorized

- **Cause**: Invalid or expired Supabase JWT token
- **Fix**: 
  - Verify SupabaseJwtSecret in CloudFormation matches Supabase dashboard
  - Ensure user is signed in
  - Check token expiration

### 500 Internal Server Error

- **Cause**: Lambda function error
- **Fix**:
  - Check CloudWatch logs
  - Verify Bedrock model access is enabled
  - Check IAM role permissions

### BEDROCK_API_ENDPOINT not configured

- **Cause**: Missing or incorrect configuration
- **Fix**:
  - iOS: Update `Info.plist` with correct endpoint
  - Android: Update `.env` file
  - Rebuild the app

### Cannot find 'BedrockTestView' in scope

- **Cause**: File not added to Xcode project
- **Fix**: Already fixed - BedrockTestView.swift is now in project.pbxproj

## Success Criteria ✓

- [ ] CloudFormation stack deployed successfully
- [ ] API endpoint retrieved and configured
- [ ] curl test returns valid response
- [ ] iOS app can send prompts and receive responses
- [ ] Android app can send prompts and receive responses
- [ ] Token usage is tracked and displayed
- [ ] No errors in CloudWatch logs
- [ ] Cost monitoring alerts configured

## Next Steps

After successful deployment:

1. Remove test screens from production builds
2. Integrate BedrockClient into actual app features
3. Implement rate limiting in Lambda
4. Add response caching for common queries
5. Set up CloudWatch alarms for errors
6. Monitor costs and optimize token usage

## Rollback

If you need to rollback:

```bash
# Delete CloudFormation stack
aws cloudformation delete-stack --stack-name amigo-bedrock-dev

# Revert code changes
git checkout main
```

## Support

For issues:
- Check CloudWatch logs first
- Review AWS Bedrock documentation
- Check Supabase JWT token validity
- Verify IAM permissions in CloudFormation template

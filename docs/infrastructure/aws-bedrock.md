# AWS Bedrock Setup

## Prerequisites

- AWS Account with Bedrock access
- AWS CLI installed and configured
- Bedrock model access enabled (Claude models)

## Enable Bedrock Model Access

1. Go to AWS Console → Amazon Bedrock
2. Navigate to "Model access" in the left sidebar
3. Click "Manage model access"
4. Enable access to:
   - Anthropic Claude 3 Sonnet
   - Anthropic Claude 3 Haiku
   - Anthropic Claude 3 Opus (optional, for advanced features)
5. Submit request and wait for approval (usually instant)

## Deploy CloudFormation Stack

### Option 1: AWS Console

1. Go to AWS CloudFormation console
2. Click "Create stack" → "With new resources"
3. Upload `infrastructure/bedrock.yaml`
4. Stack name: `amigo-bedrock-dev`
5. Parameters:
   - Environment: `dev`
6. Review and create

### Option 2: AWS CLI

```bash
aws cloudformation create-stack \
  --stack-name amigo-bedrock-dev \
  --template-body file://infrastructure/bedrock.yaml \
  --parameters ParameterKey=Environment,ParameterValue=dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

## Retrieve Credentials

After stack creation completes:

```bash
# Get Access Key ID
aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`BedrockMobileUserAccessKeyId`].OutputValue' \
  --output text

# Get Secret Access Key
aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`BedrockMobileUserSecretAccessKey`].OutputValue' \
  --output text
```

## Configure Environment Variables

Add to `.env` file (DO NOT COMMIT):

```env
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=<from CloudFormation output>
AWS_SECRET_ACCESS_KEY=<from CloudFormation output>
BEDROCK_MODEL_ID=anthropic.claude-3-sonnet-20240229-v1:0
```

## Test Bedrock Access

```bash
aws bedrock-runtime invoke-model \
  --model-id anthropic.claude-3-sonnet-20240229-v1:0 \
  --body '{"prompt":"Hello","max_tokens":100}' \
  --cli-binary-format raw-in-base64-out \
  output.json
```

## Cost Considerations

Claude 3 Pricing (as of 2024):
- **Haiku**: $0.25 per 1M input tokens, $1.25 per 1M output tokens
- **Sonnet**: $3 per 1M input tokens, $15 per 1M output tokens
- **Opus**: $15 per 1M input tokens, $75 per 1M output tokens

Estimated costs for Amigo:
- Average meal log: ~500 tokens = $0.0015 (Sonnet)
- 1000 meal logs/month: ~$1.50
- With coaching: ~$5-10/month per active user

## Security Best Practices

1. **Never commit credentials** to git
2. **Use IAM roles** in production (not access keys)
3. **Rotate keys** regularly
4. **Monitor usage** with CloudWatch
5. **Set billing alerts** in AWS

## Cleanup

To delete the stack:

```bash
aws cloudformation delete-stack --stack-name amigo-bedrock-dev
```

## Troubleshooting

### Access Denied Error

- Verify model access is enabled in Bedrock console
- Check IAM permissions
- Verify region supports Bedrock (us-east-1, us-west-2)

### Rate Limiting

- Implement exponential backoff
- Use request queuing
- Consider caching responses

## Next Steps

- Implement AWS Signature V4 signing in `BedrockClient.kt`
- Add retry logic with exponential backoff
- Implement request/response logging
- Add cost tracking

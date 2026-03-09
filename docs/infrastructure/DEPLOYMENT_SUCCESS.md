# Bedrock Agent Deployment - Complete ✅

## Deployment Summary

**Date**: March 8, 2026  
**Environment**: dev  
**Region**: us-east-1  
**Status**: Successfully Deployed

## Resources Created

### Bedrock Agent
- **Agent ID**: `4XLAIQ6BUY`
- **Agent ARN**: `arn:aws:bedrock:us-east-1:334856751928:agent/4XLAIQ6BUY`
- **Foundation Model**: Amazon Nova Micro (amazon.nova-micro-v1:0)
- **Status**: PREPARED

### Lambda Functions (5)
1. **Health Calculations**: `amigo-health-calculations-dev`
   - BMR, TDEE, goal validation, macro calculations
   
2. **Data Queries**: `amigo-data-queries-dev`
   - User profile, meals, patterns, goal progress
   
3. **Data Mutations**: `amigo-data-mutations-dev`
   - Save goals, preferences, meals, weight
   
4. **USDA Search**: `amigo-usda-search-dev`
   - Food search and nutrition lookup
   
5. **Pattern Analysis**: `amigo-pattern-analysis-dev`
   - Eating patterns, fasting analysis, insights

### S3 Bucket
- **Knowledge Base**: `amigo-knowledge-base-dev-334856751928`
- Encryption: AES256
- Versioning: Enabled

### IAM Roles
- **Agent Role**: `AmigoAgentRole-dev`
  - Bedrock access
  - Lambda invocation
  - S3 read access
  
- **Lambda Role**: `AmigoAgentLambdaRole-dev`
  - CloudWatch logs
  - Bedrock invoke model

## Next Steps

### 1. Register Action Groups (Required)
Currently the agent has NO action groups registered. You need to create them:

```bash
# Set variables
AGENT_ID=4XLAIQ6BUY
REGION=us-east-1
HEALTH_ARN="arn:aws:lambda:us-east-1:334856751928:function:amigo-health-calculations-dev"
DATA_Q_ARN="arn:aws:lambda:us-east-1:334856751928:function:amigo-data-queries-dev"
DATA_M_ARN="arn:aws:lambda:us-east-1:334856751928:function:amigo-data-mutations-dev"
USDA_ARN="arn:aws:lambda:us-east-1:334856751928:function:amigo-usda-search-dev"
PATTERN_ARN="arn:aws:lambda:us-east-1:334856751928:function:amigo-pattern-analysis-dev"

# Create Health Calculations action group
aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name health-calculations \
  --action-group-executor lambda=$HEALTH_ARN \
  --api-schema file://infrastructure/action-groups/health-calculations-schema.json

# Create Data Queries action group
aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name data-queries \
  --action-group-executor lambda=$DATA_Q_ARN \
  --api-schema file://infrastructure/action-groups/data-queries-schema.json

# Create Data Mutations action group
aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name data-mutations \
  --action-group-executor lambda=$DATA_M_ARN \
  --api-schema file://infrastructure/action-groups/data-mutations-schema.json

# Create USDA Search action group
aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name usda-search \
  --action-group-executor lambda=$USDA_ARN \
  --api-schema file://infrastructure/action-groups/usda-search-schema.json

# Create Pattern Analysis action group
aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name pattern-analysis \
  --action-group-executor lambda=$PATTERN_ARN \
  --api-schema file://infrastructure/action-groups/pattern-analysis-schema.json

# Prepare agent after adding action groups
aws bedrock-agent prepare-agent --agent-id $AGENT_ID --region $REGION
```

### 2. Upload Knowledge Base Documents
```bash
KB_BUCKET="amigo-knowledge-base-dev-334856751928"

# Create documents locally (example)
mkdir -p knowledge-base/nutrition
echo "# Macronutrients Guide..." > knowledge-base/nutrition/macros.md

# Upload to S3
aws s3 sync ./knowledge-base/ s3://${KB_BUCKET}/
```

### 3. Test the Agent
```bash
# Test Lambda directly
aws lambda invoke \
  --function-name amigo-health-calculations-dev \
  --region us-east-1 \
  --cli-binary-format raw-in-base64-out \
  --payload '{"actionGroup":"health-calculations","apiPath":"/calculate-bmr","parameters":[{"name":"weight_kg","value":"75"},{"name":"height_cm","value":"175"},{"name":"age","value":"30"},{"name":"gender","value":"male"}]}' \
  response.json

# Test agent conversation (after action groups registered)
aws bedrock-agent-runtime invoke-agent \
  --agent-id 4XLAIQ6BUY \
  --agent-alias-id TSTALIASID \
  --region us-east-1 \
  --session-id $(uuidgen) \
  --input-text "Hi Amigo, help me set a weight loss goal" \
  response.txt
```

### 4. Mobile App Integration
Update your mobile app to use the Bedrock Agent:

```kotlin
// In your Kotlin shared code
val bedrockClient = BedrockAgentRuntimeClient {
    region = "us-east-1"
}

val response = bedrockClient.invokeAgent {
    agentId = "4XLAIQ6BUY"
    agentAliasId = "your-alias-id"
    sessionId = UUID.randomUUID().toString()
    inputText = userMessage
}
```

## Configuration Files

- **Stack Config**: `/infrastructure/.bedrock-agent-config-dev.json`
- **CloudFormation Template**: `/infrastructure/bedrock-agent.yaml`
- **Action Group Schemas**: `/infrastructure/action-groups/*.json`
- **Deployment Script**: `/infrastructure/deploy-bedrock-agent.sh`
- **Test Script**: `/infrastructure/test-bedrock-agent.sh`

## Troubleshooting

### View Logs
```bash
# Lambda logs
aws logs tail /aws/lambda/amigo-health-calculations-dev --follow

# Agent logs (after invocation)
aws logs tail /aws/bedrock/agent/4XLAIQ6BUY --follow
```

### Check Agent Status
```bash
aws bedrock-agent get-agent --agent-id 4XLAIQ6BUY --region us-east-1
```

### Delete Stack (if needed)
```bash
aws cloudformation delete-stack --stack-name amigo-bedrock-agent-dev --region us-east-1
```

## Cost Estimate

- **Bedrock Agent (Nova Micro)**: ~$0.015-0.03 per conversation (75% cheaper than Claude)
- **Lambda**: Free tier covers most dev usage
- **S3**: ~$1/month for knowledge base
- **Total**: ~$5-15/month for development

## Notes

- Agent is in DRAFT mode - changes take effect immediately
- Lambda functions have inline code (good for prototyping)
- No action groups registered yet - agent won't have tools until you add them
- Knowledge base is empty - upload documents for RAG capabilities

## References

- [Bedrock Agent Console](https://console.aws.amazon.com/bedrock/agent/4XLAIQ6BUY)
- [CloudFormation Stack](https://console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks/stackinfo?stackId=amigo-bedrock-agent-dev)
- [Lambda Functions](https://console.aws.amazon.com/lambda/home?region=us-east-1#/functions)

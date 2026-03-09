# Amigo Bedrock Agent - Deployment Checklist

## 📋 Overview

This checklist guides you through deploying the complete Amigo Bedrock Agent infrastructure with all action groups, Lambda functions, and knowledge base.

## ✅ Pre-Deployment Checklist

### AWS Setup
- [ ] AWS CLI installed and configured (`aws --version`)
- [ ] AWS credentials configured (`aws sts get-caller-identity`)
- [ ] Bedrock service access enabled in your AWS account
- [ ] Claude 3 Sonnet model access requested (if not already available)
- [ ] IAM permissions for CloudFormation, Lambda, S3, Bedrock

### Project Setup
- [ ] Clone/pull latest code
- [ ] Review CloudFormation template: `infrastructure/bedrock-agent.yaml`
- [ ] Review action group schemas in `infrastructure/action-groups/`
- [ ] Prepare knowledge base documents (optional for initial deployment)

### Supabase Setup
- [ ] Supabase project URL available
- [ ] Supabase anon key available
- [ ] Database migrations run (tables created)
- [ ] Test Supabase connection

## 🚀 Deployment Steps

### Step 1: Deploy Infrastructure (10-15 minutes)

```bash
cd /Users/tilakputta/projects/apps/healthamigoai
./infrastructure/deploy-bedrock-agent.sh
```

**What this does:**
- Deploys CloudFormation stack
- Creates IAM roles
- Creates 5 Lambda functions
- Creates Bedrock Agent
- Creates S3 bucket for knowledge base
- Creates agent alias

**Verification:**
- [ ] CloudFormation stack shows `CREATE_COMPLETE`
- [ ] Agent ID and Alias ID saved to config file
- [ ] All Lambda functions created
- [ ] S3 bucket created

### Step 2: Register Action Groups (5 minutes per group)

The deployment script handles Health Calculations automatically. For the remaining 4 action groups:

#### Data Queries Action Group
```bash
# Get IDs from config
AGENT_ID=$(jq -r '.agentId' infrastructure/.bedrock-agent-config-dev.json)
REGION=$(jq -r '.region' infrastructure/.bedrock-agent-config-dev.json)

# Get Lambda ARN
DATA_QUERIES_ARN=$(aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-agent-dev \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`DataQueriesFunctionArn`].OutputValue' \
  --output text)

# Create action group
aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name data-queries \
  --action-group-executor lambda=$DATA_QUERIES_ARN \
  --api-schema file://infrastructure/action-groups/data-queries-schema.json
```

- [ ] Data Queries action group created

#### Data Mutations Action Group
```bash
DATA_MUTATIONS_ARN=$(aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-agent-dev \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`DataMutationsFunctionArn`].OutputValue' \
  --output text)

aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name data-mutations \
  --action-group-executor lambda=$DATA_MUTATIONS_ARN \
  --api-schema file://infrastructure/action-groups/data-mutations-schema.json
```

- [ ] Data Mutations action group created

#### USDA Search Action Group
```bash
USDA_SEARCH_ARN=$(aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-agent-dev \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`USDASearchFunctionArn`].OutputValue' \
  --output text)

aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name usda-search \
  --action-group-executor lambda=$USDA_SEARCH_ARN \
  --api-schema file://infrastructure/action-groups/usda-search-schema.json
```

- [ ] USDA Search action group created

#### Pattern Analysis Action Group
```bash
PATTERN_ANALYSIS_ARN=$(aws cloudformation describe-stacks \
  --stack-name amigo-bedrock-agent-dev \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`PatternAnalysisFunctionArn`].OutputValue' \
  --output text)

aws bedrock-agent create-agent-action-group \
  --agent-id $AGENT_ID \
  --region $REGION \
  --agent-version DRAFT \
  --action-group-name pattern-analysis \
  --action-group-executor lambda=$PATTERN_ANALYSIS_ARN \
  --api-schema file://infrastructure/action-groups/pattern-analysis-schema.json
```

- [ ] Pattern Analysis action group created

#### Prepare Agent After All Action Groups
```bash
aws bedrock-agent prepare-agent --agent-id $AGENT_ID --region $REGION
```

- [ ] Agent prepared with all action groups

### Step 3: Upload Knowledge Base Documents (Optional)

```bash
# Create knowledge-base directory if it doesn't exist
mkdir -p knowledge-base

# Add your documents (.txt, .md, .pdf)
# Example structure:
# knowledge-base/
#   nutrition/
#     macronutrients.md
#     micronutrients.md
#   fasting/
#     intermittent-fasting-guide.md
#   health/
#     weight-loss-basics.md

# Upload to S3
KB_BUCKET=$(jq -r '.knowledgeBaseBucket' infrastructure/.bedrock-agent-config-dev.json)
aws s3 sync ./knowledge-base/ s3://${KB_BUCKET}/
```

- [ ] Knowledge base documents created/collected
- [ ] Documents uploaded to S3

### Step 4: Test the Deployment

```bash
./infrastructure/test-bedrock-agent.sh dev
```

**This tests:**
- Agent status
- Action groups registered
- Lambda function execution (BMR calculation)
- Agent conversation
- Knowledge base bucket

- [ ] All tests passing

### Step 5: Test Agent Conversation

```bash
# Interactive test
aws bedrock-agent-runtime invoke-agent \
  --agent-id $AGENT_ID \
  --agent-alias-id $(jq -r '.agentAliasId' infrastructure/.bedrock-agent-config-dev.json) \
  --region $REGION \
  --session-id $(uuidgen) \
  --input-text "Hi Amigo, I want to set a weight loss goal. I'm 30 years old, 175cm tall, 75kg, and moderately active." \
  response.txt && cat response.txt
```

- [ ] Agent responds naturally
- [ ] Agent asks follow-up questions
- [ ] Agent can calculate BMR/TDEE

## 🔧 Post-Deployment Tasks

### Lambda Function Enhancement
Current Lambda functions have inline code with calculation logic but mock data access. To connect to Supabase:

1. **Create deployment packages with dependencies:**
```bash
cd infrastructure/lambda/data-queries
pip install -r requirements.txt -t .
zip -r function.zip .
aws lambda update-function-code \
  --function-name amigo-data-queries-dev \
  --zip-file fileb://function.zip
```

2. **Update Lambda code to use real Supabase client:**
```python
from supabase import create_client

supabase = create_client(
    os.environ['SUPABASE_URL'],
    os.environ['SUPABASE_ANON_KEY']
)

# Real query
response = supabase.table('user_profiles').select('*').eq('user_id', user_id).execute()
```

- [ ] Data Queries Lambda connected to Supabase
- [ ] Data Mutations Lambda connected to Supabase
- [ ] USDA Search Lambda connected to USDA API
- [ ] Pattern Analysis Lambda with real data analysis

### Knowledge Base Enhancement
- [ ] Create comprehensive nutrition documents
- [ ] Add fasting guides
- [ ] Add exercise recommendations
- [ ] Set up embeddings and vector search

### Mobile App Integration

Update the mobile app to use Bedrock Agent instead of direct Claude API:

**Kotlin (shared):**
```kotlin
// Add AWS SDK dependency to build.gradle.kts
implementation("aws.sdk.kotlin:bedrockagentruntime:1.0.0")

// Create BedrockAgentClient
class BedrockAgentClient(
    private val agentId: String,
    private val agentAliasId: String
) {
    suspend fun sendMessage(message: String, sessionId: String): Flow<String> {
        // Call Bedrock Agent Runtime API
    }
}
```

- [ ] AWS SDK integrated
- [ ] BedrockAgentClient implemented
- [ ] Session management updated
- [ ] UI updated for streaming responses

### Monitoring & Observability
- [ ] Set up CloudWatch dashboards
- [ ] Configure alarms for Lambda errors
- [ ] Set up log aggregation
- [ ] Track agent invocation metrics
- [ ] Monitor costs

## 📊 Validation & Testing

### Functional Tests
- [ ] Onboarding conversation flow
- [ ] Goal setting with calculation tools
- [ ] Meal logging
- [ ] Pattern analysis insights
- [ ] Multi-turn conversations with memory

### Performance Tests
- [ ] Response latency < 3 seconds
- [ ] Lambda cold start time acceptable
- [ ] Concurrent user handling

### Cost Tests
- [ ] Estimate cost per conversation
- [ ] Monitor daily Bedrock usage
- [ ] Set up billing alarms

## 🚨 Troubleshooting

### Agent Not Responding
1. Check agent status: `aws bedrock-agent get-agent --agent-id $AGENT_ID`
2. Verify agent is PREPARED
3. Check CloudWatch logs: `aws logs tail /aws/bedrock/agent/$AGENT_ID --follow`

### Lambda Errors
1. Test Lambda directly: `aws lambda invoke --function-name amigo-health-calculations-dev ...`
2. Check CloudWatch logs: `aws logs tail /aws/lambda/amigo-health-calculations-dev --follow`
3. Verify IAM permissions

### Action Group Not Working
1. List action groups: `aws bedrock-agent list-agent-action-groups --agent-id $AGENT_ID`
2. Validate OpenAPI schema syntax
3. Re-prepare agent after fixes

## 📝 Configuration Files Created

- `infrastructure/bedrock-agent.yaml` - CloudFormation template
- `infrastructure/action-groups/health-calculations-schema.json` - Health calculations API
- `infrastructure/action-groups/data-queries-schema.json` - Data queries API
- `infrastructure/action-groups/data-mutations-schema.json` - Data mutations API  
- `infrastructure/action-groups/usda-search-schema.json` - USDA search API
- `infrastructure/action-groups/pattern-analysis-schema.json` - Pattern analysis API
- `infrastructure/deploy-bedrock-agent.sh` - Deployment automation script
- `infrastructure/test-bedrock-agent.sh` - Testing script
- `infrastructure/.bedrock-agent-config-{env}.json` - Runtime configuration (auto-generated)

## 🎯 Success Criteria

Your deployment is complete when:
- [ ] All 5 Lambda functions deployed and working
- [ ] All 5 action groups registered with agent
- [ ] Agent status is PREPARED
- [ ] Test script passes all checks
- [ ] Agent responds to test conversation
- [ ] CloudWatch logs show no errors

## 📚 Next Steps

1. **Enhance Lambda Functions**: Connect to real data sources
2. **Build Knowledge Base**: Add comprehensive health documents
3. **Integrate with Mobile App**: Replace current conversation engine
4. **Add Monitoring**: Set up dashboards and alerts
5. **Test at Scale**: Load testing and optimization
6. **Deploy to Production**: Create prod environment

## 🔗 Resources

- [AWS Bedrock Agent Documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/agents.html)
- [Claude 3 Sonnet Model Card](https://docs.anthropic.com/claude/docs/models-overview)
- [OpenAPI 3.0 Specification](https://swagger.io/specification/)
- [Bedrock Agent Pricing](https://aws.amazon.com/bedrock/pricing/)

---

**Deployed**: {timestamp}  
**Environment**: dev  
**Agent ID**: {will be filled by deployment script}  
**Region**: us-east-1

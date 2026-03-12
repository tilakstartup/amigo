import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as bedrock from 'aws-cdk-lib/aws-bedrock';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as cr from 'aws-cdk-lib/custom-resources';
import { Construct } from 'constructs';
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';

export interface BedrockAgentStackProps extends cdk.StackProps {
  environment: string;
  supabaseUrl: string;
  supabaseAnonKey: string;
}

export class BedrockAgentStack extends cdk.Stack {
  public readonly agent: bedrock.CfnAgent;
  public readonly knowledgeBaseBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: BedrockAgentStackProps) {
    super(scope, id, props);

    const { environment, supabaseUrl } = props;
    const supabaseEdgeFunctionBase = `${supabaseUrl}/functions/v1`;

    // S3 Bucket for Knowledge Base
    this.knowledgeBaseBucket = new s3.Bucket(this, 'KnowledgeBaseBucket', {
      bucketName: `amigo-knowledge-base-${environment}-${this.account}`,
      versioned: true,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // IAM Role for Bedrock Agent
    const agentRole = new iam.Role(this, 'AgentRole', {
      roleName: `AmigoAgentRole-${environment}`,
      assumedBy: new iam.ServicePrincipal('bedrock.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonBedrockFullAccess'),
      ],
    });

    // Add S3 permissions for knowledge base
    agentRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['s3:GetObject', 's3:ListBucket'],
        resources: [
          this.knowledgeBaseBucket.bucketArn,
          `${this.knowledgeBaseBucket.bucketArn}/*`,
        ],
      })
    );

    // Add Bedrock permissions
    agentRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'bedrock:Retrieve',
          'bedrock:RetrieveAndGenerate',
          'bedrock:InvokeModel',
          'bedrock:InvokeModelWithResponseStream',
        ],
        resources: ['*'],
      })
    );

    // Read agent instruction from file
    const instruction = fs.readFileSync(path.join(__dirname, 'instruction.md'), 'utf-8');

    // Create Bedrock Agent
    this.agent = new bedrock.CfnAgent(this, 'Agent', {
      agentName: `amigo-${environment}`,
      description: 'Amigo AI Health Coach - Conversational agent for health tracking and coaching',
      agentResourceRoleArn: agentRole.roleArn,
      foundationModel: 'anthropic.claude-3-haiku-20240307-v1:0',
      instruction,
      idleSessionTtlInSeconds: 600,
    });

    // Create custom resource for action groups with RETURN_CONTROL
    const actionGroupManager = this.createActionGroupManager(environment);

    // Create Data Operations Action Group
    const dataOpsActionGroup = this.createActionGroup(
      'DataOperations',
      actionGroupManager,
      'data_operations',
      'Profile and onboarding data operations with RETURN_CONTROL',
      this.getDataOperationsSchema()
    );

    // Create Health Calculations Action Group
    const healthCalcActionGroup = this.createActionGroup(
      'HealthCalculations',
      actionGroupManager,
      'health_calculations',
      'BMR, TDEE, and goal validation calculations with RETURN_CONTROL',
      this.getHealthCalculationsSchema()
    );

    // Create Goal Management Action Group
    const goalMgmtActionGroup = this.createActionGroup(
      'GoalManagement',
      actionGroupManager,
      'goal_management',
      'Save and manage user health goals with RETURN_CONTROL',
      this.getGoalManagementSchema()
    );

    // Create custom resource to prepare agent
    const prepareAgent = this.createPrepareAgentResource(environment);
    prepareAgent.node.addDependency(dataOpsActionGroup);
    prepareAgent.node.addDependency(healthCalcActionGroup);
    prepareAgent.node.addDependency(goalMgmtActionGroup);

    // Outputs
    new cdk.CfnOutput(this, 'AgentIdOutput', {
      value: this.agent.attrAgentId,
      description: 'Bedrock Agent ID',
      exportName: `${id}-AgentId`,
    });

    new cdk.CfnOutput(this, 'AgentArnOutput', {
      value: this.agent.attrAgentArn,
      description: 'Bedrock Agent ARN',
      exportName: `${id}-AgentArn`,
    });

    new cdk.CfnOutput(this, 'KnowledgeBaseBucketOutput', {
      value: this.knowledgeBaseBucket.bucketName,
      description: 'S3 bucket for knowledge base documents',
      exportName: `${id}-KnowledgeBucket`,
    });

    new cdk.CfnOutput(this, 'SupabaseEdgeFunctionBaseOutput', {
      value: supabaseEdgeFunctionBase,
      description: 'Base URL for Supabase edge functions',
      exportName: `${id}-EdgeFunctionBase`,
    });
  }

  private createActionGroupManager(environment: string): lambda.Function {
    const role = new iam.Role(this, 'ActionGroupManagerRole', {
      roleName: `AmigoActionGroupManagerRole-${environment}`,
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaBasicExecutionRole'),
      ],
    });

    role.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'bedrock:CreateAgentActionGroup',
          'bedrock:UpdateAgentActionGroup',
          'bedrock:DeleteAgentActionGroup',
          'bedrock:GetAgentActionGroup',
          'bedrock:ListAgentActionGroups',
        ],
        resources: ['*'],
      })
    );

    return new lambda.Function(this, 'ActionGroupManagerFunction', {
      functionName: `amigo-action-group-manager-${environment}`,
      runtime: lambda.Runtime.PYTHON_3_11,
      handler: 'index.handler',
      role,
      timeout: cdk.Duration.seconds(60),
      code: lambda.Code.fromInline(this.getActionGroupManagerCode()),
    });
  }

  private createPrepareAgentResource(environment: string): cdk.CustomResource {
    const role = new iam.Role(this, 'PrepareAgentRole', {
      roleName: `AmigoPrepareAgentRole-${environment}`,
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaBasicExecutionRole'),
      ],
    });

    role.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['bedrock:PrepareAgent'],
        resources: ['*'],
      })
    );

    const prepareFunction = new lambda.Function(this, 'PrepareAgentFunction', {
      functionName: `amigo-prepare-agent-${environment}`,
      runtime: lambda.Runtime.PYTHON_3_11,
      handler: 'index.handler',
      role,
      timeout: cdk.Duration.seconds(60),
      code: lambda.Code.fromInline(this.getPrepareAgentCode()),
    });

    const provider = new cr.Provider(this, 'PrepareAgentProvider', {
      onEventHandler: prepareFunction,
    });

    return new cdk.CustomResource(this, 'PrepareAgent', {
      serviceToken: provider.serviceToken,
      properties: {
        AgentId: this.agent.attrAgentId,
      },
    });
  }

  private createActionGroup(
    id: string,
    managerFunction: lambda.Function,
    actionGroupName: string,
    description: string,
    apiSchema: string
  ): cdk.CustomResource {
    const provider = new cr.Provider(this, `${id}Provider`, {
      onEventHandler: managerFunction,
    });

    // Compute hash of schema to detect changes
    const schemaHash = crypto.createHash('sha256').update(apiSchema).digest('hex').substring(0, 8);

    const actionGroup = new cdk.CustomResource(this, `${id}ActionGroup`, {
      serviceToken: provider.serviceToken,
      properties: {
        AgentId: this.agent.attrAgentId,
        ActionGroupName: actionGroupName,
        Description: description,
        ApiSchema: apiSchema,
        SchemaHash: schemaHash, // This will change when schema changes
      },
    });

    actionGroup.node.addDependency(this.agent);
    return actionGroup;
  }

  private getDataOperationsSchema(): string {
    return fs.readFileSync(path.join(__dirname, 'action-groups/schemas/data-operations-schema.json'), 'utf-8');
  }

  private getHealthCalculationsSchema(): string {
    return fs.readFileSync(path.join(__dirname, 'action-groups/schemas/health-calculations-schema.json'), 'utf-8');
  }

  private getGoalManagementSchema(): string {
    return fs.readFileSync(path.join(__dirname, 'action-groups/schemas/goal-management-schema.json'), 'utf-8');
  }

  private getActionGroupManagerCode(): string {
    return `import boto3
import json
import urllib.request

bedrock = boto3.client('bedrock-agent')

def send(event, context, status, data, physical_id):
    body = {
        "Status": status,
        "Reason": f"See CloudWatch Log Stream: {context.log_stream_name}",
        "PhysicalResourceId": physical_id or context.log_stream_name,
        "StackId": event["StackId"],
        "RequestId": event["RequestId"],
        "LogicalResourceId": event["LogicalResourceId"],
        "Data": data,
    }
    req = urllib.request.Request(
        event["ResponseURL"],
        data=json.dumps(body).encode("utf-8"),
        method="PUT",
        headers={"Content-Type": ""},
    )
    urllib.request.urlopen(req)

def handler(event, context):
    print(json.dumps(event))
    props = event.get("ResourceProperties", {})
    agent_id = props["AgentId"]
    action_group_name = props["ActionGroupName"]
    description = props.get("Description", "")
    api_schema = props.get("ApiSchema", "")
    
    # Physical ID format: {agent_id}:{action_group_id}
    physical_id = event.get("PhysicalResourceId", f"{agent_id}:pending")

    try:
        req_type = event["RequestType"]
        
        if req_type == "Create":
            response = bedrock.create_agent_action_group(
                agentId=agent_id,
                agentVersion='DRAFT',
                actionGroupName=action_group_name,
                description=description,
                actionGroupExecutor={'customControl': 'RETURN_CONTROL'},
                apiSchema={'payload': api_schema},
                actionGroupState='ENABLED'
            )
            action_group_id = response['agentActionGroup']['actionGroupId']
            new_physical_id = f"{agent_id}:{action_group_id}"
            send(event, context, "SUCCESS", {"ActionGroupId": action_group_id}, new_physical_id)
            return
        
        if req_type == "Update":
            # Extract action group ID from physical ID
            if ':' in physical_id:
                _, action_group_id = physical_id.split(':', 1)
            else:
                # Fallback for old format - look up the action group by name
                print(f"WARNING: Old physical ID format detected: {physical_id}")
                print(f"Looking up action group by name: {action_group_name}")
                try:
                    list_response = bedrock.list_agent_action_groups(
                        agentId=agent_id,
                        agentVersion='DRAFT',
                        maxResults=100
                    )
                    action_groups = list_response.get('actionGroupSummaries', [])
                    matching_group = next((ag for ag in action_groups if ag['actionGroupName'] == action_group_name), None)
                    if matching_group:
                        action_group_id = matching_group['actionGroupId']
                        print(f"Found action group ID: {action_group_id}")
                    else:
                        raise Exception(f"Could not find action group with name: {action_group_name}")
                except Exception as lookup_error:
                    print(f"Failed to lookup action group: {lookup_error}")
                    raise
            
            response = bedrock.update_agent_action_group(
                agentId=agent_id,
                agentVersion='DRAFT',
                actionGroupId=action_group_id,
                actionGroupName=action_group_name,
                description=description,
                actionGroupExecutor={'customControl': 'RETURN_CONTROL'},
                apiSchema={'payload': api_schema},
                actionGroupState='ENABLED'
            )
            # Update to new physical ID format
            new_physical_id = f"{agent_id}:{action_group_id}"
            send(event, context, "SUCCESS", {"ActionGroupId": action_group_id}, new_physical_id)
            return
        
        if req_type == "Delete":
            try:
                # Extract action group ID from physical ID
                if ':' in physical_id:
                    _, action_group_id = physical_id.split(':', 1)
                else:
                    action_group_id = physical_id
                
                bedrock.delete_agent_action_group(
                    agentId=agent_id,
                    agentVersion='DRAFT',
                    actionGroupId=action_group_id
                )
            except Exception as e:
                print(f"Delete error (may be already deleted): {e}")
            send(event, context, "SUCCESS", {"Deleted": True}, physical_id)
            return

        send(event, context, "FAILED", {"Error": "Unsupported RequestType"}, physical_id)
    except Exception as e:
        print(f"Error: {e}")
        send(event, context, "FAILED", {"Error": str(e)}, physical_id)`;
  }

  private getPrepareAgentCode(): string {
    return `import boto3
import json
import urllib.request

bedrock = boto3.client('bedrock-agent')

def send(event, context, status, data, physical_id):
    body = {
        "Status": status,
        "Reason": f"See CloudWatch Log Stream: {context.log_stream_name}",
        "PhysicalResourceId": physical_id or context.log_stream_name,
        "StackId": event["StackId"],
        "RequestId": event["RequestId"],
        "LogicalResourceId": event["LogicalResourceId"],
        "Data": data,
    }
    req = urllib.request.Request(
        event["ResponseURL"],
        data=json.dumps(body).encode("utf-8"),
        method="PUT",
        headers={"Content-Type": ""},
    )
    urllib.request.urlopen(req)

def handler(event, context):
    print(json.dumps(event))
    props = event.get("ResourceProperties", {})
    agent_id = props["AgentId"]
    physical_id = event.get("PhysicalResourceId", "prepare-agent")

    try:
        req_type = event["RequestType"]
        
        if req_type in ["Create", "Update"]:
            bedrock.prepare_agent(agentId=agent_id)
            send(event, context, "SUCCESS", {"Prepared": True}, physical_id)
            return
        
        if req_type == "Delete":
            send(event, context, "SUCCESS", {"Deleted": True}, physical_id)
            return

        send(event, context, "FAILED", {"Error": "Unsupported RequestType"}, physical_id)
    except Exception as e:
        print(f"Error: {e}")
        send(event, context, "FAILED", {"Error": str(e)}, physical_id)`;
  }
}

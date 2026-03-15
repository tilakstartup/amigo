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
  proFoundationModel?: string;
  freeFoundationModel?: string;
}

export class BedrockAgentStack extends cdk.Stack {
  public readonly proAgent: bedrock.CfnAgent;
  public readonly freeAgent: bedrock.CfnAgent;
  public readonly knowledgeBaseBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: BedrockAgentStackProps) {
    super(scope, id, props);

    const {
      environment,
      supabaseUrl,
      proFoundationModel = 'amazon.nova-lite-v1:0',
      freeFoundationModel = 'amazon.nova-lite-v1:0',
    } = props;
    const supabaseEdgeFunctionBase = `${supabaseUrl}/functions/v1`;

    // S3 Bucket for Knowledge Base
    this.knowledgeBaseBucket = new s3.Bucket(this, 'KnowledgeBaseBucket', {
      bucketName: `amigo-knowledge-base-${environment}-${this.account}`,
      versioned: true,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // ─── Pro Agent ────────────────────────────────────────────────────────────

    const proAgentRole = new iam.Role(this, 'ProAgentRole', {
      roleName: `AmigoProAgentRole-${environment}`,
      assumedBy: new iam.ServicePrincipal('bedrock.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonBedrockFullAccess'),
      ],
    });

    proAgentRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['s3:GetObject', 's3:ListBucket'],
        resources: [
          this.knowledgeBaseBucket.bucketArn,
          `${this.knowledgeBaseBucket.bucketArn}/*`,
        ],
      })
    );

    proAgentRole.addToPolicy(
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

    const proInstruction = fs.readFileSync(path.join(__dirname, 'instruction-pro.md'), 'utf-8');

    this.proAgent = new bedrock.CfnAgent(this, 'ProAgent', {
      agentName: `amigo-pro-${environment}`,
      description: 'Amigo AI Health Coach — Pro tier (full action groups)',
      agentResourceRoleArn: proAgentRole.roleArn,
      foundationModel: proFoundationModel,
      instruction: proInstruction,
      idleSessionTtlInSeconds: 600,
    });

    // Action group manager (scoped to Pro Agent only)
    const actionGroupManager = this.createActionGroupManager(environment);

    const dataOpsActionGroup = this.createActionGroup(
      'DataOperations',
      actionGroupManager,
      this.proAgent,
      'data_operations',
      'Profile and onboarding data operations with RETURN_CONTROL',
      this.getDataOperationsSchema()
    );

    const healthCalcActionGroup = this.createActionGroup(
      'HealthCalculations',
      actionGroupManager,
      this.proAgent,
      'health_calculations',
      'BMR, TDEE, and goal validation calculations with RETURN_CONTROL',
      this.getHealthCalculationsSchema()
    );

    const goalMgmtActionGroup = this.createActionGroup(
      'GoalManagement',
      actionGroupManager,
      this.proAgent,
      'goal_management',
      'Save and manage user health goals with RETURN_CONTROL',
      this.getGoalManagementSchema()
    );

    const prepareProAgent = this.createPrepareAgentResource('Pro', environment, this.proAgent);
    prepareProAgent.node.addDependency(dataOpsActionGroup);
    prepareProAgent.node.addDependency(healthCalcActionGroup);
    prepareProAgent.node.addDependency(goalMgmtActionGroup);

    // ─── Free Agent ───────────────────────────────────────────────────────────

    const freeAgentRole = new iam.Role(this, 'FreeAgentRole', {
      roleName: `AmigoFreeAgentRole-${environment}`,
      assumedBy: new iam.ServicePrincipal('bedrock.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonBedrockFullAccess'),
      ],
    });

    freeAgentRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'bedrock:InvokeModel',
          'bedrock:InvokeModelWithResponseStream',
        ],
        resources: ['*'],
      })
    );

    const freeInstruction = fs.readFileSync(path.join(__dirname, 'instruction-free.md'), 'utf-8');

    this.freeAgent = new bedrock.CfnAgent(this, 'FreeAgent', {
      agentName: `amigo-free-${environment}`,
      description: 'Amigo AI Health Coach — Free tier (no action groups)',
      agentResourceRoleArn: freeAgentRole.roleArn,
      foundationModel: freeFoundationModel,
      instruction: freeInstruction,
      idleSessionTtlInSeconds: 600,
    });

    const prepareFreAgent = this.createPrepareAgentResource('Free', environment, this.freeAgent);

    // ─── Outputs ──────────────────────────────────────────────────────────────

    new cdk.CfnOutput(this, 'ProAgentId', {
      value: this.proAgent.attrAgentId,
      description: 'Pro Bedrock Agent ID',
      exportName: `${id}-ProAgentId`,
    });

    new cdk.CfnOutput(this, 'FreeAgentId', {
      value: this.freeAgent.attrAgentId,
      description: 'Free Bedrock Agent ID',
      exportName: `${id}-FreeAgentId`,
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

  private createPrepareAgentResource(
    agentLabel: string,
    environment: string,
    agent: bedrock.CfnAgent
  ): cdk.CustomResource {
    const role = new iam.Role(this, `Prepare${agentLabel}AgentRole`, {
      roleName: `AmigoPrepare${agentLabel}AgentRole-${environment}`,
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

    const prepareFunction = new lambda.Function(this, `Prepare${agentLabel}AgentFunction`, {
      functionName: `amigo-prepare-${agentLabel.toLowerCase()}-agent-${environment}`,
      runtime: lambda.Runtime.PYTHON_3_11,
      handler: 'index.handler',
      role,
      timeout: cdk.Duration.seconds(60),
      code: lambda.Code.fromInline(this.getPrepareAgentCode()),
    });

    const provider = new cr.Provider(this, `Prepare${agentLabel}AgentProvider`, {
      onEventHandler: prepareFunction,
    });

    return new cdk.CustomResource(this, `Prepare${agentLabel}Agent`, {
      serviceToken: provider.serviceToken,
      properties: {
        AgentId: agent.attrAgentId,
      },
    });
  }

  private createActionGroup(
    id: string,
    managerFunction: lambda.Function,
    agent: bedrock.CfnAgent,
    actionGroupName: string,
    description: string,
    apiSchema: string
  ): cdk.CustomResource {
    const provider = new cr.Provider(this, `${id}Provider`, {
      onEventHandler: managerFunction,
    });

    const schemaHash = crypto.createHash('sha256').update(apiSchema).digest('hex').substring(0, 8);

    const actionGroup = new cdk.CustomResource(this, `${id}ActionGroup`, {
      serviceToken: provider.serviceToken,
      properties: {
        AgentId: agent.attrAgentId,
        ActionGroupName: actionGroupName,
        Description: description,
        ApiSchema: apiSchema,
        SchemaHash: schemaHash,
      },
    });

    actionGroup.node.addDependency(agent);
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
            if ':' in physical_id:
                _, action_group_id = physical_id.split(':', 1)
            else:
                print(f"WARNING: Old physical ID format detected: {physical_id}")
                list_response = bedrock.list_agent_action_groups(
                    agentId=agent_id,
                    agentVersion='DRAFT',
                    maxResults=100
                )
                action_groups = list_response.get('actionGroupSummaries', [])
                matching_group = next((ag for ag in action_groups if ag['actionGroupName'] == action_group_name), None)
                if matching_group:
                    action_group_id = matching_group['actionGroupId']
                else:
                    raise Exception(f"Could not find action group with name: {action_group_name}")
            
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
            new_physical_id = f"{agent_id}:{action_group_id}"
            send(event, context, "SUCCESS", {"ActionGroupId": action_group_id}, new_physical_id)
            return
        
        if req_type == "Delete":
            try:
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

import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import * as path from 'path';

export interface BedrockProxyStackProps extends cdk.StackProps {
  environment: string;
  supabaseUrl: string;
  supabaseAnonKey: string;
  supabaseJwtPublicKey: string;
  proAgentId: string;
  proAgentAliasId: string;
  freeAgentId: string;
  freeAgentAliasId: string;
}

export class BedrockProxyStack extends cdk.Stack {
  public readonly lambdaFunction: lambda.Function;
  public readonly api: apigateway.RestApi;
  public readonly apiEndpoint: string;

  constructor(scope: Construct, id: string, props: BedrockProxyStackProps) {
    super(scope, id, props);

    const {
      environment,
      supabaseUrl,
      supabaseAnonKey,
      supabaseJwtPublicKey,
      proAgentId,
      proAgentAliasId,
      freeAgentId,
      freeAgentAliasId,
    } = props;

    // Lambda Execution Role
    const lambdaRole = new iam.Role(this, 'BedrockLambdaRole', {
      roleName: `amigo-bedrock-lambda-${environment}`,
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaBasicExecutionRole'),
      ],
    });

    // Add Bedrock permissions
    lambdaRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'bedrock:InvokeModel',
          'bedrock:InvokeModelWithResponseStream',
          'bedrock:Converse',
          'bedrock:ConverseStream',
        ],
        resources: [
          `arn:aws:bedrock:${this.region}::foundation-model/anthropic.claude-*`,
          `arn:aws:bedrock:${this.region}::foundation-model/amazon.nova-*`,
          `arn:aws:bedrock:${this.region}:${this.account}:inference-profile/*`,
        ],
      })
    );

    // Add Bedrock Agent Runtime permissions
    lambdaRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'bedrock-agent-runtime:InvokeAgent',
          'bedrock:InvokeAgent',
        ],
        resources: ['*'],
      })
    );

    // Add permission to use inference profiles
    lambdaRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'bedrock:GetInferenceProfile',
          'bedrock:ListInferenceProfiles',
        ],
        resources: ['*'],
      })
    );

    // Lambda Layer: PyJWT + cryptography (Linux x86_64 wheels, pre-built for python3.11)
    const pyJwtLayer = new lambda.LayerVersion(this, 'PyJwtLayer', {
      layerVersionName: `amigo-pyjwt-${environment}`,
      code: lambda.Code.fromAsset(path.join(__dirname, 'layer')),
      compatibleRuntimes: [lambda.Runtime.PYTHON_3_11],
      description: 'PyJWT and cryptography for ES256 JWT verification',
    });

    // Lambda Function
    this.lambdaFunction = new lambda.Function(this, 'BedrockProxyFunction', {
      functionName: `amigo-bedrock-proxy-${environment}`,
      runtime: lambda.Runtime.PYTHON_3_11,
      handler: 'index.lambda_handler',
      code: lambda.Code.fromAsset(path.join(__dirname, 'lambda')),
      layers: [pyJwtLayer],
      role: lambdaRole,
      timeout: cdk.Duration.seconds(30),
      memorySize: 512,
      environment: {
        SUPABASE_URL: supabaseUrl,
        SUPABASE_ANON_KEY: supabaseAnonKey,
        SUPABASE_EDGE_BASE: `${supabaseUrl}/functions/v1`,
        ENVIRONMENT: environment,
        SUPABASE_JWT_PUBLIC_KEY: supabaseJwtPublicKey,
        BEDROCK_PRO_AGENT_ID: proAgentId,
        BEDROCK_PRO_AGENT_ALIAS_ID: proAgentAliasId,
        BEDROCK_FREE_AGENT_ID: freeAgentId,
        BEDROCK_FREE_AGENT_ALIAS_ID: freeAgentAliasId,
      },
    });

    // API Gateway
    this.api = new apigateway.RestApi(this, 'BedrockProxyApi', {
      restApiName: `amigo-bedrock-api-${environment}`,
      description: 'API Gateway for Bedrock Lambda proxy',
      deployOptions: {
        stageName: environment,
      },
      defaultCorsPreflightOptions: {
        allowOrigins: apigateway.Cors.ALL_ORIGINS,
        allowMethods: apigateway.Cors.ALL_METHODS,
        allowHeaders: ['Content-Type', 'Authorization', 'X-Amigo-Auth'],
      },
    });

    // Add /invoke resource
    const invokeResource = this.api.root.addResource('invoke');
    const lambdaIntegration = new apigateway.LambdaIntegration(this.lambdaFunction);
    invokeResource.addMethod('POST', lambdaIntegration);

    // Store API endpoint
    this.apiEndpoint = this.api.urlForPath('/invoke');

    // Outputs
    new cdk.CfnOutput(this, 'BedrockProxyFunctionArn', {
      value: this.lambdaFunction.functionArn,
      description: 'ARN of the Bedrock proxy Lambda function',
      exportName: `${id}-FunctionArn`,
    });

    new cdk.CfnOutput(this, 'BedrockApiEndpoint', {
      value: this.apiEndpoint,
      description: 'API Gateway endpoint URL for Bedrock proxy',
      exportName: `${id}-ApiEndpoint`,
    });

    new cdk.CfnOutput(this, 'BedrockApiId', {
      value: this.api.restApiId,
      description: 'API Gateway ID',
      exportName: `${id}-ApiId`,
    });
  }
}

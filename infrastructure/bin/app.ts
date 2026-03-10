#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { BedrockProxyStack } from '../lib/stacks/bedrock-proxy/stack';
import { BedrockAgentStack } from '../lib/stacks/bedrock-agent/stack';
import * as dotenv from 'dotenv';
import * as path from 'path';

// Load environment variables from .env file
dotenv.config({ path: path.join(__dirname, '../.env') });

const app = new cdk.App();

// Get environment from context or environment variable
const environment = app.node.tryGetContext('environment') || process.env.ENVIRONMENT || 'dev';

// Get Supabase configuration from environment variables
const supabaseUrl = process.env.SUPABASE_URL;
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY;

if (!supabaseUrl) {
  throw new Error('SUPABASE_URL must be set in .env file');
}

if (!supabaseAnonKey) {
  throw new Error('SUPABASE_ANON_KEY must be set in .env file');
}

// Deploy Bedrock Proxy Stack (Lambda + API Gateway)
new BedrockProxyStack(app, `AmigoBedrockProxyStack-${environment}`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.AWS_REGION || process.env.CDK_DEFAULT_REGION || 'us-east-1',
  },
  environment,
  supabaseUrl,
  supabaseAnonKey,
  description: `Amigo Bedrock Proxy Stack - ${environment}`,
});

// Deploy Bedrock Agent Stack (Agent + Action Groups)
new BedrockAgentStack(app, `AmigoBedrockAgentStack-${environment}`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.AWS_REGION || process.env.CDK_DEFAULT_REGION || 'us-east-1',
  },
  environment,
  supabaseUrl,
  supabaseAnonKey,
  description: `Amigo Bedrock Agent Stack - ${environment}`,
});

// Add tags to all resources
cdk.Tags.of(app).add('Project', 'Amigo');
cdk.Tags.of(app).add('Environment', environment);
cdk.Tags.of(app).add('ManagedBy', 'CDK');

app.synth();

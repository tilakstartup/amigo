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

// Supabase configuration
const supabaseUrl = process.env.SUPABASE_URL;
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY;
const supabaseJwtPublicKey = process.env.SUPABASE_JWT_PUBLIC_KEY;

if (!supabaseUrl) {
  throw new Error('SUPABASE_URL must be set in .env file');
}
if (!supabaseAnonKey) {
  throw new Error('SUPABASE_ANON_KEY must be set in .env file');
}
if (!supabaseJwtPublicKey) {
  throw new Error('SUPABASE_JWT_PUBLIC_KEY must be set in .env file');
}

// Bedrock agent IDs (populated after first deploy of BedrockAgentStack)
// Alias IDs are optional — defaults to TSTALIASID (test alias → DRAFT) when not set
const proAgentId = process.env.BEDROCK_PRO_AGENT_ID;
const proAgentAliasId = process.env.BEDROCK_PRO_AGENT_ALIAS_ID || 'TSTALIASID';
const freeAgentId = process.env.BEDROCK_FREE_AGENT_ID;
const freeAgentAliasId = process.env.BEDROCK_FREE_AGENT_ALIAS_ID || 'TSTALIASID';

if (!proAgentId) {
  throw new Error('BEDROCK_PRO_AGENT_ID must be set in .env file');
}
if (!freeAgentId) {
  throw new Error('BEDROCK_FREE_AGENT_ID must be set in .env file');
}

// Optional foundation model overrides
const proFoundationModel = process.env.BEDROCK_PRO_FOUNDATION_MODEL;
const freeFoundationModel = process.env.BEDROCK_FREE_FOUNDATION_MODEL;

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.AWS_REGION || process.env.CDK_DEFAULT_REGION || 'us-east-1',
};

// Deploy Bedrock Proxy Stack (Lambda + API Gateway)
new BedrockProxyStack(app, `AmigoBedrockProxyStack-${environment}`, {
  env,
  environment,
  supabaseUrl,
  supabaseAnonKey,
  supabaseJwtPublicKey,
  proAgentId,
  proAgentAliasId,
  freeAgentId,
  freeAgentAliasId,
  description: `Amigo Bedrock Proxy Stack - ${environment}`,
});

// Deploy Bedrock Agent Stack (Agent + Action Groups)
new BedrockAgentStack(app, `AmigoBedrockAgentStack-${environment}`, {
  env,
  environment,
  supabaseUrl,
  supabaseAnonKey,
  ...(proFoundationModel ? { proFoundationModel } : {}),
  ...(freeFoundationModel ? { freeFoundationModel } : {}),
  description: `Amigo Bedrock Agent Stack - ${environment}`,
});

// Add tags to all resources
cdk.Tags.of(app).add('Project', 'Amigo');
cdk.Tags.of(app).add('Environment', environment);
cdk.Tags.of(app).add('ManagedBy', 'CDK');

app.synth();

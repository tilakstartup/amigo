/**
 * Model provider resolution for the strands-agent edge function.
 * Reads env vars to determine which LLM provider and model ID to use.
 */

export type ModelProvider = 'bedrock' | 'openai' | 'deepseek'

export interface ModelConfig {
  modelProvider: ModelProvider
  modelId: string
}

const VALID_PROVIDERS: ModelProvider[] = ['bedrock', 'openai', 'deepseek']

const DEFAULT_MODEL_IDS: Record<ModelProvider, string> = {
  bedrock: 'amazon.nova-lite-v1:0',
  openai: 'gpt-4o',
  deepseek: 'deepseek-chat',
}

const DEFAULT_PRO_MODEL_IDS: Record<ModelProvider, string> = {
  bedrock: 'us.anthropic.claude-sonnet-4-20250514-v1:0',
  openai: 'gpt-4o',
  deepseek: 'deepseek-chat',
}

function resolveProvider(envVar: string | undefined): ModelProvider {
  const raw = envVar?.toLowerCase()
  if (raw && (VALID_PROVIDERS as string[]).includes(raw)) {
    return raw as ModelProvider
  }
  if (raw) {
    console.warn(`[model] Unrecognized provider "${raw}", defaulting to "bedrock"`)
  }
  return 'bedrock'
}

/**
 * Resolves the model provider and model ID based on subscription status.
 * Pro + active users get PRO_MODEL_PROVIDER / PRO_MODEL_ID.
 * All others get FREE_MODEL_PROVIDER / FREE_MODEL_ID.
 */
export function resolveModel(subscriptionStatus: string, isActive: boolean): ModelConfig {
  const isPro = subscriptionStatus === 'pro' && isActive

  const providerEnvVar = isPro
    ? Deno.env.get('PRO_MODEL_PROVIDER')
    : Deno.env.get('FREE_MODEL_PROVIDER')

  const modelIdEnvVar = isPro
    ? Deno.env.get('PRO_MODEL_ID')
    : Deno.env.get('FREE_MODEL_ID')

  const modelProvider = resolveProvider(providerEnvVar)
  const defaultModelId = isPro ? DEFAULT_PRO_MODEL_IDS[modelProvider] : DEFAULT_MODEL_IDS[modelProvider]
  const modelId = modelIdEnvVar ?? defaultModelId

  console.log(`[model] resolveModel: subscriptionStatus=${subscriptionStatus} isActive=${isActive} isPro=${isPro} modelId=${modelId}`)
  return { modelProvider, modelId }
}

/**
 * strands-agent edge function — URL router + three endpoint handlers.
 *
 * Routes:
 *   POST /create-session
 *   GET  /get-session
 *   POST /send-message
 */

import { Agent } from '@strands-agents/sdk'
import { BedrockModel } from '@strands-agents/sdk/bedrock'
import { OpenAIModel } from '@strands-agents/sdk/openai'
import { NodeHttpHandler } from '@smithy/node-http-handler'
// Force bundler to include @aws-sdk/client-s3 (required by @strands-agents/sdk S3Storage)
import '@aws-sdk/client-s3'
// Force bundler to include openai (required by @strands-agents/sdk OpenAIModel)
import 'openai'
import { createClient } from '@supabase/supabase-js'
import { verifyJwt } from '../_shared/auth.ts'
import * as db from '../_shared/db.ts'
import {
  buildAgentResponse,
  buildFallbackCompletion,
  validateAgentJson,
} from '../_shared/response.ts'
import {
  buildSystemPrompt,
  buildSystemPromptBlocks,
  getSessionConfig,
  isSessionExpired,
  trimMessages,
} from '../_shared/session.ts'
import { accumulateDataCollected, normalizeLegacyDataCollected } from '../_shared/accumulate.ts'
import { buildTools } from './tools.ts'
import { resolveModel } from './model.ts'
import type { SessionConfig, DataCollected } from '../_shared/types.ts'

// ─── CORS ─────────────────────────────────────────────────────────────────────

const CORS_HEADERS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type',
}

function corsResponse(): Response {
  return new Response(null, { status: 200, headers: CORS_HEADERS })
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
  })
}

// ─── Supabase clients ─────────────────────────────────────────────────────────

function makeAnonClient(jwt: string) {
  return createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_ANON_KEY')!,
    { global: { headers: { Authorization: `Bearer ${jwt}` } } },
  )
}

function makeServiceClient() {
  return createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
  )
}

// ─── Agent builder ────────────────────────────────────────────────────────────

function buildAgent(params: {
  sessionConfig: SessionConfig
  dataCollected: DataCollected
  subscriptionStatus: string
  isActive: boolean
  tools: ReturnType<typeof buildTools>
}): { agent: Agent; systemPrompt: string } {
  const { modelProvider, modelId } = resolveModel(params.subscriptionStatus, params.isActive)
  const isPro = params.subscriptionStatus === 'pro' && params.isActive
  const systemPrompt = buildSystemPrompt(params.sessionConfig, params.dataCollected)

  let model: BedrockModel | OpenAIModel
  // systemPrompt passed to Agent — for Bedrock we use the block array (with cachePoint),
  // for other providers we use the plain string.
  // deno-lint-ignore no-explicit-any
  let agentSystemPrompt: any

  if (modelProvider === 'bedrock') {
    model = new BedrockModel({
      modelId,
      region: Deno.env.get('AWS_REGION') ?? 'us-east-1',
      // Force HTTP/1.1 — Deno's node:http2 polyfill is incomplete (kSessionFrameErrorListenerCount)
      clientConfig: { requestHandler: new NodeHttpHandler() },
    })
    if (isPro) {
      model.updateConfig({
        // Cache tool definitions — static across all turns, saves tokens on every request
        cacheTools: 'default',
      })
    }
    
    // Block array with two cachePoints — static prefix cached globally, session config cached per-session.
    // For pro models dataCollected is omitted (model tracks from history), making both caches fully static.
    agentSystemPrompt = buildSystemPromptBlocks(params.sessionConfig, params.dataCollected, isPro)
  } else if (modelProvider === 'openai') {
    model = new OpenAIModel({
      modelId,
      apiKey: Deno.env.get('OPENAI_API_KEY'),
    })
    agentSystemPrompt = systemPrompt
  } else {
    // deepseek — OpenAI-compatible API
    model = new OpenAIModel({
      modelId,
      apiKey: Deno.env.get('DEEPSEEK_API_KEY'),
      clientConfig: { baseURL: 'https://api.deepseek.com' },
    })
    agentSystemPrompt = systemPrompt
  }

  return { agent: new Agent({ model, systemPrompt: agentSystemPrompt, tools: params.tools }), systemPrompt }
}

// ─── Handlers ─────────────────────────────────────────────────────────────────

async function handleCreateSession(req: Request): Promise<Response> {
  const body = await req.json().catch(() => ({}))
  const hat: string | undefined = body?.hat
  if (!hat) return json({ error: 'hat is required' }, 400)

  // JWT verification — anonymous allowed for onboarding hat
  const authHeader = req.headers.get('Authorization') ?? ''
  const jwt = authHeader.replace(/^Bearer\s+/i, '')

  let userId: string
  let subscriptionStatus = 'free'

  if (hat === 'onboarding' && !jwt) {
    userId = `onboarding-anon-${Date.now()}`
  } else {
    const payload = await verifyJwt(jwt)
    if (!payload) return json({ error: 'Unauthorized' }, 401)
    userId = payload.userId
    subscriptionStatus = payload.subscriptionStatus
  }

  const anonClient = jwt ? makeAnonClient(jwt) : makeServiceClient()
  const serviceClient = makeServiceClient()
  const { resumeMessages } = getSessionConfig()

  // Try to resume an existing session (SELECT — anon client with RLS)
  const existing = await db.findResumableSession(anonClient, userId, hat)
  if (existing) {
    const messages = trimMessages(existing.messages, resumeMessages)
    return json({
      sessionId: existing.session_id,
      isResumed: true,
      messages,
      data_collected: normalizeLegacyDataCollected(existing.data_collected),
      aim_status: existing.aim_status,
    })
  }

  // No resumable session — expire any stale ones and create fresh (writes use service-role)
  await db.expireSessionsForUser(serviceClient, userId, hat)
  const sessionId = crypto.randomUUID()
  const sessionConfig: SessionConfig = body?.sessionConfig ?? { hat, responsibilities: [], data_to_be_collected: [], data_to_be_calculated: [], notes: [] }

  await db.createSession(serviceClient, {
    session_id: sessionId,
    user_id: userId,
    hat,
    session_config: sessionConfig,
    messages: [],
    data_collected: [],
    aim_status: 'not_set',
    is_expired: false,
    token_usage: [],
    total_input_tokens: 0,
    total_output_tokens: 0,
  })

  return json({
    sessionId,
    isResumed: false,
    messages: [],
    data_collected: [],
    aim_status: 'not_set',
    sessionConfig,
  })
}

async function handleGetSession(req: Request, url: URL): Promise<Response> {
  const authHeader = req.headers.get('Authorization') ?? ''
  const jwt = authHeader.replace(/^Bearer\s+/i, '')
  const payload = await verifyJwt(jwt)
  if (!payload) return json({ error: 'Unauthorized' }, 401)

  const sessionId = url.searchParams.get('sessionId')
  if (!sessionId) return json({ error: 'sessionId is required' }, 400)

  const client = makeAnonClient(jwt)
  const { resumeMessages } = getSessionConfig()

  const session = await db.getSession(client, sessionId, payload.userId)
  if (!session) return json({ error: 'Session not found' }, 404)

  const messages = trimMessages(session.messages, resumeMessages)
  return json({
    sessionId: session.session_id,
    hat: session.hat,
    aim_status: session.aim_status,
    is_expired: session.is_expired,
    data_collected: normalizeLegacyDataCollected(session.data_collected),
    messages,
  })
}

async function handleEndSession(req: Request): Promise<Response> {
  const body = await req.json().catch(() => ({}))
  const { sessionId } = body ?? {}
  if (!sessionId) return json({ error: 'sessionId is required' }, 400)

  const authHeader = req.headers.get('Authorization') ?? ''
  const jwt = authHeader.replace(/^Bearer\s+/i, '')
  const payload = await verifyJwt(jwt)
  if (!payload) return json({ error: 'Unauthorized' }, 401)

  const serviceClient = makeServiceClient()
  const anonClient = makeAnonClient(jwt)

  // Verify the session belongs to this user before expiring
  const session = await db.getSession(anonClient, sessionId, payload.userId)
  if (!session) return json({ error: 'Session not found' }, 404)

  await db.expireSession(serviceClient, sessionId)
  return json({ success: true, sessionId })
}

async function handleSendMessage(req: Request): Promise<Response> {
  const body = await req.json().catch(() => ({}))
  const { sessionId, message, sessionConfig: bodySessionConfig } = body ?? {}

  if (!sessionId) return json({ error: 'sessionId is required' }, 400)
  if (!message) return json({ error: 'message is required' }, 400)

  const authHeader = req.headers.get('Authorization') ?? ''
  const jwt = authHeader.replace(/^Bearer\s+/i, '')
  const payload = await verifyJwt(jwt)
  if (!payload) return json({ error: 'Unauthorized' }, 401)

  const { userId, subscriptionStatus, isActive } = payload
  console.log('[strands-agent] auth payload:', { userId, subscriptionStatus, isActive })
  const client = makeAnonClient(jwt)
  const serviceClient = makeServiceClient()
  const { expiryHours, maxMessages } = getSessionConfig()

  // Load session
  const session = await db.getSession(client, sessionId, userId)
  if (!session || session.is_expired) {
    return json({ error: 'Session expired or not found' }, 400)
  }

  // Check expiry conditions
  if (isSessionExpired(session, { sessionExpiryHours: expiryHours, sessionMaxMessages: maxMessages })) {
    await db.expireSession(serviceClient, sessionId)
    return json({ error: 'Session expired or not found' }, 400)
  }

  // Resolve session config — prefer client-provided, fall back to persisted, then bare minimum
  const sessionConfig: SessionConfig = bodySessionConfig ?? session.session_config ?? {
    hat: session.hat,
    responsibilities: [],
    data_to_be_collected: [],
    data_to_be_calculated: [],
    notes: [],
  }

  // Build tools and agent
  const { modelProvider, modelId } = resolveModel(subscriptionStatus, isActive)
  const tools = buildTools({ supabaseClient: client, serviceClient, userId })
  const { agent } = buildAgent({ sessionConfig, dataCollected: normalizeLegacyDataCollected(session.data_collected), subscriptionStatus, isActive, tools })

  // Build full message history for the agent
  // For legacy assistant messages stored as raw JSON blobs, extract just the render.text
  const history = [
    ...session.messages.map(m => {
      let text = m.content.replace(/<thinking>[\s\S]*?<\/thinking>/gi, '').trim()
      // Legacy: assistant messages stored as full JSON — extract render.text if possible
      if (m.role === 'assistant' && text.startsWith('{')) {
        try {
          const parsed = JSON.parse(text)
          if (parsed?.ui?.render?.text) text = parsed.ui.render.text
        } catch { /* not JSON, use as-is */ }
      }
      return { role: m.role as 'user' | 'assistant', content: [{ type: 'text' as const, text }] }
    }),
    { role: 'user' as const, content: [{ type: 'text' as const, text: message }] },
  ]

  // Stream agent — drain the async generator to capture both the result and cache token events.
  // modelMetadataEvent is wrapped inside ModelStreamUpdateEvent (event.event).
  // AgentResultEvent carries the final AgentResult (event.result).
  let inputTokens = 0, outputTokens = 0, cacheReadInputTokens = 0, cacheWriteInputTokens = 0
  // deno-lint-ignore no-explicit-any
  let agentResult: any = null
  for await (const event of agent.stream(history)) {
    if (event.type === 'modelStreamUpdateEvent' && event.event.type === 'modelMetadataEvent' && event.event.usage) {
      inputTokens += event.event.usage.inputTokens ?? 0
      outputTokens += event.event.usage.outputTokens ?? 0
      cacheReadInputTokens += event.event.usage.cacheReadInputTokens ?? 0
      cacheWriteInputTokens += event.event.usage.cacheWriteInputTokens ?? 0
    } else if (event.type === 'agentResultEvent') {
      agentResult = event.result
    }
  }
  const responseText: string = agentResult?.toString() ?? ''
  console.log('[strands-agent] raw response:', responseText.slice(0, 500))

  // Validate JSON — one retry with correction prompt if needed
  let validationResult = validateAgentJson(responseText)
  if (!validationResult.valid) {
    console.log('[strands-agent] validation failed:', validationResult.error)
    const correctionPrompt = `Your previous response was not valid JSON. Error: ${validationResult.error}\n\nOriginal response:\n${responseText}\n\nPlease respond with ONLY a valid JSON object matching the required schema. No markdown, no explanation.`
    // deno-lint-ignore no-explicit-any
    let retryAgentResult: any = null
    for await (const event of agent.stream(correctionPrompt)) {
      if (event.type === 'modelStreamUpdateEvent' && event.event.type === 'modelMetadataEvent' && event.event.usage) {
        inputTokens += event.event.usage.inputTokens ?? 0
        outputTokens += event.event.usage.outputTokens ?? 0
        cacheReadInputTokens += event.event.usage.cacheReadInputTokens ?? 0
        cacheWriteInputTokens += event.event.usage.cacheWriteInputTokens ?? 0
      } else if (event.type === 'agentResultEvent') {
        retryAgentResult = event.result
      }
    }
    const retryText: string = retryAgentResult?.toString() ?? ''
    console.log('[strands-agent] retry response:', retryText.slice(0, 500))
    validationResult = validateAgentJson(retryText)
    if (!validationResult.valid) {
      console.log('[strands-agent] retry validation failed:', validationResult.error)
    }
  }

  // Build turnTokens after all stream passes (includes retry tokens if any)
  const turnTokens = { inputTokens, outputTokens, cacheReadInputTokens, cacheWriteInputTokens, modelId, modelProvider }
  console.log('[strands-agent] token usage:', turnTokens)

  let completion = validationResult.valid ? validationResult.parsed : buildFallbackCompletion()

  // Accumulate data_collected
  const updatedDataCollected = accumulateDataCollected(
    normalizeLegacyDataCollected(session.data_collected),
    completion.new_data_collected,
  )

  // Build response
  const agentResponse = buildAgentResponse({
    completion,
    data_collected: updatedDataCollected,
    userId,
    subscriptionStatus,
  })

  // Persist session — await directly so failures are visible in logs
  // Store only the rendered text as the assistant message — not the raw JSON/thinking blocks.
  // This keeps history lean and avoids confusing the model with its own schema on the next turn.
  const assistantText = completion.ui.render.text
  const updatedMessages = trimMessages(
    [...session.messages, { role: 'user', content: message }, { role: 'assistant', content: assistantText }],
    maxMessages,
  )
  try {
    const existingTokenUsage = Array.isArray(session.token_usage) ? session.token_usage : []
    const updatedTokenUsage = [...existingTokenUsage, turnTokens]
    const totalInputTokens = updatedTokenUsage.reduce((sum, t) => sum + t.inputTokens, 0)
    const totalOutputTokens = updatedTokenUsage.reduce((sum, t) => sum + t.outputTokens, 0)
    await db.upsertSession(serviceClient, {
      session_id: sessionId,
      session_config: sessionConfig,
      messages: updatedMessages,
      data_collected: updatedDataCollected,
      aim_status: completion.status_of_aim,
      token_usage: updatedTokenUsage,
      total_input_tokens: totalInputTokens,
      total_output_tokens: totalOutputTokens,
    })
  } catch (e) {
    console.error('[strands-agent] upsertSession failed:', e instanceof Error ? e.message : String(e))
  }

  return json(agentResponse)
}

// ─── Router ───────────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return corsResponse()

  const url = new URL(req.url)
  // Strip the function name prefix so both /strands-agent/create-session and /create-session work
  const path = url.pathname.replace(/^\/strands-agent/, '')

  try {
    if (req.method === 'POST' && path === '/create-session') return await handleCreateSession(req)
    if (req.method === 'GET' && path === '/get-session') return await handleGetSession(req, url)
    if (req.method === 'POST' && path === '/end-session') return await handleEndSession(req)
    if (req.method === 'POST' && path === '/send-message') return await handleSendMessage(req)
    return json({ error: 'Not found' }, 404)
  } catch (e) {
    const msg = e instanceof Error ? `${e.message}\n${e.stack}` : String(e)
    console.error('[strands-agent] unhandled error:', msg)
    return json({ error: "I'm having trouble connecting right now. Please try again." }, 500)
  }
})

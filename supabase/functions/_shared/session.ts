/**
 * Session lifecycle helpers for the strands-agent edge function.
 */

import { TextBlock, CachePointBlock } from '@strands-agents/sdk'
import type { SessionRecord, ConversationMessage, SessionConfig, DataCollected } from './types.ts'

// ─── Session config ───────────────────────────────────────────────────────────

export function getSessionConfig(): { expiryHours: number; maxMessages: number; resumeMessages: number } {
  return {
    expiryHours: Number(Deno.env.get('SESSION_EXPIRY_HOURS') ?? '1'),
    maxMessages: Number(Deno.env.get('SESSION_MAX_MESSAGES') ?? '100'),
    resumeMessages: Number(Deno.env.get('SESSION_RESUME_MESSAGES') ?? '50'),
  }
}

// ─── Expiry check ─────────────────────────────────────────────────────────────

export function isSessionExpired(
  session: Pick<SessionRecord, 'updated_at' | 'messages'>,
  params: { sessionExpiryHours: number; sessionMaxMessages: number },
): boolean {
  const updatedAt = new Date(session.updated_at).getTime()
  const expiryMs = params.sessionExpiryHours * 60 * 60 * 1000
  if (Date.now() - updatedAt > expiryMs) return true
  if (session.messages.length >= params.sessionMaxMessages) return true
  return false
}

// ─── Message trimming ─────────────────────────────────────────────────────────

export function trimMessages(messages: ConversationMessage[], maxCount: number): ConversationMessage[] {
  if (messages.length <= maxCount) return messages
  return messages.slice(messages.length - maxCount)
}

// ─── Token estimation ─────────────────────────────────────────────────────────

/**
 * Rough token estimate: ~4 chars per token (works for English/JSON text).
 * Good enough for cost tracking without a full tokenizer.
 */
export function estimateTokens(text: string): number {
  return Math.ceil(text.length / 4)
}

// ─── System prompt builder ────────────────────────────────────────────────────

// Content block types — SDK class instances (TextBlock | CachePointBlock)
// These are passed directly as SystemPrompt array to the Agent constructor.
export type SystemBlock = TextBlock | CachePointBlock

/**
 * Builds the static prefix — identical for every session and every turn.
 * This is the cacheable portion: place a cachePoint immediately after it.
 */
function buildStaticPrefix(): string {
  const lines: string[] = []
  lines.push('You are Amigo an AI health coach assistant.')
  lines.push('')
  lines.push('ABSOLUTE RULE: When you have tools available, you MUST call them before generating a response.')
  lines.push('NEVER fabricate calculated values (BMR, TDEE, calories, etc.) — always invoke the tool and use its returned result.')
  lines.push('')
  lines.push('## Response Format')
  lines.push('You MUST respond with ONLY a valid JSON object. No markdown, no XML tags, no text before or after the JSON.')
  lines.push('')
  lines.push('Every response MUST contain ALL of these top-level fields: "status_of_aim", "ui", "input", "new_data_collected".')
  lines.push('')
  lines.push('- "status_of_aim": one of "not_set", "in_progress", or "completed"')
  lines.push('- "ui": object with "render" object containing:')
  lines.push('  - "render.type": one of "info", "message", or "message_with_summary"')
  lines.push('  - "render.text": your message to the user (non-empty string)')
  lines.push('- "input": REQUIRED in every response. NEVER omit this field. NEVER set it to null (except for "info" type). Must be an object with:')
  lines.push('  - "type": one of "text", "weight", "date", "quick_pills", "yes_no", "dropdown"')
  lines.push('  - "options": array (empty [] unless type is "quick_pills", "yes_no", or "dropdown")')
  lines.push('- "new_data_collected": CRITICAL — when the user provides a value in their message, you MUST capture it here immediately in the SAME response. Do NOT wait for the next turn. Array of NEW data collected THIS turn. Empty [] only if the user provided no new information. Each entry:')
  lines.push('  - "field": the field name key, e.g. "current_weight"')
  lines.push('  - "label": SHORT display name for the UI — NOT the question text. Examples:')
  lines.push('    - field "current_weight" → label "Current Weight"')
  lines.push('    - field "target_weight" → label "Target Weight"')
  lines.push('    - field "height_cm" → label "Height"')
  lines.push('    - field "age" → label "Age"')
  lines.push('    - field "gender" → label "Gender"')
  lines.push('    - field "activity_level" → label "Activity Level"')
  lines.push('    - field "target_date" → label "Target Date"')
  lines.push('    - field "goal_type" → label "Goal Type"')
  lines.push('  - "value": the collected value as a string, or null')
  lines.push('')
  lines.push('render.type rules:')
  lines.push('- "info" → input.type MUST be "text", options: []')
  lines.push('- "message" → input.type is whatever matches the question being asked')
  lines.push('- "message_with_summary" → CRITICAL: input field is REQUIRED and MUST be { "type": "yes_no", "options": [{"label":"Yes","value":"yes"},{"label":"No","value":"no"}] }')
  lines.push('  - "message_with_summary" status_of_aim MUST be "in_progress" (not "completed" — user has not confirmed yet)')
  lines.push('')
  lines.push('Example — asking a question:')
  lines.push('{')
  lines.push('  "status_of_aim": "in_progress",')
  lines.push('  "ui": { "render": { "type": "message", "text": "What is your current weight?" } },')
  lines.push('  "input": { "type": "weight", "options": [] },')
  lines.push('  "new_data_collected": []')
  lines.push('}')
  lines.push('')
  lines.push('Example — user just answered with their weight (MUST capture in new_data_collected in THIS response):')
  lines.push('{')
  lines.push('  "status_of_aim": "in_progress",')
  lines.push('  "ui": { "render": { "type": "message", "text": "Got it! What is your height?" } },')
  lines.push('  "input": { "type": "text", "options": [] },')
  lines.push('  "new_data_collected": [{ "field": "current_weight", "label": "Current Weight", "value": "75 kg" }]')
  lines.push('}')
  lines.push('NOTE: The user said their weight in the PREVIOUS message. This response acknowledges it, asks the next question, AND records the weight. This is correct.')
  lines.push('')
  lines.push('WRONG (never do this — new_data_collected is empty even though user just gave their weight):')
  lines.push('{')
  lines.push('  "status_of_aim": "in_progress",')
  lines.push('  "ui": { "render": { "type": "message", "text": "Got it! What is your height?" } },')
  lines.push('  "input": { "type": "text", "options": [] },')
  lines.push('  "new_data_collected": []')
  lines.push('}')
  lines.push('')
  lines.push('Example — summary confirmation (message_with_summary ALWAYS needs yes_no with exactly 2 options, status MUST be in_progress):')
  lines.push('{')
  lines.push('  "status_of_aim": "in_progress",')
  lines.push('  "ui": { "render": { "type": "message_with_summary", "text": "Here is your plan. Does this look correct?" } },')
  lines.push('  "input": { "type": "yes_no", "options": [{ "label": "Yes", "value": "yes" }, { "label": "No", "value": "no" }] },')
  lines.push('  "new_data_collected": []')
  lines.push('}')
  lines.push('')
  lines.push('IMPORTANT: Output ONLY the JSON object. No markdown code blocks. No explanation. The "input" and "new_data_collected" fields are MANDATORY in every response.')
  return lines.join('\n')
}

/**
 * Builds the session config block — static for the lifetime of a session.
 * Cached with a cachePoint after it (second cache layer).
 */
function buildSessionConfigBlock(sessionConfig: SessionConfig): string {
  const lines: string[] = []

  lines.push(`## Your Role`)
  lines.push(`You are acting as: ${sessionConfig.hat}.`)
  lines.push('')

  if (sessionConfig.responsibilities.length > 0) {
    lines.push('## Responsibilities')
    for (const r of sessionConfig.responsibilities) lines.push(`- ${r}`)
    lines.push('')
  }

  if (sessionConfig.data_to_be_collected.length > 0) {
    lines.push('## Data to Collect')
    for (const d of sessionConfig.data_to_be_collected) lines.push(`- ${d}`)
    lines.push('')
  }

  if (sessionConfig.data_to_be_calculated.length > 0) {
    lines.push('## Data to Calculate (MUST use tool calls — never fabricate these values)')
    for (const d of sessionConfig.data_to_be_calculated) lines.push(`- ${d}`)
    lines.push('')
    lines.push('CRITICAL: Every field listed above MUST be obtained by calling the corresponding tool.')
    lines.push('NEVER estimate, guess, or calculate these values yourself. Always invoke the tool and use its returned value.')
    lines.push('')
  }

  if (sessionConfig.notes.length > 0) {
    lines.push('## Notes (HARD CONSTRAINTS — follow exactly)')
    for (const n of sessionConfig.notes) lines.push(`- ${n}`)
    lines.push('')
  }

  return lines.join('\n')
}

/**
 * Builds the data collected block — changes each turn as the user provides data.
 * This is the only portion re-processed on every request.
 */
function buildDataCollectedBlock(dataCollected: DataCollected): string {
  if (dataCollected.length === 0) return ''
  const lines: string[] = []
  lines.push('## Already Collected Data (DO NOT ask for these again, DO NOT include in new_data_collected)')
  for (const entry of dataCollected) {
    lines.push(`- ${entry.label} (${entry.field}): ${entry.value ?? 'not provided'}`)
  }
  lines.push('')
  lines.push('REMINDER: new_data_collected must only contain fields collected for the FIRST TIME this turn. Never repeat fields from the list above.')
  return lines.join('\n')
}

/**
 * Returns system prompt as an array of blocks with two cachePoints:
 *   1. After the static prefix (instructions) — cached across all sessions
 *   2. After the session config (hat/responsibilities/etc.) — cached per-session
 *
 * For pro models: dataCollected is omitted — the model tracks context from conversation
 * history, making the entire system prompt fully static per-session (both caches hold).
 *
 * For free models: dataCollected is appended after the second cachePoint so the model
 * doesn't lose track of already-collected fields.
 */
export function buildSystemPromptBlocks(
  sessionConfig: SessionConfig,
  dataCollected: DataCollected = [],
  isPro = false,
): SystemBlock[] {
  const blocks: SystemBlock[] = [
    new TextBlock(buildStaticPrefix()),
    new CachePointBlock({ cacheType: 'default' }),
    new TextBlock(buildSessionConfigBlock(sessionConfig)),
    new CachePointBlock({ cacheType: 'default' }),
  ]
  if (!isPro) {
    const dataBlock = buildDataCollectedBlock(dataCollected)
    if (dataBlock) blocks.push(new TextBlock(dataBlock))
  }
  return blocks
}

/**
 * Returns system prompt as a plain string (for non-Bedrock models or token estimation).
 */
export function buildSystemPrompt(sessionConfig: SessionConfig, dataCollected: DataCollected = []): string {
  return [buildStaticPrefix(), buildSessionConfigBlock(sessionConfig), buildDataCollectedBlock(dataCollected)]
    .filter(Boolean)
    .join('\n')
}

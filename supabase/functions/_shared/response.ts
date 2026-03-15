/**
 * Response builder utilities for the strands-agent edge function.
 */

import type {
  AgentCompletion,
  AgentResponse,
  DataCollected,
  AimStatus,
  RenderType,
  InputType,
} from './types.ts'

// ─── Exported types ───────────────────────────────────────────────────────────

export type ValidationResult =
  | { valid: true; parsed: AgentCompletion }
  | { valid: false; error: string }

// ─── Label helpers ────────────────────────────────────────────────────────────

function toLabel(field: string): string {
  return field
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

// ─── Public API ───────────────────────────────────────────────────────────────

export function buildAgentResponse(params: {
  completion: AgentCompletion
  data_collected: DataCollected
  userId: string
  subscriptionStatus: string
}): AgentResponse {
  return {
    completion: params.completion,
    data_collected: params.data_collected,
    invocations: null,
    invocationId: null,
    error: null,
    userId: params.userId,
    subscription_status: params.subscriptionStatus,
    timestamp: new Date().toISOString(),
  }
}

export function buildErrorResponse(params: {
  error: string
  userId?: string
  subscriptionStatus?: string
}): AgentResponse {
  return {
    completion: null,
    data_collected: null,
    invocations: null,
    invocationId: null,
    error: params.error,
    userId: params.userId ?? null,
    subscription_status: params.subscriptionStatus ?? null,
    timestamp: new Date().toISOString(),
  }
}

export function buildFallbackCompletion(): AgentCompletion {
  return {
    status_of_aim: 'in_progress',
    ui: {
      render: {
        type: 'message',
        text: "I'm having trouble connecting right now. Please try again.",
      },
    },
    input: { type: 'text' },
    new_data_collected: [],
  }
}

export function validateAgentJson(text: string): ValidationResult {
  // Step 1: Strip <thinking>...</thinking> blocks (Nova Lite chain-of-thought)
  let cleaned = text.replace(/<thinking>[\s\S]*?<\/thinking>/gi, '').trim()

  // Step 2: Strip markdown fences
  cleaned = cleaned
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/\s*```$/i, '')
    .trim()

  // Step 3: Strip XML wrappers like <response>...</response>
  cleaned = cleaned.replace(/^<[^>]+>\s*/i, '').replace(/\s*<\/[^>]+>$/i, '').trim()

  // Step 3: Sanitize literal control characters inside JSON string values
  // Replace literal newlines, carriage returns, tabs that appear inside strings
  cleaned = cleaned.replace(
    /"(?:[^"\\]|\\.)*"/g,
    (match) =>
      match
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r')
        .replace(/\t/g, '\\t'),
  )

  // Step 4: JSON.parse
  let parsed: unknown
  try {
    parsed = JSON.parse(cleaned)
  } catch (e) {
    return { valid: false, error: `JSON parse failed: ${(e as Error).message}` }
  }

  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return { valid: false, error: 'Parsed value is not an object' }
  }

  const obj = parsed as Record<string, unknown>

  // Step 5: Required fields — accept legacy names and remap to new_data_collected
  // latest_field_collected (single object) → wrap in array
  if ('latest_field_collected' in obj && !('new_data_collected' in obj)) {
    const lfc = obj.latest_field_collected
    obj.new_data_collected = lfc != null ? [lfc] : []
    delete obj.latest_field_collected
  }
  // previous_field_collected (older legacy) → wrap in array
  if ('previous_field_collected' in obj && !('new_data_collected' in obj)) {
    const pfc = obj.previous_field_collected
    obj.new_data_collected = pfc != null ? [pfc] : []
    delete obj.previous_field_collected
  }
  // Coerce missing field to empty array
  if (!('new_data_collected' in obj)) {
    obj.new_data_collected = []
  }

  const requiredFields = ['status_of_aim', 'ui'] as const
  for (const field of requiredFields) {
    if (!(field in obj)) {
      return { valid: false, error: `Missing required field: ${field}` }
    }
  }

  // Step 6: status_of_aim enum
  const validAimStatuses: AimStatus[] = ['not_set', 'in_progress', 'completed']
  if (!validAimStatuses.includes(obj.status_of_aim as AimStatus)) {
    return { valid: false, error: `Invalid status_of_aim: ${obj.status_of_aim}` }
  }

  // Step 7: ui.render.type enum
  const ui = obj.ui as Record<string, unknown> | undefined
  if (typeof ui !== 'object' || ui === null) {
    return { valid: false, error: 'ui must be an object' }
  }
  const render = ui.render as Record<string, unknown> | undefined
  if (typeof render !== 'object' || render === null) {
    return { valid: false, error: 'ui.render must be an object' }
  }
  const validRenderTypes: RenderType[] = ['info', 'message', 'message_with_summary']
  if (!validRenderTypes.includes(render.type as RenderType)) {
    return { valid: false, error: `Invalid ui.render.type: ${render.type}` }
  }

  // Step 8: ui.render.text is a non-empty string
  if (typeof render.text !== 'string' || render.text.length === 0) {
    return { valid: false, error: 'ui.render.text must be a non-empty string' }
  }

  // Step 9: input validation — coerce missing/null input for message_with_summary to yes_no
  const renderType = render.type as RenderType
  let input = obj.input

  if (renderType === 'message_with_summary' && (input === null || input === undefined)) {
    // Model forgot to include input — coerce to yes_no per spec
    input = { type: 'yes_no', options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] }
    obj.input = input
  }

  if (renderType === 'info' && (input === null || input === undefined)) {
    // null/absent is valid for info renders — skip input validation
  } else {
    if (typeof input !== 'object' || input === null || Array.isArray(input)) {
      return { valid: false, error: 'input must be an object' }
    }
    const inputObj = input as Record<string, unknown>
    const validInputTypes: InputType[] = ['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown']
    if (!validInputTypes.includes(inputObj.type as InputType)) {
      return { valid: false, error: `Invalid input.type: ${inputObj.type}` }
    }
  }

  // Step 10: new_data_collected — must be an array of valid entries
  const ndc = obj.new_data_collected
  if (!Array.isArray(ndc)) {
    obj.new_data_collected = []
  } else {
    for (const entry of ndc as unknown[]) {
      if (typeof entry !== 'object' || entry === null || Array.isArray(entry)) {
        return { valid: false, error: 'new_data_collected entries must be objects' }
      }
      const e = entry as Record<string, unknown>
      if (typeof e.field !== 'string' || e.field.length === 0) {
        return { valid: false, error: 'new_data_collected entry.field must be a non-empty string' }
      }
      if (typeof e.label !== 'string' || e.label.length === 0) {
        return { valid: false, error: 'new_data_collected entry.label must be a non-empty string' }
      }
      if (e.value !== null && typeof e.value !== 'string') {
        return { valid: false, error: 'new_data_collected entry.value must be string or null' }
      }
    }
  }

  return { valid: true, parsed: obj as unknown as AgentCompletion }
}

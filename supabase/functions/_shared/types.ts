// Shared TypeScript interfaces for the strands-agent edge function.
// No runtime logic — types only.

// ─── Enums / literals ────────────────────────────────────────────────────────

export type AimStatus = 'not_set' | 'in_progress' | 'completed'
export type RenderType = 'info' | 'message' | 'message_with_summary'
export type InputType = 'text' | 'weight' | 'date' | 'quick_pills' | 'yes_no' | 'dropdown'
export type SubscriptionStatus = 'free' | 'pro'
export type Gender = 'male' | 'female'
export type ActivityLevel =
  | 'sedentary'
  | 'lightly_active'
  | 'moderately_active'
  | 'very_active'
  | 'extra_active'
export type GoalType = 'weight_loss' | 'muscle_gain' | 'maintenance'
export type MessageRole = 'user' | 'assistant'

// ─── Core agent response types ────────────────────────────────────────────────

export interface AgentResponse {
  completion: AgentCompletion | null
  data_collected: DataCollectedEntry[] | null
  invocations: FunctionInvocation[] | null
  invocationId: string | null
  error: string | null
  userId: string | null
  subscription_status: string | null
  timestamp: string
}

export interface AgentCompletion {
  status_of_aim: AimStatus
  ui: {
    render: {
      type: RenderType
      text: string
    }
    tone?: string
  }
  // null when render.type === 'info' (auto-acknowledged by client, no input needed)
  input: {
    type: InputType
    options?: Array<{ label: string; value: string }>
  } | null
  new_data_collected: Array<{
    field: string
    label: string
    value: string | null
  }>
}

// ─── data_collected ───────────────────────────────────────────────────────────

// Wire format — stored in DB as JSONB array and returned to the mobile client
export interface DataCollectedEntry {
  field: string
  label: string
  value: string | null
}

// Stored as DataCollectedEntry[] in DB and on the wire
export type DataCollected = DataCollectedEntry[]

// ─── Invocations (legacy / passthrough) ──────────────────────────────────────

export interface FunctionInvocation {
  action_group: string
  function_name: string
  params: Record<string, string>
}

// ─── Token usage ─────────────────────────────────────────────────────────────

export interface TokenUsageEntry {
  inputTokens: number
  outputTokens: number
  cacheReadInputTokens: number
  cacheWriteInputTokens: number
  modelId: string
  modelProvider: string
}

// ─── Conversation ─────────────────────────────────────────────────────────────

export interface ConversationMessage {
  role: MessageRole
  content: string
}

// ─── Session ──────────────────────────────────────────────────────────────────

export interface SessionConfig {
  hat: string
  responsibilities: string[]
  data_to_be_collected: string[]
  data_to_be_calculated: string[]
  notes: string[]
  initial_message?: string
}

export interface SessionRecord {
  session_id: string
  user_id: string
  hat: string
  session_config: SessionConfig
  messages: ConversationMessage[]
  data_collected: DataCollected
  aim_status: AimStatus
  is_expired: boolean
  updated_at: string
  token_usage: TokenUsageEntry[]
  total_input_tokens: number
  total_output_tokens: number
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export interface JwtPayload {
  userId: string
  subscriptionStatus: SubscriptionStatus
  isActive: boolean
}

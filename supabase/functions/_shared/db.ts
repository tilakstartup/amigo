/**
 * Database operations for the strands-agent edge function.
 * All functions accept a Supabase client instance; no Strands SDK dependency.
 */

import type { SupabaseClient } from '@supabase/supabase-js'
import type { SessionRecord, DataCollected, ConversationMessage, AimStatus, GoalType, ActivityLevel, Gender } from './types.ts'

// ─── Supporting types ─────────────────────────────────────────────────────────

export interface ProfileRow {
  id: string
  display_name?: string
  age?: number
  weight_kg?: number
  height_cm?: number
  gender?: Gender
  activity_level?: ActivityLevel
  onboarding_completed?: boolean
  email?: string
}

export interface OnboardingStatus {
  completed: boolean
  completion_percentage: number
  completed_fields: string[]
  missing_fields: string[]
}

export interface SaveGoalParams {
  goal_type: GoalType
  current_weight: number
  target_weight: number
  target_date: string
  current_height?: number
  activity_level?: string
  calculated_bmr?: number
  calculated_tdee?: number
  calculated_daily_calories?: number
  user_daily_calories?: number
  is_realistic?: boolean
  validation_reason?: string
  user_overridden?: boolean
}

// ─── Profile operations ───────────────────────────────────────────────────────

const ONBOARDING_FIELDS = ['display_name', 'age', 'weight_kg', 'height_cm', 'gender', 'activity_level']

export async function getProfile(client: SupabaseClient, userId: string): Promise<ProfileRow | null> {
  const { data, error } = await client
    .from('users_profiles')
    .select('id, display_name, age, weight_kg, height_cm, gender, activity_level, onboarding_completed, email')
    .eq('id', userId)
    .maybeSingle()
  if (error) throw new Error(`getProfile failed: ${error.message}`)
  return data as ProfileRow | null
}

export async function saveOnboardingData(
  client: SupabaseClient,
  userId: string,
  fields: Partial<ProfileRow>,
): Promise<void> {
  const { error } = await client
    .from('users_profiles')
    .upsert({ id: userId, ...fields }, { onConflict: 'id' })
  if (error) throw new Error(`saveOnboardingData failed: ${error.message}`)
}

export async function getOnboardingStatus(
  client: SupabaseClient,
  userId: string,
): Promise<OnboardingStatus> {
  const profile = await getProfile(client, userId)
  if (!profile) {
    return {
      completed: false,
      completion_percentage: 0,
      completed_fields: [],
      missing_fields: [...ONBOARDING_FIELDS],
    }
  }
  const completed_fields = ONBOARDING_FIELDS.filter(
    (f) => profile[f as keyof ProfileRow] != null,
  )
  const missing_fields = ONBOARDING_FIELDS.filter(
    (f) => profile[f as keyof ProfileRow] == null,
  )
  const completion_percentage = Math.round((completed_fields.length / ONBOARDING_FIELDS.length) * 100)
  return {
    completed: profile.onboarding_completed ?? false,
    completion_percentage,
    completed_fields,
    missing_fields,
  }
}

// ─── Goal operations ──────────────────────────────────────────────────────────

export async function saveGoal(
  serviceClient: SupabaseClient,
  userId: string,
  params: SaveGoalParams,
): Promise<void> {
  const { error } = await serviceClient
    .from('ai_context_goals')
    .upsert(
      { user_id: userId, ...params, updated_at: new Date().toISOString() },
      { onConflict: 'user_id' },
    )
  if (error) throw new Error(`saveGoal failed: ${error.message}`)
}

// ─── Session operations ───────────────────────────────────────────────────────

export async function findResumableSession(
  client: SupabaseClient,
  userId: string,
  hat: string,
): Promise<SessionRecord | null> {
  const { data, error } = await client
    .from('conversation_sessions')
    .select('*')
    .eq('user_id', userId)
    .eq('hat', hat)
    .eq('is_expired', false)
    .neq('aim_status', 'completed')
    .order('updated_at', { ascending: false })
    .limit(1)
    .maybeSingle()
  if (error) throw new Error(`findResumableSession failed: ${error.message}`)
  return data as SessionRecord | null
}

export async function createSession(
  client: SupabaseClient,
  record: Omit<SessionRecord, 'updated_at'>,
): Promise<void> {
  const { error } = await client.from('conversation_sessions').insert(record)
  if (error) throw new Error(`createSession failed: ${error.message}`)
}

export async function getSession(
  client: SupabaseClient,
  sessionId: string,
  userId: string,
): Promise<SessionRecord | null> {
  const { data, error } = await client
    .from('conversation_sessions')
    .select('*')
    .eq('session_id', sessionId)
    .eq('user_id', userId)
    .maybeSingle()
  if (error) throw new Error(`getSession failed: ${error.message}`)
  return data as SessionRecord | null
}

export async function upsertSession(
  client: SupabaseClient,
  record: Partial<SessionRecord> & { session_id: string },
): Promise<void> {
  const { session_id, ...fields } = record
  const { error } = await client
    .from('conversation_sessions')
    .update({ ...fields, updated_at: new Date().toISOString() })
    .eq('session_id', session_id)
  if (error) throw new Error(`upsertSession failed: ${error.message}`)
}
export async function expireSession(client: SupabaseClient, sessionId: string): Promise<void> {
  const { error } = await client
    .from('conversation_sessions')
    .update({ is_expired: true })
    .eq('session_id', sessionId)
  if (error) throw new Error(`expireSession failed: ${error.message}`)
}

export async function expireSessionsForUser(
  client: SupabaseClient,
  userId: string,
  hat: string,
): Promise<void> {
  const { error } = await client
    .from('conversation_sessions')
    .update({ is_expired: true })
    .eq('user_id', userId)
    .eq('hat', hat)
    .eq('is_expired', false)
  if (error) throw new Error(`expireSessionsForUser failed: ${error.message}`)
}

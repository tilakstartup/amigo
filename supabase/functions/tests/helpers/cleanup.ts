/**
 * Test helper — delete all conversation_sessions rows for a given user.
 * Uses the service-role client to bypass RLS.
 */
import { createClient, SupabaseClient } from '@supabase/supabase-js'

export function makeServiceClient(): SupabaseClient {
  const url = Deno.env.get('SUPABASE_URL')
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !serviceKey) {
    throw new Error('Missing env vars: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY')
  }
  return createClient(url, serviceKey)
}

export async function deleteTestSessions(
  serviceClient: SupabaseClient,
  userId: string,
): Promise<void> {
  const { error } = await serviceClient
    .from('conversation_sessions')
    .delete()
    .eq('user_id', userId)

  if (error) {
    console.warn(`[cleanup] deleteTestSessions failed for user ${userId}:`, error.message)
  }
}

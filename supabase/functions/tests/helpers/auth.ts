/**
 * Test helper — sign in with test credentials and return a JWT string.
 */
import { createClient } from '@supabase/supabase-js'

export async function signIn(): Promise<string> {
  const url = Deno.env.get('SUPABASE_URL')
  const anonKey = Deno.env.get('SUPABASE_ANON_KEY')
  const email = Deno.env.get('TEST_USER_EMAIL') ?? Deno.env.get('SUPABASE_TEST_USER_EMAIL')
  const password = Deno.env.get('TEST_USER_PASSWORD') ?? Deno.env.get('SUPABASE_TEST_USER_PASSWORD')

  if (!url || !anonKey || !email || !password) {
    throw new Error(
      'Missing env vars: SUPABASE_URL, SUPABASE_ANON_KEY, TEST_USER_EMAIL, TEST_USER_PASSWORD',
    )
  }

  const client = createClient(url, anonKey)
  const { data, error } = await client.auth.signInWithPassword({ email, password })

  if (error || !data.session?.access_token) {
    throw new Error(`signIn failed: ${error?.message ?? 'no access_token'}`)
  }

  return data.session.access_token
}

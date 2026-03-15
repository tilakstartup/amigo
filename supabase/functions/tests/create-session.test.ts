/**
 * Integration tests for POST /create-session
 */
import { assertEquals, assertExists } from 'https://deno.land/std@0.224.0/assert/mod.ts'
import { createClient } from '@supabase/supabase-js'
import { signIn } from './helpers/auth.ts'
import { deleteTestSessions, makeServiceClient } from './helpers/cleanup.ts'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const EDGE_URL = `${SUPABASE_URL}/functions/v1/strands-agent`

const SESSION_CONFIG = {
  hat: 'goal_setting',
  responsibilities: [],
  data_to_be_collected: [],
  data_to_be_calculated: [],
  notes: [],
}

async function createSession(token: string, hat: string, extraHeaders: Record<string, string> = {}) {
  return fetch(`${EDGE_URL}/create-session`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...extraHeaders,
    },
    body: JSON.stringify({ hat, sessionConfig: SESSION_CONFIG }),
  })
}

// Decode userId from JWT
function getUserId(token: string): string {
  const payload = JSON.parse(atob(token.split('.')[1]))
  return payload.sub as string
}

Deno.test({
  name: 'create-session: fresh pair → isResumed false, new sessionId, empty messages',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const res = await createSession(token, 'goal_setting')
      assertEquals(res.status, 200)

      const body = await res.json()
      assertExists(body.sessionId)
      assertEquals(body.isResumed, false)
      assertEquals(body.messages, [])
      assertEquals(body.aim_status, 'not_set')
      assertEquals(Array.isArray(body.data_collected), true)
      assertEquals(body.data_collected.length, 0)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'create-session: second call for same pair → isResumed true, same sessionId',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const res1 = await createSession(token, 'goal_setting')
      assertEquals(res1.status, 200)
      const body1 = await res1.json()
      const firstSessionId = body1.sessionId

      const res2 = await createSession(token, 'goal_setting')
      assertEquals(res2.status, 200)
      const body2 = await res2.json()

      assertEquals(body2.isResumed, true)
      assertEquals(body2.sessionId, firstSessionId)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'create-session: missing hat → 400',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()

    const res = await fetch(`${EDGE_URL}/create-session`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({}),
    })
    assertEquals(res.status, 400)
    const body = await res.json()
    assertEquals(body.error, 'hat is required')
  },
})

Deno.test('create-session: invalid JWT → 401', async () => {
  const res = await fetch(`${EDGE_URL}/create-session`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer invalid.jwt.token',
    },
    body: JSON.stringify({ hat: 'goal_setting', sessionConfig: SESSION_CONFIG }),
  })
  await res.body?.cancel()
  assertEquals(res.status, 401)
})

Deno.test('create-session: no auth on non-onboarding hat → 401', async () => {
  const res = await fetch(`${EDGE_URL}/create-session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ hat: 'goal_setting', sessionConfig: SESSION_CONFIG }),
  })
  await res.body?.cancel()
  assertEquals(res.status, 401)
})

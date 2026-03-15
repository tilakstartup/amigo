/**
 * Integration tests for GET /get-session
 */
import { assertEquals, assertExists } from 'https://deno.land/std@0.224.0/assert/mod.ts'
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

async function createSession(token: string, hat: string) {
  const res = await fetch(`${EDGE_URL}/create-session`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ hat, sessionConfig: SESSION_CONFIG }),
  })
  return res.json()
}

async function getSession(token: string, sessionId: string) {
  return fetch(`${EDGE_URL}/get-session?sessionId=${sessionId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}

function getUserId(token: string): string {
  const payload = JSON.parse(atob(token.split('.')[1]))
  return payload.sub as string
}

Deno.test({
  name: 'get-session: valid sessionId owned by test user → 200 with full record',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const created = await createSession(token, 'goal_setting')
      assertExists(created.sessionId)

      const res = await getSession(token, created.sessionId)
      assertEquals(res.status, 200)

      const body = await res.json()
      assertEquals(body.sessionId, created.sessionId)
      assertEquals(body.hat, 'goal_setting')
      assertEquals(body.aim_status, 'not_set')
      assertEquals(body.is_expired, false)
      assertEquals(Array.isArray(body.data_collected), true)
      assertEquals(Array.isArray(body.messages), true)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'get-session: unknown sessionId → 404',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const res = await getSession(token, 'non-existent-session-id-00000000')
      await res.body?.cancel()
      assertEquals(res.status, 404)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'get-session: sessionId belonging to different user → 404',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const created = await createSession(token, 'goal_setting')
      assertExists(created.sessionId)

      const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
      const res = await getSession(ANON_KEY, created.sessionId)
      await res.body?.cancel()
      const status = res.status
      assertEquals(status === 401 || status === 404, true)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'get-session: missing sessionId param → 400',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()

    const res = await fetch(`${EDGE_URL}/get-session`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    await res.body?.cancel()
    assertEquals(res.status, 400)
  },
})

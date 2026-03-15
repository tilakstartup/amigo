/**
 * Integration tests for POST /send-message
 */
import { assertEquals, assertExists } from 'https://deno.land/std@0.224.0/assert/mod.ts'
import { signIn } from './helpers/auth.ts'
import { deleteTestSessions, makeServiceClient } from './helpers/cleanup.ts'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const EDGE_URL = `${SUPABASE_URL}/functions/v1/strands-agent`

const BASIC_SESSION_CONFIG = {
  hat: 'goal_setting',
  responsibilities: ['Help the user set a health goal'],
  data_to_be_collected: ['current_weight'],
  data_to_be_calculated: [],
  notes: [],
}

function getUserId(token: string): string {
  return JSON.parse(atob(token.split('.')[1])).sub as string
}

async function doCreateSession(token: string, sessionConfig = BASIC_SESSION_CONFIG) {
  const res = await fetch(`${EDGE_URL}/create-session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ hat: sessionConfig.hat, sessionConfig }),
  })
  return res.json()
}

async function doSendMessage(token: string, sessionId: string, message: string) {
  return fetch(`${EDGE_URL}/send-message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ sessionId, message }),
  })
}

// ─── Tests ────────────────────────────────────────────────────────────────────

Deno.test({
  name: 'send-message: missing sessionId → 400',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const res = await fetch(`${EDGE_URL}/send-message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ message: 'hello' }),
    })
    assertEquals(res.status, 400)
    const body = await res.json()
    assertEquals(body.error, 'sessionId is required')
  },
})

Deno.test({
  name: 'send-message: missing message → 400',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const res = await fetch(`${EDGE_URL}/send-message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ sessionId: 'some-id' }),
    })
    assertEquals(res.status, 400)
    const body = await res.json()
    assertEquals(body.error, 'message is required')
  },
})

Deno.test({
  name: 'send-message: invalid JWT → 401',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const res = await fetch(`${EDGE_URL}/send-message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer bad.jwt.token' },
      body: JSON.stringify({ sessionId: 'x', message: 'hi' }),
    })
    await res.body?.cancel()
    assertEquals(res.status, 401)
  },
})

Deno.test({
  name: 'send-message: expired/unknown sessionId → 400',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const res = await doSendMessage(token, 'non-existent-session-00000000', 'hello')
    assertEquals(res.status, 400)
    const body = await res.json()
    assertEquals(body.error, 'Session expired or not found')
  },
})

Deno.test({
  name: 'send-message: full round-trip — AgentResponse shape is valid',
  sanitizeOps: false,
  sanitizeResources: false,
  // LLM call — allow up to 60s
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const session = await doCreateSession(token)
      assertExists(session.sessionId)

      const res = await doSendMessage(token, session.sessionId, 'Hi, I want to lose some weight')
      assertEquals(res.status, 200)

      const body = await res.json()

      // Top-level shape
      assertExists(body.completion)
      assertEquals(body.error, null)
      assertExists(body.timestamp)
      assertEquals(Array.isArray(body.data_collected), true)

      // completion shape
      const c = body.completion
      assertExists(c.status_of_aim)
      assertExists(c.ui)
      assertExists(c.ui.render)
      assertExists(c.ui.render.type)
      assertExists(c.ui.render.text)
      assertEquals(typeof c.ui.render.text, 'string')
      assertEquals(c.ui.render.text.length > 0, true)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'send-message: data_collected accumulates across two turns',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const session = await doCreateSession(token)
      assertExists(session.sessionId)

      // First turn
      const res1 = await doSendMessage(token, session.sessionId, 'Hi, I want to lose weight')
      assertEquals(res1.status, 200)
      const body1 = await res1.json()
      assertEquals(Array.isArray(body1.data_collected), true)

      // Second turn — data_collected should be an array (may grow)
      const res2 = await doSendMessage(token, session.sessionId, 'My current weight is 80kg')
      assertEquals(res2.status, 200)
      const body2 = await res2.json()
      assertEquals(Array.isArray(body2.data_collected), true)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

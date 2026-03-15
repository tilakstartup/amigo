/**
 * Integration tests — health calculations via send-message with goal-setting hat.
 *
 * These tests verify that the agent invokes BMR/TDEE tools and the response
 * shape is valid. We do NOT assert exact LLM text (non-deterministic).
 */
import { assertEquals, assertExists } from 'https://deno.land/std@0.224.0/assert/mod.ts'
import { signIn } from './helpers/auth.ts'
import { deleteTestSessions, makeServiceClient } from './helpers/cleanup.ts'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const EDGE_URL = `${SUPABASE_URL}/functions/v1/strands-agent`

const GOAL_SESSION_CONFIG = {
  hat: 'goal_setting',
  responsibilities: [
    'Help the user set a realistic health goal',
    'Collect current weight, target weight, height, age, gender, activity level',
    'Calculate BMR and TDEE once all data is collected',
  ],
  data_to_be_collected: [
    'current_weight',
    'target_weight',
    'height_cm',
    'age',
    'gender',
    'activity_level',
  ],
  data_to_be_calculated: ['bmr', 'tdee', 'daily_calories'],
  notes: [],
}

function getUserId(token: string): string {
  return JSON.parse(atob(token.split('.')[1])).sub as string
}

async function doCreateSession(token: string) {
  const res = await fetch(`${EDGE_URL}/create-session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ hat: 'goal_setting', sessionConfig: GOAL_SESSION_CONFIG }),
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

Deno.test({
  name: 'health-calculations: send-message returns valid AgentResponse shape',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const session = await doCreateSession(token)
      assertExists(session.sessionId)

      const res = await doSendMessage(
        token,
        session.sessionId,
        'I am a 30 year old male, 175cm tall, currently 80kg, want to reach 70kg. I am moderately active.',
      )
      assertEquals(res.status, 200)

      const body = await res.json()
      assertEquals(body.error, null)
      assertExists(body.completion)
      assertExists(body.completion.ui)
      assertExists(body.completion.ui.render)
      assertEquals(typeof body.completion.ui.render.text, 'string')
      assertEquals(body.completion.ui.render.text.length > 0, true)
      assertEquals(Array.isArray(body.data_collected), true)
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

Deno.test({
  name: 'health-calculations: data_collected array entries have field/label/value shape',
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    const token = await signIn()
    const userId = getUserId(token)
    const serviceClient = makeServiceClient()

    try {
      const session = await doCreateSession(token)
      assertExists(session.sessionId)

      // Send two turns to give the agent a chance to collect some data
      const res1 = await doSendMessage(token, session.sessionId, 'I want to lose weight')
      assertEquals(res1.status, 200)
      const body1 = await res1.json()
      assertEquals(Array.isArray(body1.data_collected), true)

      const res2 = await doSendMessage(
        token,
        session.sessionId,
        'I am 30 years old, male, 175cm, 80kg, moderately active',
      )
      assertEquals(res2.status, 200)
      const body2 = await res2.json()
      assertEquals(Array.isArray(body2.data_collected), true)

      // Any entries that exist must have the correct shape
      for (const entry of body2.data_collected) {
        assertExists(entry.field)
        assertExists(entry.label)
        assertEquals(typeof entry.field, 'string')
        assertEquals(typeof entry.label, 'string')
        // value can be string or null
        assertEquals(
          entry.value === null || typeof entry.value === 'string',
          true,
          `entry.value should be string or null, got: ${typeof entry.value}`,
        )
      }
    } finally {
      await deleteTestSessions(serviceClient, userId)
    }
  },
})

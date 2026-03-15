/**
 * Property-based tests for session helpers.
 *
 * Validates: Requirements 16.7
 */

import * as fc from 'npm:fast-check'
import { isSessionExpired } from '../_shared/session.ts'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeSession(updatedAtMs: number, messageCount: number) {
  return {
    updated_at: new Date(updatedAtMs).toISOString(),
    messages: Array.from({ length: messageCount }, (_, i) => ({
      role: (i % 2 === 0 ? 'user' : 'assistant') as 'user' | 'assistant',
      content: `msg ${i}`,
    })),
  }
}

// ─── Property 9 ───────────────────────────────────────────────────────────────

/**
 * Property 9: session with updated_at older than expiryHours is always expired.
 * Validates: Requirements 16.7a
 */
Deno.test('Property 9: session older than expiryHours is always expired', () => {
  fc.assert(
    fc.property(
      fc.integer({ min: 1, max: 24 }),   // expiryHours
      fc.integer({ min: 1, max: 72 }),   // extraHours beyond expiry
      fc.integer({ min: 0, max: 5 }),    // messageCount (well below max)
      (expiryHours, extraHours, messageCount) => {
        const ageMs = (expiryHours + extraHours) * 60 * 60 * 1000
        const session = makeSession(Date.now() - ageMs, messageCount)
        return isSessionExpired(session, { sessionExpiryHours: expiryHours, sessionMaxMessages: 20 }) === true
      },
    ),
    { numRuns: 200 },
  )
})

// ─── Property 10 ──────────────────────────────────────────────────────────────

/**
 * Property 10: session with messageCount >= maxMessages is always expired.
 * Validates: Requirements 16.7b
 */
Deno.test('Property 10: session with messageCount >= maxMessages is always expired', () => {
  fc.assert(
    fc.property(
      fc.integer({ min: 1, max: 50 }),   // maxMessages
      fc.integer({ min: 0, max: 20 }),   // extra messages beyond max
      (maxMessages, extra) => {
        const messageCount = maxMessages + extra
        // fresh session (not time-expired)
        const session = makeSession(Date.now() - 1000, messageCount)
        return isSessionExpired(session, { sessionExpiryHours: 24, sessionMaxMessages: maxMessages }) === true
      },
    ),
    { numRuns: 200 },
  )
})

// ─── Property 11 ──────────────────────────────────────────────────────────────

/**
 * Property 11: fresh session with few messages is never expired.
 * Validates: Requirements 16.7
 */
Deno.test('Property 11: fresh session with few messages is never expired', () => {
  fc.assert(
    fc.property(
      fc.integer({ min: 1, max: 24 }),   // expiryHours
      fc.integer({ min: 5, max: 50 }),   // maxMessages
      fc.integer({ min: 1, max: 60 }),   // ageSeconds (well within expiry)
      (expiryHours, maxMessages, ageSeconds) => {
        const messageCount = Math.max(0, maxMessages - 1)
        const session = makeSession(Date.now() - ageSeconds * 1000, messageCount)
        return isSessionExpired(session, { sessionExpiryHours: expiryHours, sessionMaxMessages: maxMessages }) === false
      },
    ),
    { numRuns: 200 },
  )
})

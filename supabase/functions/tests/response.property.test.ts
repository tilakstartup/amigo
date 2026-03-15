/**
 * Property-based tests for response builder utilities.
 *
 * Validates: Requirements 6.1, 6.2
 */

import * as fc from 'npm:fast-check'
import { validateAgentJson } from '../_shared/response.ts'
import type { AgentCompletion, AimStatus, RenderType, InputType } from '../_shared/types.ts'

// ─── Arbitraries ──────────────────────────────────────────────────────────────

const aimStatusArb = fc.constantFrom<AimStatus>('not_set', 'in_progress', 'completed')
const renderTypeArb = fc.constantFrom<RenderType>('info', 'message', 'message_with_summary')
const inputTypeArb = fc.constantFrom<InputType>('text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown')

const nonEmptyStringArb = fc.string({ minLength: 1, maxLength: 100 }).filter(
  (s) => s.trim().length > 0,
)

const previousFieldCollectedArb = fc.oneof(
  fc.constant(null),
  fc.record({
    field: nonEmptyStringArb,
    label: nonEmptyStringArb,
    value: fc.oneof(fc.constant(null), fc.string({ maxLength: 100 })),
  }),
)

// Build a valid AgentCompletion arbitrary.
// For info renders, input is null; for other render types, input has a valid type.
const agentCompletionArb: fc.Arbitrary<AgentCompletion> = fc
  .tuple(aimStatusArb, renderTypeArb, nonEmptyStringArb, previousFieldCollectedArb, inputTypeArb)
  .map(([status_of_aim, renderType, text, previous_field_collected, inputType]) => {
    const input = renderType === 'info' ? null : { type: inputType }
    return {
      status_of_aim,
      ui: {
        render: {
          type: renderType,
          text,
        },
      },
      input,
      previous_field_collected,
    } as AgentCompletion
  })

// ─── Property 7 ───────────────────────────────────────────────────────────────

/**
 * Property 7: Any valid AgentCompletion survives JSON.stringify → validateAgentJson round-trip.
 * Validates: Requirements 6.1
 */
Deno.test('Property 7: valid AgentCompletion survives JSON.stringify → validateAgentJson round-trip', () => {
  fc.assert(
    fc.property(agentCompletionArb, (completion: AgentCompletion) => {
      const result = validateAgentJson(JSON.stringify(completion))
      return result.valid === true
    }),
    { numRuns: 200 },
  )
})

// ─── Property 8 ───────────────────────────────────────────────────────────────

/**
 * Property 8: validateAgentJson rejects any object missing a required field.
 * Validates: Requirements 6.2
 */
Deno.test('Property 8: validateAgentJson rejects objects missing required fields', () => {
  const requiredFields = ['status_of_aim', 'ui', 'previous_field_collected'] as const

  fc.assert(
    fc.property(
      agentCompletionArb,
      fc.constantFrom(...requiredFields),
      (completion: AgentCompletion, fieldToDelete: typeof requiredFields[number]) => {
        const obj = JSON.parse(JSON.stringify(completion)) as Record<string, unknown>
        delete obj[fieldToDelete]
        const result = validateAgentJson(JSON.stringify(obj))
        return result.valid === false
      },
    ),
    { numRuns: 200 },
  )
})

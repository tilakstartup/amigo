/**
 * Property-based tests for accumulateDataCollected.
 *
 * Validates: Requirements 5.1
 */

import * as fc from 'npm:fast-check'
import { accumulateDataCollected } from '../_shared/accumulate.ts'
import type { DataCollected } from '../_shared/types.ts'

// Arbitrary for a valid field name (non-empty alphanumeric/underscore string)
const fieldNameArb = fc.stringMatching(/^[a-z][a-z0-9_]{0,19}$/)

// Arbitrary for a non-null, non-empty, non-"null" string value
const nonNullValueArb = fc.string({ minLength: 1, maxLength: 50 }).filter(
  (s) => s !== 'null' && s !== '',
)

// Arbitrary for a DataCollected with 0–5 random string fields
const dataCollectedArb: fc.Arbitrary<DataCollected> = fc
  .array(fc.tuple(fieldNameArb, nonNullValueArb), { minLength: 0, maxLength: 5 })
  .map((entries) => Object.fromEntries(entries))

/**
 * Property 4: accumulating the same non-null value twice is idempotent.
 * Validates: Requirements 5.1
 */
Deno.test('Property 4: double accumulation of same non-null value is idempotent', () => {
  fc.assert(
    fc.property(
      dataCollectedArb,
      fieldNameArb,
      nonNullValueArb,
      (existing: DataCollected, field: string, value: string) => {
        const prev = { field, label: field, value }
        const once = accumulateDataCollected(existing, prev)
        const twice = accumulateDataCollected(once, prev)
        return JSON.stringify(twice) === JSON.stringify(once)
      },
    ),
    { numRuns: 200 },
  )
})

/**
 * Property 5: null value never overwrites an existing non-null value.
 * Validates: Requirements 5.1
 */
Deno.test('Property 5: null value never overwrites an existing non-null value', () => {
  fc.assert(
    fc.property(
      // existing must have at least one field
      fc
        .array(fc.tuple(fieldNameArb, nonNullValueArb), { minLength: 1, maxLength: 5 })
        .map((entries) => Object.fromEntries(entries) as DataCollected),
      (existing: DataCollected) => {
        const fields = Object.keys(existing)
        // pick the first field deterministically
        const field = fields[0]
        const originalValue = existing[field]

        const result = accumulateDataCollected(existing, { field, label: field, value: null })
        return result[field] === originalValue
      },
    ),
    { numRuns: 200 },
  )
})

/**
 * Property 6: all pre-existing fields are preserved when a new field is added.
 * Validates: Requirements 5.1
 */
Deno.test('Property 6: all pre-existing fields are preserved when a new field is added', () => {
  fc.assert(
    fc.property(
      dataCollectedArb,
      fieldNameArb,
      nonNullValueArb,
      (existing: DataCollected, newField: string, newValue: string) => {
        // Ensure newField is not already in existing
        fc.pre(!Object.prototype.hasOwnProperty.call(existing, newField))

        const result = accumulateDataCollected(existing, {
          field: newField,
          label: newField,
          value: newValue,
        })

        // All original fields must still be present with original values
        for (const [key, val] of Object.entries(existing)) {
          if (result[key] !== val) return false
        }
        return true
      },
    ),
    { numRuns: 200 },
  )
})

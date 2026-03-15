import type { DataCollected, DataCollectedEntry, AgentCompletion } from './types.ts'

/**
 * Normalize a value: treat empty string and "null" string as actual null.
 */
function normalize(value: string | null | undefined): string | null {
  if (value === null || value === undefined || value === '' || value === 'null') {
    return null
  }
  return value
}

/**
 * Accumulate new_data_collected entries into the existing DataCollected array.
 *
 * Rules:
 * - non-null value → overwrite (or add) entry
 * - null value + field absent → add entry with null value
 * - null value + field present → preserve existing (no-op)
 */
export function accumulateDataCollected(
  existing: DataCollected,
  newEntries: AgentCompletion['new_data_collected'],
): DataCollected {
  if (!newEntries || newEntries.length === 0) {
    return existing
  }

  let result = [...existing]

  for (const prev of newEntries) {
    const { field, label, value } = prev
    const normalized = normalize(value)
    const existingIndex = result.findIndex((e) => e.field === field)

    // null value + field already present → no-op
    if (normalized === null && existingIndex !== -1) {
      continue
    }

    const entry: DataCollectedEntry = { field, label, value: normalized }

    if (existingIndex !== -1) {
      result[existingIndex] = entry
    } else {
      result = [...result, entry]
    }
  }

  return result
}

/**
 * Convert legacy flat-dict format (from old DB rows) to DataCollectedEntry[].
 * Used when reading sessions created before the schema change.
 */
export function normalizeLegacyDataCollected(raw: unknown): DataCollected {
  if (Array.isArray(raw)) {
    return raw as DataCollected
  }
  if (raw && typeof raw === 'object') {
    return Object.entries(raw as Record<string, string | null>).map(([field, value]) => ({
      field,
      label: field.split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
      value,
    }))
  }
  return []
}

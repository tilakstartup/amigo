# Task 6 Verification Checklist — `response.ts`

## Implementation Checks

- [x] `buildAgentResponse` constructs a well-formed `AgentResponse` with ISO 8601 `timestamp`
- [x] `buildErrorResponse` constructs an `AgentResponse` with `completion: null` and populated `error`
- [x] `buildFallbackCompletion` returns an `AgentCompletion` with `status_of_aim: 'in_progress'` and `render.type: 'message'`
- [x] `validateAgentJson` strips markdown fences (` ```json ` / ` ``` `)
- [x] `validateAgentJson` strips XML wrappers (`<response>...</response>`)
- [x] `validateAgentJson` sanitizes literal control characters inside JSON string values
- [x] `validateAgentJson` rejects non-object JSON (arrays, primitives)
- [x] `validateAgentJson` rejects objects missing `status_of_aim`, `ui`, or `previous_field_collected`
- [x] `validateAgentJson` rejects invalid `status_of_aim` enum values
- [x] `validateAgentJson` rejects invalid `ui.render.type` enum values
- [x] `validateAgentJson` rejects empty `ui.render.text`
- [x] `validateAgentJson` allows `input: null` when `render.type === 'info'`
- [x] `validateAgentJson` rejects invalid `input.type` enum values
- [x] `validateAgentJson` validates `previous_field_collected` shape when non-null
- [x] `toDataCollectedArray` converts `DataCollected` object to `DataCollectedEntry[]` with auto-generated labels

## Property Tests

- [x] Property 7: valid `AgentCompletion` survives `JSON.stringify` → `validateAgentJson` round-trip (200 runs)
- [x] Property 8: `validateAgentJson` rejects any object missing a required field (200 runs)

## Type Safety

- [x] `deno check` passes with zero errors on `response.ts`

## Notes

- No mobile build required — pure Deno/TypeScript module
- No Supabase deployment required at this stage

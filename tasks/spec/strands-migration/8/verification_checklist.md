# Task 8 Verification Checklist — `session.ts`

## Implementation Checks

- [x] `getSessionConfig` reads `SESSION_EXPIRY_HOURS`, `SESSION_MAX_MESSAGES`, `SESSION_RESUME_MESSAGES` from `Deno.env`
- [x] `getSessionConfig` falls back to defaults: 1h, 20 messages, 20 resume messages
- [x] `isSessionExpired` returns `true` when `updated_at` is older than `expiryHours`
- [x] `isSessionExpired` returns `true` when `messages.length >= maxMessages`
- [x] `isSessionExpired` returns `false` for a fresh session with few messages
- [x] `trimMessages` returns the last N messages when count exceeds `maxCount`
- [x] `trimMessages` returns the full array when count is within `maxCount`
- [x] `buildSystemPrompt` includes hat, responsibilities, data_to_be_collected, data_to_be_calculated, notes
- [x] `buildSystemPrompt` includes the JSON response format schema
- [x] `buildSystemPrompt` notes that `input` may be `null` for `render.type === 'info'`

## Property Tests

- [x] Property 9: session older than `expiryHours` is always expired (200 runs)
- [x] Property 10: session with `messageCount >= maxMessages` is always expired (200 runs)
- [x] Property 11: fresh session with few messages is never expired (200 runs)

## Manual Sanity Checks

- [x] `isSessionExpired` with 2h-old session and 1h expiry → `true`
- [x] `isSessionExpired` with fresh session → `false`

## Type Safety

- [x] `deno check` passes with zero errors on `session.ts`

## Notes

- No mobile build required — pure Deno/TypeScript module
- No Supabase deployment required at this stage

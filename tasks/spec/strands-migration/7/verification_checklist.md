# Task 7 Verification Checklist — `db.ts`

## Implementation Checks

- [x] `getProfile` fetches from `users_profiles` by `id`, returns `null` if not found
- [x] `saveOnboardingData` upserts into `users_profiles` with `onConflict: 'id'`
- [x] `getOnboardingStatus` computes `completion_percentage` and `missing_fields` from profile
- [x] `saveGoal` upserts into `ai_context_goals` using service-role client (bypasses RLS)
- [x] `findResumableSession` queries `WHERE user_id = userId AND hat = hat AND is_expired = false AND aim_status != 'completed'`
- [x] `findResumableSession` does NOT apply time-based expiry (that is deferred to `isSessionExpired`)
- [x] `createSession` inserts a new row into `conversation_sessions`
- [x] `getSession` queries by `session_id` AND `user_id` (enforces ownership)
- [x] `upsertSession` upserts with `onConflict: 'session_id'`
- [x] `expireSession` sets `is_expired = true` for a single session
- [x] `expireSessionsForUser` sets `is_expired = true` for all active sessions for `(user_id, hat)`
- [x] All functions throw typed errors on DB failure

## Live Integration Checks (verified against Supabase)

- [x] `createSession` via service-role key → 201 OK
- [x] `getSession` returns correct row
- [x] `findResumableSession` finds the newly created session
- [x] `expireSession` sets `is_expired = true`
- [x] `expireSessionsForUser` marks all matching sessions expired
- [x] SELECT via anon JWT returns `[]` (RLS scoped to `auth.uid()`)
- [x] INSERT via anon JWT returns 403 (RLS blocks client-side writes)

## Type Safety

- [x] `deno check` passes with zero errors on `db.ts`

## Notes

- No mobile build required — pure Deno/TypeScript module
- Service-role key is required for all write operations at runtime

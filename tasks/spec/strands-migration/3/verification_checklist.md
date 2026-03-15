# Task 3 Verification Checklist — JWT Verification (`auth.ts`)

## Implementation Checks

- [ ] `supabase/functions/_shared/auth.ts` exists
- [ ] `verifyJwt` is exported and has signature `(jwt: string): Promise<JwtPayload | null>`
- [ ] Uses `supabase.auth.getUser(jwt)` to validate the token
- [ ] Extracts `sub` (user ID) from `user.id`
- [ ] Reads `user_subscription.subscription_status` from `app_metadata`
- [ ] Reads `user_subscription.is_active` from `app_metadata`
- [ ] Falls back to `{ subscriptionStatus: 'free', isActive: true }` when claim is absent
- [ ] Returns `null` on expired JWT
- [ ] Returns `null` on invalid/malformed JWT
- [ ] Returns `null` on network failure (caught exception)
- [ ] Returns `null` when `user` is null in the response

## Type Correctness

- [x] `deno check --config supabase/functions/deno.json supabase/functions/_shared/auth.ts` exits 0 with no errors ✅ auto-verified
- [x] Return type matches `JwtPayload` interface from `types.ts` ✅ auto-verified (deno check passes)

## Smoke Test

- [x] Running `verifyJwt` with a valid token returns `{ userId, subscriptionStatus, isActive }` ✅ auto-verified
  - Result: `{"userId":"36b8d29b-8d45-402f-b6e0-e9e0d40b0be5","subscriptionStatus":"free","isActive":true}`
- [ ] Running `verifyJwt` with an expired token returns `null` ⏳ manual (requires an expired token)
- [x] Running `verifyJwt` with a garbage string returns `null` ✅ auto-verified

# Implementation Plan: strands-migration

## Overview

Migrate the Amigo AI agent backend from AWS Lambda + Bedrock Managed Agents to the Strands Agents TypeScript SDK running inside a Supabase Edge Function. The mobile client gains a new `AmigoClient` behind an `AgentClient` interface, switchable via `AppConfig.AI_MODE`.

**Critical ordering constraint**: Backend (Phases 1–3) must be fully implemented and validated before any mobile client work begins (Phase 4).

---

## Getting a Bearer Token

Before running any API verification steps, obtain a JWT from Supabase Auth:

```bash
export SUPABASE_URL="https://<project>.supabase.co"
export SUPABASE_ANON_KEY="<your-anon-key>"
export TEST_EMAIL="[email]"
export TEST_PASSWORD="[password]"

export TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}" \
  | jq -r '.access_token')

echo "TOKEN=$TOKEN"
```

Keep `$TOKEN`, `$SUPABASE_URL`, and `$SUPABASE_ANON_KEY` set in your shell for all subsequent verification steps.

---

## Tasks

- [x] 1. Database migration — create `conversation_sessions` table
  - Create `supabase/migrations/20260315000001_create_conversation_sessions.sql`
  - Define columns: `session_id TEXT PK`, `user_id UUID FK → users_profiles`, `hat TEXT`, `messages JSONB DEFAULT '[]'`, `data_collected JSONB DEFAULT '{}'`, `aim_status TEXT CHECK IN ('not_set','in_progress','completed') DEFAULT 'not_set'`, `is_expired BOOLEAN DEFAULT false`, `updated_at TIMESTAMPTZ DEFAULT now()`
  - Add indexes: `(user_id, hat)`, partial index on `(user_id, hat) WHERE is_expired = false`, `(updated_at)`
  - Add `BEFORE UPDATE` trigger calling `handle_updated_at()`
  - Enable RLS with SELECT / INSERT / UPDATE policies scoped to `auth.uid() = user_id`
  - _Requirements: 16.1, 16.9_

  #### Verification
  ```bash
  # Apply the migration
  supabase db push

  # Confirm the table exists and SELECT RLS works (returns empty array for your user)
  curl -s "$SUPABASE_URL/rest/v1/conversation_sessions?limit=1" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Authorization: Bearer $TOKEN"
  # Expected: 200 with []

  # Confirm direct INSERT is blocked (all writes go through the edge function service-role key)
  curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$SUPABASE_URL/rest/v1/conversation_sessions" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"session_id":"test","hat":"goal_setting","messages":[],"data_collected":{},"aim_status":"not_set","is_expired":false}'
  # Expected: 403 (RLS blocks client-side INSERT — no insert policy exists)
  ```

- [x] 2. Shared module — types and interfaces
  - Create `supabase/functions/_shared/types.ts`
  - Export TypeScript interfaces: `AgentResponse`, `AgentCompletion`, `DataCollected`, `ConversationMessage`, `SessionConfig`, `SessionRecord`, `JwtPayload`
  - Include all enum literals for `status_of_aim`, `ui.render.type`, `input.type`
  - No runtime logic — types only
  - _Requirements: 12.6_

  #### Verification
  ```bash
  # Type-check the file — no output means success
  deno check --config supabase/functions/deno.json supabase/functions/_shared/types.ts
  # Expected: exits 0 with no errors printed
  ```

- [x] 3. Shared module — JWT verification (`auth.ts`)
  - Create `supabase/functions/_shared/auth.ts`
  - Export `verifyJwt(jwt: string): Promise<JwtPayload | null>` using `supabase.auth.getUser(jwt)`
  - Extract `sub` as `userId`, read `user_subscription.subscription_status` and `user_subscription.is_active` from `app_metadata` / custom claims
  - Return `null` on any error (expired, invalid, network failure)
  - Fall back to `{ subscriptionStatus: 'free', isActive: true }` when claim is absent
  - _Requirements: 2.1, 2.2, 2.3, 12.1_

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/_shared/auth.ts
  # Expected: exits 0 with no errors

  # Smoke-test verifyJwt with your real token via a one-liner Deno script
  SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
  deno eval "
    import { verifyJwt } from './supabase/functions/_shared/auth.ts';
    const result = await verifyJwt('$TOKEN');
    console.log(JSON.stringify(result));
  "
  # Expected: { "userId": "<uuid>", "subscriptionStatus": "free", "isActive": true }
  ```

- [x] 4. Shared module — health calculations (`health.ts`)
  - Create `supabase/functions/_shared/health.ts`
  - Export `calculateBmr`, `calculateTdee`, `calculateDailyCalories`, `validateGoal` as pure functions with no I/O
  - Implement Mifflin-St Jeor for BMR (male: base+5, female: base−161)
  - Implement activity multipliers: sedentary=1.2, lightly_active=1.375, moderately_active=1.55, very_active=1.725, extra_active=1.9
  - Implement calorie deficit/surplus: `weightDiff * 7700 / daysUntilTarget`
  - Implement USDA minimums: male 1500 kcal/day, female 1200 kcal/day; return `{ is_valid, message, minimum_calories, suggestions }`
  - Results must be numerically identical (within floating-point tolerance) to the Kotlin `HealthCalculationsActionGroup` implementations
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.6, 7.7, 7.8, 12.8_

  - [x] 4.1 Write property tests for health calculations
    - **Property 1: `calculateBmr` matches Mifflin-St Jeor formula exactly for all valid inputs**
    - **Validates: Requirements 7.1, 7.6**
    - **Property 2: `calculateTdee` equals `calculateBmr × activityMultiplier` for all valid inputs**
    - **Validates: Requirements 7.2, 7.7**
    - **Property 3: `calculateDailyCalories` deficit equals `weightDiff * 7700 / days` for all valid inputs**
    - **Validates: Requirements 7.3, 7.8**
    - Use `npm:fast-check` in Deno; place in `supabase/functions/tests/health-calculations.property.test.ts`

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/_shared/health.ts
  # Expected: exits 0

  deno test --config supabase/functions/deno.json \
    supabase/functions/tests/health-calculations.property.test.ts
  # Expected: all property tests pass (100 runs each by default)

  # Quick sanity check — 70kg male, 175cm, 30yo, sedentary
  # BMR = (10*70) + (6.25*175) - (5*30) + 5 = 1698.75
  # TDEE = 1698.75 * 1.2 = 2038.5
  deno eval "
    import { calculateBmr, calculateTdee } from './supabase/functions/_shared/health.ts';
    console.log('BMR:', calculateBmr({ weight_kg: 70, height_cm: 175, age: 30, gender: 'male' }));
    console.log('TDEE:', calculateTdee({ weight_kg: 70, height_cm: 175, age: 30, gender: 'male', activity_level: 'sedentary' }));
  "
  # Expected: BMR: 1698.75   TDEE: 2038.5
  ```

- [x] 5. Shared module — `data_collected` accumulation (`accumulate.ts`)
  - Create `supabase/functions/_shared/accumulate.ts`
  - Export `accumulateDataCollected(existing: DataCollected, prev: AgentCompletion['previous_field_collected']): DataCollected`
  - Rule 1: non-null value → overwrite (or add) field
  - Rule 2: null value + field absent → store null
  - Rule 3: null value + field present → preserve existing (no-op)
  - Treat empty string `""` and string `"null"` as null
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 12.4_

  - [x] 5.1 Write property tests for `accumulateDataCollected`
    - **Property 4: accumulating the same field twice with the same value is idempotent**
    - **Validates: Requirements 10.6**
    - **Property 5: null value never overwrites an existing non-null value**
    - **Validates: Requirements 10.4**
    - **Property 6: all pre-existing fields are preserved when a new field is added**
    - **Validates: Requirements 10.2, 10.3**
    - Place in `supabase/functions/tests/accumulate.property.test.ts`

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/_shared/accumulate.ts
  # Expected: exits 0

  deno test --config supabase/functions/deno.json \
    supabase/functions/tests/accumulate.property.test.ts
  # Expected: all property tests pass

  # Manual logic check
  deno eval "
    import { accumulateDataCollected } from './supabase/functions/_shared/accumulate.ts';
    const existing = { current_weight: '70' };
    // null should NOT overwrite existing value
    const r1 = accumulateDataCollected(existing, { field: 'current_weight', label: 'Weight', value: null });
    console.log('null no-op:', r1.current_weight); // Expected: '70'
    // non-null should overwrite
    const r2 = accumulateDataCollected(existing, { field: 'current_weight', label: 'Weight', value: '75' });
    console.log('overwrite:', r2.current_weight); // Expected: '75'
  "
  ```

- [x] 6. Shared module — response builders and JSON validation (`response.ts`)
  - Create `supabase/functions/_shared/response.ts`
  - Export `buildAgentResponse(params)` and `buildErrorResponse(params)` constructing well-formed `AgentResponse` objects with ISO 8601 `timestamp`
  - Export `validateAgentJson(text: string): ValidationResult` implementing the 9-step validation sequence from design §11
  - Strip markdown fences and XML wrappers; sanitize control characters; validate required fields and enum values
  - Export `buildFallbackCompletion(errorMessage: string): AgentCompletion`
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 12.2, 12.3, 12.5, 4.7_

  - [x] 6.1 Write property tests for `validateAgentJson`
    - **Property 7: any valid `AgentCompletion` object survives JSON.stringify → `validateAgentJson` round-trip**
    - **Validates: Requirements 11.8**
    - **Property 8: `validateAgentJson` rejects any object missing a required field**
    - **Validates: Requirements 11.1**
    - Place in `supabase/functions/tests/response.property.test.ts`

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/_shared/response.ts
  # Expected: exits 0

  deno test --config supabase/functions/deno.json \
    supabase/functions/tests/response.property.test.ts
  # Expected: all property tests pass

  # Confirm a valid completion round-trips cleanly
  deno eval "
    import { validateAgentJson } from './supabase/functions/_shared/response.ts';
    const valid = JSON.stringify({
      status_of_aim: 'in_progress',
      ui: { render: { type: 'message', text: 'Hello' }, tone: 'friendly' },
      input: { type: 'text' },
      previous_field_collected: null
    });
    console.log(JSON.stringify(validateAgentJson(valid)));
  "
  # Expected: { "valid": true, "parsed": { ... } }
  ```

- [x] 7. Shared module — database operations (`db.ts`)
  - Create `supabase/functions/_shared/db.ts`
  - Export profile functions: `getProfile`, `saveOnboardingData`, `getOnboardingStatus`
  - Export goal function: `saveGoal` (accepts service-role client to bypass RLS)
  - Export session functions: `findResumableSession`, `createSession`, `getSession`, `upsertSession`, `expireSession`, `expireSessionsForUser`
  - `findResumableSession` queries `WHERE user_id = userId AND hat = hat AND is_expired = false AND aim_status != 'completed'` — no time check here; time-based expiry is evaluated at send-message time by `isSessionExpired`
  - All functions accept a Supabase client instance; no Strands SDK dependency
  - Throw typed errors on DB failure (callers catch and return tool error results)
  - _Requirements: 6.1, 6.2, 6.3, 8.1, 12.7, 16.1_

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/_shared/db.ts
  # Expected: exits 0

  # Insert a test session row directly via the REST API and read it back
  export SESSION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
  curl -s -X POST "$SUPABASE_URL/rest/v1/conversation_sessions" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "Prefer: return=representation" \
    -d "{\"session_id\":\"$SESSION_ID\",\"hat\":\"goal_setting\",\"messages\":[],\"data_collected\":{},\"aim_status\":\"not_set\",\"is_expired\":false}"
  # Expected: 201 with the inserted row

  curl -s "$SUPABASE_URL/rest/v1/conversation_sessions?session_id=eq.$SESSION_ID" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Authorization: Bearer $TOKEN"
  # Expected: array with one row matching the inserted session_id

  # Clean up
  curl -s -X DELETE "$SUPABASE_URL/rest/v1/conversation_sessions?session_id=eq.$SESSION_ID" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Authorization: Bearer $TOKEN"
  ```

- [x] 8. Shared module — session helpers (`session.ts`)
  - Create `supabase/functions/_shared/session.ts`
  - Export `isSessionExpired(session, { sessionExpiryHours, sessionMaxMessages }): boolean`
  - Export `getSessionConfig(): { expiryHours, maxMessages, resumeMessages }` reading from `Deno.env` with defaults (1h, 20, 20)
  - Export `trimMessages(messages, maxCount): ConversationMessage[]`
  - Export `buildSystemPrompt(sessionConfig: SessionConfig): string` — constructs the system prompt from `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, and `notes`; uses the updated schema where `input` may be `null` when `render.type === 'info'`
  - _Requirements: 16.7, 16.8_

  - [x] 8.1 Write property tests for `isSessionExpired`
    - **Property 9: session with `updated_at` older than `expiryHours` is always expired**
    - **Validates: Requirements 16.7a**
    - **Property 10: session with `messageCount >= maxMessages` is always expired**
    - **Validates: Requirements 16.7b**
    - **Property 11: fresh session with few messages is never expired**
    - **Validates: Requirements 16.7**
    - Place in `supabase/functions/tests/session.property.test.ts`

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/_shared/session.ts
  # Expected: exits 0

  deno test --config supabase/functions/deno.json \
    supabase/functions/tests/session.property.test.ts
  # Expected: all property tests pass

  # Confirm expiry logic manually
  deno eval "
    import { isSessionExpired } from './supabase/functions/_shared/session.ts';
    const old = { updated_at: new Date(Date.now() - 2 * 3600 * 1000).toISOString(), messages: [] };
    console.log('old session expired:', isSessionExpired(old, { sessionExpiryHours: 1, sessionMaxMessages: 20 }));
    // Expected: true
    const fresh = { updated_at: new Date().toISOString(), messages: [] };
    console.log('fresh session expired:', isSessionExpired(fresh, { sessionExpiryHours: 1, sessionMaxMessages: 20 }));
    // Expected: false
  "
  ```

- [x] 9. Checkpoint — shared module complete
  - Create `supabase/functions/deno.json` with import map entries for `npm:fast-check` and `https://esm.sh/@supabase/supabase-js@2` so Deno resolves imports correctly
  - Ensure all shared module files compile without errors (`deno check --config supabase/functions/deno.json supabase/functions/_shared/*.ts`)
  - Ensure all property tests pass (`deno test --config supabase/functions/deno.json supabase/functions/tests/*.property.test.ts`)
  - Ask the user if questions arise before proceeding to the edge function.

  #### Verification
  ```bash
  # Check all shared modules in one pass
  deno check --config supabase/functions/deno.json supabase/functions/_shared/*.ts
  # Expected: exits 0, no errors

  # Run all property tests
  deno test --config supabase/functions/deno.json \
    supabase/functions/tests/*.property.test.ts
  # Expected: all tests pass (green output, 0 failures)
  ```

- [x] 10. Strands edge function — model resolution (`strands-agent/model.ts`)
  - Create `supabase/functions/strands-agent/model.ts`
  - Implement `resolveModel(subscriptionStatus, isActive): { modelProvider, modelId }`
  - Read `PRO_MODEL_PROVIDER` / `PRO_MODEL_ID` for pro+active users; `FREE_MODEL_PROVIDER` / `FREE_MODEL_ID` otherwise
  - Validate provider ∈ `{ 'bedrock', 'openai', 'deepseek' }`; default to `'bedrock'` with warning if absent/unrecognized
  - Fall back to hardcoded defaults per provider when model ID env var is absent: bedrock=`anthropic.claude-3-5-sonnet-20241022-v2:0`, openai=`gpt-4o`, deepseek=`deepseek-chat`
  - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.8, 13.9_

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/strands-agent/model.ts
  # Expected: exits 0

  # Confirm free-tier resolves to bedrock default
  deno eval "
    import { resolveModel } from './supabase/functions/strands-agent/model.ts';
    console.log(JSON.stringify(resolveModel('free', true)));
  "
  # Expected: { "modelProvider": "bedrock", "modelId": "anthropic.claude-3-5-sonnet-20241022-v2:0" }
  ```

- [x] 11. Strands edge function — tool registrations (`strands-agent/tools.ts`)
  - Create `supabase/functions/strands-agent/tools.ts`
  - Register Strands tools as thin wrappers calling `_shared/db.ts` and `_shared/health.ts`
  - Data tools: `get_profile`, `save_onboarding_data`, `get_onboarding_status`
  - Health tools: `calculate_bmr`, `calculate_tdee`, `calculate_daily_calories`, `validate_goal`
  - Goal tool: `save_goal` — validate `userId` present; validate `user_daily_calories` present when `user_overridden=true`; use service-role client
  - Unauthenticated (onboarding hat) data tools return empty profile values rather than errors
  - Catch DB errors from `_shared/db.ts` and return `{ status: "error", message }` tool results
  - _Requirements: 6.4, 6.5, 6.6, 7.5, 7.9, 8.2, 8.3, 8.4, 8.5, 8.6_

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/strands-agent/tools.ts
  # Expected: exits 0, no type errors
  ```

- [x] 12. Strands edge function — main router and endpoint handlers (`strands-agent/index.ts`)
  - Create `supabase/functions/strands-agent/index.ts`
  - Implement URL-path router dispatching to `handleCreateSession`, `handleGetSession`, `handleSendMessage`; return 404 for unknown paths
  - Apply CORS headers to all responses including OPTIONS preflight
  - **`handleCreateSession`**: verify JWT (allow anonymous for `hat=onboarding`); validate `hat` present; call `findResumableSession`; if found return with `isResumed: true` and `toDataCollectedArray(session.data_collected)`; else `expireSessionsForUser` + `createSession`; return `{ sessionId, isResumed, messages, data_collected: [], aim_status }`
  - **`handleGetSession`**: verify JWT; read `sessionId` query param; call `getSession(client, sessionId, userId)`; return 404 if not found or wrong user; call `toDataCollectedArray(session.data_collected)` before returning
  - **`handleSendMessage`**: verify JWT; validate `sessionId` + `message`; load session; check expiry via `isSessionExpired`; build Strands agent via `buildAgent`; invoke with full history; call `validateAgentJson` with one retry using correction prompt; call `accumulateDataCollected`; fire-and-forget `upsertSession`; return `AgentResponse`
  - Anonymous onboarding: assign `userId = "onboarding-anon-{timestamp}"`, `subscriptionStatus = "free"`
  - Return HTTP 401 for invalid JWT (non-onboarding), 400 for missing params or expired session
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1–3.16, 4.1–4.8, 5.1–5.6, 16.2–16.6_

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json supabase/functions/strands-agent/index.ts
  # Expected: exits 0, no type errors
  ```

- [x] 13. Checkpoint — edge function complete
  - Ensure `deno check --config supabase/functions/deno.json supabase/functions/strands-agent/index.ts` passes with no type errors
  - Deploy edge function to Supabase: `supabase functions deploy strands-agent`
  - Ask the user if questions arise before proceeding to integration tests.

  #### Verification
  ```bash
  # Deploy
  supabase functions deploy strands-agent

  export EDGE_URL="$SUPABASE_URL/functions/v1/strands-agent"

  # 1. Unknown path → 404
  curl -s -o /dev/null -w "%{http_code}" "$EDGE_URL/unknown-path" \
    -H "Authorization: Bearer $TOKEN"
  # Expected: 404

  # 2. OPTIONS preflight → 200 with CORS headers
  curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$EDGE_URL/create-session" \
    -H "Origin: http://localhost" \
    -H "Access-Control-Request-Method: POST"
  # Expected: 200

  # 3. Missing hat → 400
  curl -s -X POST "$EDGE_URL/create-session" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{}'
  # Expected: { "error": "hat is required" } with HTTP 400

  # 4. No auth on non-onboarding hat → 401
  curl -s -o /dev/null -w "%{http_code}" -X POST "$EDGE_URL/create-session" \
    -H "Content-Type: application/json" \
    -d '{"hat":"goal_setting","sessionConfig":{}}'
  # Expected: 401
  ```

- [x] 14. Backend integration tests — test helpers
  - Create `supabase/functions/tests/helpers/auth.ts`: `signIn()` signs in with `TEST_USER_EMAIL` / `TEST_USER_PASSWORD` and returns a JWT string
  - Create `supabase/functions/tests/helpers/cleanup.ts`: `deleteTestSessions(serviceClient, userId)` deletes all `conversation_sessions` rows for the test user using the service-role client
  - _Requirements: 17.1, 17.2, 17.3, 17.6_

  #### Verification
  ```bash
  deno check --config supabase/functions/deno.json \
    supabase/functions/tests/helpers/auth.ts \
    supabase/functions/tests/helpers/cleanup.ts
  # Expected: exits 0

  # Confirm signIn() returns a non-empty token
  TEST_USER_EMAIL=$TEST_EMAIL TEST_USER_PASSWORD=$TEST_PASSWORD \
  SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
  deno eval "
    import { signIn } from './supabase/functions/tests/helpers/auth.ts';
    const token = await signIn();
    console.log('token length:', token.length);
  "
  # Expected: token length: <number > 100>
  ```

- [x] 15. Backend integration tests — `create-session` and `get-session`
  - Create `supabase/functions/tests/create-session.test.ts`
  - Test: fresh `(userId, hat)` pair → `isResumed: false`, new `sessionId`, empty `messages`
  - Test: second call for same pair → `isResumed: true`, returns prior `sessionId` and messages
  - Test: prior session is marked `is_expired = true` when a new session is forced
  - Test: missing `hat` → HTTP 400
  - Test: invalid JWT → HTTP 401
  - Create `supabase/functions/tests/get-session.test.ts`
  - Test: valid `sessionId` owned by test user → 200 with full session record
  - Test: unknown `sessionId` → 404
  - Test: `sessionId` belonging to a different user → 404
  - Call `deleteTestSessions` in `afterEach` / `finally`
  - _Requirements: 17.4, 17.6, 17.7, 17.8_

  #### Verification
  ```bash
  export EDGE_URL="$SUPABASE_URL/functions/v1/strands-agent"

  # Run the integration tests
  TEST_USER_EMAIL=$TEST_EMAIL TEST_USER_PASSWORD=$TEST_PASSWORD \
  SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
  SUPABASE_SERVICE_ROLE_KEY=$SUPABASE_SERVICE_ROLE_KEY \
  deno test --config supabase/functions/deno.json --allow-net --allow-env \
    supabase/functions/tests/create-session.test.ts \
    supabase/functions/tests/get-session.test.ts
  # Expected: all tests pass

  # Manual spot-check — create a session
  curl -s -X POST "$EDGE_URL/create-session" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"hat":"goal_setting","sessionConfig":{"hat":"goal_setting","responsibilities":[],"data_to_be_collected":[],"data_to_be_calculated":[],"notes":[]}}' \
    | jq '{sessionId, isResumed, aim_status}'
  # Expected: { "sessionId": "<uuid>", "isResumed": false, "aim_status": "not_set" }

  # Store the session ID and fetch it back
  export SESSION_ID=$(curl -s -X POST "$EDGE_URL/create-session" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"hat":"goal_setting","sessionConfig":{"hat":"goal_setting","responsibilities":[],"data_to_be_collected":[],"data_to_be_calculated":[],"notes":[]}}' \
    | jq -r '.sessionId')

  curl -s "$EDGE_URL/get-session?sessionId=$SESSION_ID" \
    -H "Authorization: Bearer $TOKEN" \
    | jq '{sessionId, hat, aim_status, is_expired}'
  # Expected: { "sessionId": "<uuid>", "hat": "goal_setting", "aim_status": "not_set", "is_expired": false }
  ```

- [x] 16. Backend integration tests — `send-message` and health calculations
  - Create `supabase/functions/tests/send-message.test.ts`
  - Test: full round-trip — create session → send message → verify `AgentResponse` shape matches Requirement 4 contract
  - Test: `data_collected` accumulates correctly across two turns
  - Test: expired session returns HTTP 400 with `"Session expired or not found"`
  - Test: missing `sessionId` or `message` → HTTP 400
  - Create `supabase/functions/tests/health-calculations.test.ts`
  - Test: send-message with goal-setting hat triggers BMR/TDEE tool calls; verify the `data_collected` array in the response contains the expected calculated fields (e.g. `bmr`, `tdee`) — do not assert exact LLM response text (non-deterministic)
  - Call `deleteTestSessions` in `afterEach` / `finally`
  - _Requirements: 17.4, 17.5, 17.6, 17.7, 17.8_

  #### Verification
  ```bash
  export EDGE_URL="$SUPABASE_URL/functions/v1/strands-agent"

  # Run integration tests
  TEST_USER_EMAIL=$TEST_EMAIL TEST_USER_PASSWORD=$TEST_PASSWORD \
  SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
  SUPABASE_SERVICE_ROLE_KEY=$SUPABASE_SERVICE_ROLE_KEY \
  deno test --config supabase/functions/deno.json --allow-net --allow-env \
    supabase/functions/tests/send-message.test.ts \
    supabase/functions/tests/health-calculations.test.ts
  # Expected: all tests pass

  # Manual full round-trip
  export SESSION_ID=$(curl -s -X POST "$EDGE_URL/create-session" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"hat":"goal_setting","sessionConfig":{"hat":"goal_setting","responsibilities":["Help user set a health goal"],"data_to_be_collected":["current_weight"],"data_to_be_calculated":[],"notes":[]}}' \
    | jq -r '.sessionId')

  curl -s -X POST "$EDGE_URL/send-message" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Hi, I want to lose weight\"}" \
    | jq '{completion: .completion.ui.render, data_collected, error}'
  # Expected: completion has render.type and render.text; error is null

  # Missing sessionId → 400
  curl -s -o /dev/null -w "%{http_code}" -X POST "$EDGE_URL/send-message" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"message":"hello"}'
  # Expected: 400
  ```

- [x] 17. Checkpoint — backend fully validated
  - Run full integration test suite: `deno test --config supabase/functions/deno.json supabase/functions/tests/ --allow-net --allow-env`
  - All tests must pass against the deployed Supabase edge function
  - Ask the user if questions arise before proceeding to mobile client work.
  - **Do not begin Phase 4 until this checkpoint passes.**

  #### Verification
  ```bash
  TEST_USER_EMAIL=$TEST_EMAIL TEST_USER_PASSWORD=$TEST_PASSWORD \
  SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
  SUPABASE_SERVICE_ROLE_KEY=$SUPABASE_SERVICE_ROLE_KEY \
  deno test --config supabase/functions/deno.json \
    supabase/functions/tests/ --allow-net --allow-env
  # Expected: all tests pass, 0 failures — safe to proceed to Phase 4
  ```

- [ ] 18. Mobile — `AgentClient` interface and updated `BedrockClient`
  - Create `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/AgentClient.kt`
  - Define interface with property `ownsSessionState: Boolean` and methods: `createSession(hat, sessionConfig): Result<CreateSessionResponse>`, `sendMessage(sessionId, message): Result<BedrockResponse>`, `getSession(sessionId): Result<GetSessionResponse>`, `close()`
  - Define data classes: `CreateSessionResponse`, `GetSessionResponse`, `DataCollectedEntry(field, label, value)`, `ConversationMessage` (as per design §2)
  - `CreateSessionResponse.dataCollected` and `GetSessionResponse.dataCollected` are `List<DataCollectedEntry>` matching the wire-format array
  - Update `BedrockClient` to implement `AgentClient` with `ownsSessionState = false`: `createSession` returns a stub `CreateSessionResponse`; `sendMessage` delegates to existing `invokeAgent`; `getSession` returns a stub
  - _Requirements: 14.1, 14.2, 15.2, 15.4_

  #### Verification
  ```bash
  # Compile the shared module — confirms AgentClient and BedrockClient changes type-check
  ./gradlew :shared:compileKotlinMetadata
  # Expected: BUILD SUCCESSFUL, no compilation errors
  ```

- [ ] 19. Mobile — `AmigoClient`
  - Create `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/AmigoClient.kt`
  - Constructor accepts `edgeFunctionUrl: String` and `getAuthToken: suspend () -> String?`; no AWS/Bedrock dependencies
  - Override `ownsSessionState = true`
  - `createSession`: POST `/create-session` with `{ hat, sessionConfig }`; deserialize to `CreateSessionResponse` (dataCollected as `List<DataCollectedEntry>`)
  - `sendMessage`: POST `/send-message` with `{ sessionId, message }` only — no `conversationHistory`, `data_collected`, `agentId`, or `agentAliasId`; map `AgentResponse.completion` → `BedrockResponse.completion` and `AgentResponse.data_collected` (array) → `BedrockResponse.dataCollected` (JsonElement) for `AmigoAgentConversation` compatibility
  - `getSession`: GET `/get-session?sessionId=<uuid>`; deserialize to `GetSessionResponse`
  - Do not retry on non-null `error` field — errors are terminal
  - _Requirements: 14.3, 14.4, 14.5, 14.6, 14.10_

  #### Verification
  ```bash
  ./gradlew :shared:compileKotlinMetadata
  # Expected: BUILD SUCCESSFUL

  # Run existing BedrockClient unit tests to confirm no regressions
  ./gradlew :shared:allTests --tests "*.BedrockClientTest"
  # Expected: all tests pass
  ```

- [ ] 20. Mobile — `AppConfig` wiring and `AmigoAgentConversation` refactor
  - Add `AI_MODE` constant to `AppConfig.kt` with values `"strands"` or `"bedrockagent"`; add `STRANDS_EDGE_FUNCTION_URL`
  - Default to `"bedrockagent"` and log a warning if `AI_MODE` is unrecognized
  - Refactor `AmigoAgentConversation` to depend on `AgentClient` instead of `BedrockClient` directly
  - On `startSession`: if `agentClient.ownsSessionState`, call `createSession` to obtain `sessionId` (resuming if available); store `sessionId` for subsequent calls; do NOT initialize `accumulatedDataCollected` (server owns this state)
  - `invokeAgentForCompletion` calls `agentClient.sendMessage(sessionId, message)`
  - Skip `processActionInvocations` when `agentClient.ownsSessionState` (invocations always null in strands mode)
  - When `agentClient.ownsSessionState`, do NOT pass `dataCollected` in any request — the `dataCollected` parameter to `invokeAgent` / `sendMessage` is always omitted
  - Update `SessionInitializerFactory` (or equivalent construction site) to read `AI_MODE` and inject either `BedrockClient` or `AmigoClient`
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 9.1–9.6, 14.7–14.9, 14.11, 15.1–15.3_

  #### Verification
  ```bash
  ./gradlew :shared:compileKotlinMetadata
  # Expected: BUILD SUCCESSFUL

  # Run all shared tests — confirms no regressions in session/config logic
  ./gradlew :shared:allTests
  # Expected: all tests pass
  ```

- [ ] 21. Final checkpoint — end-to-end validation
  - Ensure all mobile shared module tests pass: `./gradlew :shared:allTests`
  - Verify `AI_MODE = "strands"` routes through `AmigoClient` → Supabase edge function
  - Verify `AI_MODE = "bedrockagent"` routes through `BedrockClient` → Lambda (unchanged)
  - Ensure all `ActionGroup*` classes still compile without errors
  - Ask the user if questions arise.

  #### Verification
  ```bash
  # All mobile tests
  ./gradlew :shared:allTests
  # Expected: BUILD SUCCESSFUL, all tests pass

  export EDGE_URL="$SUPABASE_URL/functions/v1/strands-agent"

  # End-to-end strands path: create session → send message → get session
  export SESSION_ID=$(curl -s -X POST "$EDGE_URL/create-session" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"hat":"goal_setting","sessionConfig":{"hat":"goal_setting","responsibilities":["Help user set a health goal"],"data_to_be_collected":["current_weight","target_weight"],"data_to_be_calculated":["bmr","tdee"],"notes":[]}}' \
    | jq -r '.sessionId')
  echo "Session: $SESSION_ID"

  curl -s -X POST "$EDGE_URL/send-message" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"I want to lose 5kg by June\"}" \
    | jq '{status: .completion.status_of_aim, text: .completion.ui.render.text, data_collected, error}'
  # Expected: status is "in_progress", text is non-empty, error is null

  curl -s "$EDGE_URL/get-session?sessionId=$SESSION_ID" \
    -H "Authorization: Bearer $TOKEN" \
    | jq '{aim_status, message_count: (.messages | length), data_collected}'
  # Expected: aim_status "in_progress", message_count >= 2 (user + assistant), data_collected array
  ```

---

## Notes

- Phases 1–3 (tasks 1–17) are backend-only and must be complete before Phase 4 (tasks 18–21)
- Property tests use `npm:fast-check` importable in Deno; a `supabase/functions/deno.json` import map is required (created in task 9)
- Integration tests require `TEST_USER_EMAIL`, `TEST_USER_PASSWORD`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` environment secrets
- `data_collected` is stored internally as a flat dict `{ field: value }` and serialized to `[{ field, label, value }]` array format in all API responses — this matches the Lambda's existing wire format
- The Lambda stack and `ActionGroup*` classes remain untouched throughout — rollback is a single constant change

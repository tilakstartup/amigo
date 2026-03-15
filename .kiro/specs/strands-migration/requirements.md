# Requirements Document

## Introduction

This feature migrates the Amigo AI agent backend from AWS Lambda (Python) + Bedrock Managed Agents to the Strands Agents TypeScript SDK running inside Supabase Edge Functions. The migration is designed to be zero-downtime and fully backward-compatible: a single mode flag in `AppConfig.kt` switches the mobile client between the existing `bedrockagent` path (Lambda) and the new `strands` path (Supabase Edge Function). Both paths must return an identical JSON response contract to the mobile client. Once the `strands` path is validated in production, the Lambda stack and the mobile `ActionGroup*` classes become dead code and can be removed.

---

## Glossary

- **Strands_Edge_Function**: The new Supabase Edge Function (`supabase/functions/strands-agent/index.ts`) that hosts the Strands Agents TypeScript SDK and handles all AI agent requests in `strands` mode.
- **Lambda_Proxy**: The existing AWS Lambda function (`infrastructure/lib/stacks/bedrock-proxy/lambda/index.py`) that handles all AI agent requests in `bedrockagent` mode. Remains unchanged during migration.
- **Mobile_Client**: The Kotlin Multiplatform AI client layer in the mobile app. In `bedrockagent` mode this is `BedrockClient`; in `strands` mode this is the new `AmigoClient`.
- **AmigoClient**: A new KMP class (`AmigoClient.kt`) that calls the three Supabase Edge Function session endpoints (`POST /create-session`, `GET /get-session`, `POST /send-message`). It has no dependency on AWS or Bedrock. It implements the same `AgentClient` interface as `BedrockClient`.
- **AgentClient**: A new KMP interface that abstracts the AI backend. Both `BedrockClient` and `AmigoClient` implement it. `AmigoAgentConversation` depends on `AgentClient`, not on `BedrockClient` directly.
- **AppConfig**: The `AppConfig.kt` object that holds compile-time configuration constants, including the `AI_MODE` flag.
- **AI_MODE**: A string constant in `AppConfig` with values `"bedrockagent"` or `"strands"` that controls which backend the Mobile_Client calls.
- **Agent_Response**: The JSON object returned by either backend to the Mobile_Client. Shape: `{ completion, data_collected, invocations, invocationId, error, userId, subscription_status, timestamp }`.
- **Session_Config**: The first-turn payload sent by the Mobile_Client containing `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`, and `initial_message`.
- **Conversation_History**: The ordered list of `{ role, content }` message pairs stored server-side in the `conversation_sessions` table. In `strands` mode the server loads this from the database on each `send-message` call — the Mobile_Client does not send it.
- **Tool**: A TypeScript function decorated with `@tool` in the Strands Agents SDK that the agent can invoke server-side (replaces Bedrock RETURN_CONTROL).
- **Shared_Module**: TypeScript modules placed in `supabase/functions/_shared/` and imported by the Strands_Edge_Function and any other edge functions. Contains all database operations, health calculations, session management, JWT verification, and response-building utilities as plain TypeScript functions with no dependency on the Strands SDK.
- **JWT**: A Supabase-issued JSON Web Token sent in the `Authorization: Bearer` header by the Mobile_Client.
- **Subscription_Tier**: The user's subscription level (`"free"` or `"pro"`) extracted from the JWT `user_subscription` custom claim.
- **PRO_MODEL_PROVIDER**: Supabase Edge Function environment secret specifying the LLM provider for pro-tier users (`"bedrock"` | `"openai"` | `"deepseek"`). Defaults to `"bedrock"` if absent.
- **PRO_MODEL_ID**: Supabase Edge Function environment secret specifying the model ID for pro-tier users. Falls back to a hardcoded default for the resolved provider if absent.
- **FREE_MODEL_PROVIDER**: Supabase Edge Function environment secret specifying the LLM provider for free-tier users. Defaults to `"bedrock"` if absent.
- **FREE_MODEL_ID**: Supabase Edge Function environment secret specifying the model ID for free-tier users. Falls back to a hardcoded default for the resolved provider if absent.
- **data_collected**: A JSON array `[{ field, label, value }]` accumulating all fields collected across turns. In `bedrockagent` mode the Mobile_Client sends this array on each turn so the Lambda can restore session state; in `strands` mode the server owns this state and the Mobile_Client never sends it. Both backends echo the current accumulated array back in the `Agent_Response`.
- **Converse_API**: The AWS Bedrock `converse` / `converseStream` API used directly by the Strands SDK (replaces Bedrock Managed Agents invoke API).
- **Hat**: A session type identifier string (e.g., `"onboarding"`, `"goal_setting"`, `"general_chat"`) that determines the agent's responsibilities for a session.
- **conversation_sessions**: A Supabase table that persists conversation state between app sessions. Schema: `session_id TEXT PK, user_id UUID, hat TEXT, messages JSONB, data_collected JSONB, aim_status TEXT, is_expired BOOLEAN, updated_at TIMESTAMPTZ`. Protected by RLS so users can only access their own rows.
- **aim_status**: A column in `conversation_sessions` mirroring the agent's `status_of_aim` field. Valid values: `"not_set"` | `"in_progress"` | `"completed"`. Defaults to `"not_set"`.
- **is_expired**: A boolean column in `conversation_sessions` that is set to `true` when a session is explicitly expired. Defaults to `false`. A session is resumable only when `is_expired = false` AND `aim_status != "completed"`.
- **SESSION_EXPIRY_HOURS**: A Supabase Edge Function environment secret controlling how long (in hours) an inactive session remains resumable. Defaults to `1`.
- **SESSION_MAX_MESSAGES**: A Supabase Edge Function environment secret controlling the maximum number of messages a session may accumulate before it is expired. Defaults to `20`.
- **SESSION_RESUME_MESSAGES**: A Supabase Edge Function environment secret controlling how many recent messages are returned to the Mobile_Client by `create-session` and `get-session` for UI display. Defaults to `20`.

---

## Requirements

### Requirement 1: Mode Flag and Routing

**User Story:** As a developer, I want a single compile-time flag in `AppConfig.kt` to switch between the existing Lambda backend and the new Strands Edge Function, so that I can validate the new path in production without risk.

#### Acceptance Criteria

1. THE `AppConfig` SHALL expose a constant `AI_MODE` with valid values `"bedrockagent"` and `"strands"`.
2. WHEN `AI_MODE` equals `"bedrockagent"`, THE `Mobile_Client` SHALL send all agent requests to `AppConfig.BEDROCK_API_ENDPOINT` (the existing Lambda URL) using the existing request format.
3. WHEN `AI_MODE` equals `"strands"`, THE `Mobile_Client` SHALL send all agent requests to `AppConfig.STRANDS_EDGE_FUNCTION_URL` (the new Supabase Edge Function URL) using the Strands request format.
4. THE `Mobile_Client` SHALL NOT change the response-handling logic based on `AI_MODE`; both backends MUST return an identical `Agent_Response` JSON shape.
5. IF `AI_MODE` is set to an unrecognized value, THEN THE `Mobile_Client` SHALL default to `"bedrockagent"` and log a warning.

---

### Requirement 2: Strands Edge Function — Authentication

**User Story:** As a security engineer, I want the Strands Edge Function to verify the caller's identity before processing any request, so that only authenticated users can invoke the AI agent.

#### Acceptance Criteria

1. WHEN a request arrives at the Strands_Edge_Function, THE Strands_Edge_Function SHALL extract the JWT from the `Authorization: Bearer` header.
2. WHEN the JWT is valid and not expired, THE Strands_Edge_Function SHALL extract `sub` (user ID) and the `user_subscription` custom claim (`subscription_status`, `is_active`) from the JWT payload.
3. IF the JWT is absent, malformed, or expired, THEN THE Strands_Edge_Function SHALL return HTTP 401 with an `Agent_Response` where `error` is `"Invalid or expired token"` and all other fields are `null`.
4. WHERE the request `hat` equals `"onboarding"`, THE Strands_Edge_Function SHALL permit requests with no JWT and assign an anonymous `userId` of the form `"onboarding-anon-{timestamp}"` with `subscription_status = "free"`.
5. THE Strands_Edge_Function SHALL use Supabase's built-in JWT verification (via the `Authorization` header forwarded to the Supabase client) rather than a custom EC key implementation.

---

### Requirement 3: Session API — Endpoints

**User Story:** As a mobile developer, I want dedicated session management endpoints so that the mobile client does not need to maintain or transmit conversation history on every turn.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL expose three endpoints: `POST /create-session`, `GET /get-session`, and `POST /send-message`.
2. ALL three endpoints SHALL require a valid JWT in the `Authorization: Bearer` header, except `POST /create-session` when `hat` equals `"onboarding"` (anonymous allowed per Requirement 2).
3. IF any required field is missing from a request body, THE endpoint SHALL return HTTP 400 with `error: "Missing required parameters"`.

#### `POST /create-session`

4. THE `POST /create-session` endpoint SHALL accept a JSON body with fields: `hat` (required) and `sessionConfig` (required on first turn, contains `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`, `initial_message`).
5. WHEN `POST /create-session` is called, THE endpoint SHALL check for an existing resumable session for `(user_id, hat)` where `is_expired = false` AND `aim_status != "completed"`.
6. IF a resumable session exists, THE endpoint SHALL return that session's `sessionId`, `aim_status`, `data_collected`, and the most recent `SESSION_RESUME_MESSAGES` messages (configurable env secret, default `20`) rather than creating a new one.
7. IF no resumable session exists, THE endpoint SHALL create a new session record in `conversation_sessions` with a generated `sessionId`, mark any prior session for `(user_id, hat)` as `is_expired = true`, and return the new `sessionId` with empty `messages` and `data_collected`.
8. THE response SHALL have shape: `{ sessionId, isResumed, messages, data_collected, aim_status }` where `data_collected` is the wire-format array `[{ field, label, value }]` (same format as `Agent_Response.data_collected`).

#### `GET /get-session`

9. THE `GET /get-session` endpoint SHALL accept a `sessionId` query parameter.
10. THE endpoint SHALL return the session record for the given `sessionId` including: `sessionId`, `hat`, `aim_status`, `is_expired`, `data_collected` (wire-format array `[{ field, label, value }]`), and the most recent `SESSION_RESUME_MESSAGES` messages.
11. IF the session does not exist or belongs to a different user, THE endpoint SHALL return HTTP 404.

#### `POST /send-message`

12. THE `POST /send-message` endpoint SHALL accept a JSON body with fields: `sessionId` (required) and `message` (required).
13. THE endpoint SHALL load the full `conversationHistory` and `data_collected` from the `conversation_sessions` table using `sessionId` — the Mobile_Client SHALL NOT send history or `data_collected` in this request.
14. IF the session is expired (`is_expired = true`) or does not exist, THE endpoint SHALL return HTTP 400 with `error: "Session expired or not found"`.
15. THE endpoint SHALL run the Strands agent with the loaded history and current message, then return an `Agent_Response` as defined in Requirement 4.
16. THE endpoint SHALL persist the updated session (new messages + merged `data_collected` + updated `aim_status`) to the database after each turn per the rules in Requirement 16.

---

### Requirement 4: Strands Edge Function — Response Contract

**User Story:** As a mobile developer, I want the Strands Edge Function to return the exact same JSON shape as the Lambda, so that the Mobile_Client response-handling code is unchanged.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL return HTTP 200 with a JSON body matching the `Agent_Response` shape: `{ completion, data_collected, invocations, invocationId, error, userId, subscription_status, timestamp }`.
2. THE `completion` field SHALL be a JSON object matching the agent's structured response schema (`status_of_aim`, `ui`, `input`, `previous_field_collected`).
3. THE `data_collected` field SHALL be a JSON array `[{ field, label, value }]` reflecting all fields accumulated across turns. The `label` for each entry is the human-readable display name (e.g. `"Current Weight"` for field `"current_weight"`). This matches the format the Lambda already returns so the Mobile_Client response-handling code is unchanged.
4. THE `invocations` field SHALL be `null` in `strands` mode because all tool execution happens server-side inside the Strands_Edge_Function.
5. THE `invocationId` field SHALL be `null` in `strands` mode.
6. IF an unrecoverable error occurs, THEN THE Strands_Edge_Function SHALL return HTTP 200 with `completion: null`, `error: "<message>"`, and all other data fields as `null`.
7. THE `timestamp` field SHALL be an ISO 8601 UTC string.
8. THE `subscription_status` field SHALL reflect the tier resolved from the JWT (`"free"` or `"pro"`).

---

### Requirement 5: Strands Edge Function — Agent Execution

**User Story:** As a product engineer, I want the Strands Agents SDK to drive the conversation loop server-side, so that the mobile client no longer needs to handle RETURN_CONTROL tool execution.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL initialize a Strands `Agent` with the system prompt derived from the `Hat` value in the request (`"onboarding"`, `"goal_setting"`, or `"general_chat"`).
2. THE Strands_Edge_Function SHALL select the model provider and model ID based on `subscription_status` and `is_active` per the rules in Requirement 13.
3. THE Strands_Edge_Function SHALL load `conversationHistory` from the `conversation_sessions` table using the `sessionId` and pass it to the Strands `Agent` as prior conversation turns so the agent has full context.
4. THE Strands_Edge_Function SHALL invoke the Strands `Agent` with the current `message` and await a complete response before returning.
5. THE Strands_Edge_Function SHALL parse the agent's text output as a JSON object matching the agent response schema and place it in the `completion` field of the `Agent_Response`.
6. IF the agent's text output is not valid JSON, THEN THE Strands_Edge_Function SHALL retry the agent call once with a JSON-correction prompt before returning an error.

---

### Requirement 6: Server-Side Tools — Data Operations

**User Story:** As a backend engineer, I want the `get_profile`, `save_onboarding_data`, and `get_onboarding_status` tools to run server-side inside the Strands Edge Function, so that the mobile ActionGroup classes are no longer needed.

#### Acceptance Criteria

1. THE Shared_Module SHALL export a `getProfile(supabaseClient, userId)` function that queries the `users_profiles` table and returns profile fields (`first_name`, `last_name`, `age`, `weight_kg`, `height_cm`, `gender`, `activity_level`).
2. THE Shared_Module SHALL export a `saveOnboardingData(supabaseClient, userId, fields)` function that upserts the provided fields into the `users_profiles` table.
3. THE Shared_Module SHALL export a `getOnboardingStatus(supabaseClient, userId)` function that returns the completion percentage and list of missing required profile fields.
4. THE Strands_Edge_Function SHALL register `get_profile`, `save_onboarding_data`, and `get_onboarding_status` Strands tools that are thin wrappers calling the corresponding Shared_Module functions.
5. WHEN a data-operation tool is called for an unauthenticated user (onboarding hat), THE tool SHALL return a success response with empty field values rather than an error.
6. IF a Supabase database operation fails, THEN THE Shared_Module function SHALL throw a typed error, and the tool wrapper SHALL catch it and return a tool result with `status: "error"` and a descriptive message, allowing the agent to handle the failure gracefully.

---

### Requirement 7: Server-Side Tools — Health Calculations

**User Story:** As a backend engineer, I want the BMR, TDEE, daily-calorie, and goal-validation calculations to live in the shared module as pure functions, so that any edge function can call them directly.

#### Acceptance Criteria

1. THE Shared_Module SHALL export a pure `calculateBmr({ weight_kg, height_cm, age, gender })` function implementing the Mifflin-St Jeor equation.
2. THE Shared_Module SHALL export a pure `calculateTdee({ weight_kg, height_cm, age, gender, activity_level })` function that multiplies BMR by an activity multiplier.
3. THE Shared_Module SHALL export a pure `calculateDailyCalories({ goal_type, tdee, current_weight_kg, target_weight_kg, target_date })` function.
4. THE Shared_Module SHALL export a pure `validateGoal({ goal_type, daily_calories, gender, current_weight_kg, target_weight_kg, target_date, tdee })` function that checks USDA minimum calorie guidelines and returns `{ is_valid, message, suggestions }`.
5. THE Strands_Edge_Function SHALL register `calculate_bmr`, `calculate_tdee`, `calculate_daily_calories`, and `validate_goal` Strands tools that are thin wrappers calling the corresponding Shared_Module functions.
6. THE `calculateBmr` function SHALL produce results numerically identical (within floating-point tolerance) to the existing `HealthCalculationsActionGroup.calculateBMR` Kotlin implementation for the same inputs.
7. THE `calculateTdee` function SHALL produce results numerically identical (within floating-point tolerance) to the existing `HealthCalculationsActionGroup.calculateTDEE` Kotlin implementation for the same inputs.
8. THE `calculateDailyCalories` function SHALL produce results numerically identical (within floating-point tolerance) to the existing `HealthCalculationsActionGroup.calculateDailyCalories` Kotlin implementation for the same inputs.
9. IF any required parameter is missing or non-numeric, THEN THE tool wrapper SHALL return a tool result with `status: "error"` and a descriptive message.

---

### Requirement 8: Server-Side Tools — Goal Management

**User Story:** As a backend engineer, I want the `save_goal` logic to live in the shared module, so that goal persistence can be called directly by any edge function without going through the agent.

#### Acceptance Criteria

1. THE Shared_Module SHALL export a `saveGoal(supabaseClient, userId, goalParams)` function that writes a goal record to the Supabase database with parameters matching the existing `GoalManagementActionGroup.saveGoal` signature.
2. THE Strands_Edge_Function SHALL register a `save_goal` Strands tool that is a thin wrapper calling `Shared_Module.saveGoal`.
3. WHEN `user_overridden` is `true`, THE `saveGoal` function SHALL require `user_daily_calories` to be present and non-null.
4. IF `user_overridden` is `true` and `user_daily_calories` is absent, THEN THE tool wrapper SHALL return a tool result with `status: "error"` and message `"user_daily_calories is required when user_overridden=true"`.
5. IF the authenticated user ID is absent, THEN THE tool wrapper SHALL return a tool result with `status: "error"` and message `"Authentication required for goal management"`.
6. THE `saveGoal` function SHALL use the Supabase service-role client (not the anon client) to bypass Row Level Security when writing goal records.

---

### Requirement 9: Session History — Server-Side Ownership

**User Story:** As a mobile developer, I want the server to own conversation history entirely, so that the mobile client only needs to send the current message and receive a response.

#### Acceptance Criteria

1. THE `Mobile_Client` SHALL NOT maintain an in-memory `conversationHistory` list in `strands` mode — history is owned by the server.
2. WHEN `AI_MODE` equals `"strands"` and a new conversation screen opens, THE `Mobile_Client` SHALL call `POST /create-session` to obtain a `sessionId` (resuming an existing session if one is available).
3. THE `Mobile_Client` SHALL use the `messages` returned by `POST /create-session` or `GET /get-session` to populate the UI with prior conversation turns.
4. ON each user turn, THE `Mobile_Client` SHALL call `POST /send-message` with only `{ sessionId, message }` — no history or `data_collected` in the request body.
5. THE `Mobile_Client` SHALL NOT send `conversationHistory` or `data_collected` when `AI_MODE` equals `"strands"`.
6. THE `Mobile_Client` SHALL NOT send `conversationHistory` when `AI_MODE` equals `"bedrockagent"` (the Lambda manages session state internally via Bedrock session attributes).
7. THE `Agent_Response` returned by `POST /send-message` SHALL include the current `data_collected` so the mobile UI can display collected fields progressively.

---

### Requirement 10: data_collected Accumulation

**User Story:** As a product engineer, I want `data_collected` to accumulate correctly across turns in `strands` mode, so that the mobile UI can display collected fields progressively.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL merge the `data_collected` array loaded from the database (not from the Mobile_Client — the client does not send it in `send-message` requests) with any new `previous_field_collected` value returned by the agent in the current turn.
2. WHEN the agent returns a non-null `previous_field_collected` with a non-null `value`, THE Strands_Edge_Function SHALL add `{ [field]: value }` to the merged `data_collected` object.
3. WHEN the agent returns a `previous_field_collected` with a `null` value and the field does not yet exist in `data_collected`, THE Strands_Edge_Function SHALL store `null` for that field.
4. WHEN the agent returns a `previous_field_collected` with a `null` value and the field already exists in `data_collected`, THE Strands_Edge_Function SHALL preserve the existing value.
5. THE Strands_Edge_Function SHALL return the merged `data_collected` array in the `Agent_Response` so the Mobile_Client can display collected fields progressively.
6. FOR ALL sequences of turns where the same field is collected once and never corrected, the final `data_collected` array SHALL contain exactly one entry for that field with the collected value (idempotence of accumulation).

---

### Requirement 11: JSON Response Validation

**User Story:** As a reliability engineer, I want the Strands Edge Function to validate the agent's JSON output before returning it to the mobile client, so that malformed responses are caught server-side.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL validate that the agent's text output is a parseable JSON object containing all required fields: `status_of_aim`, `ui`, `input`, `previous_field_collected`.
2. THE Strands_Edge_Function SHALL validate that `status_of_aim` is one of `"not_set"`, `"in_progress"`, `"completed"`.
3. THE Strands_Edge_Function SHALL validate that `ui.render.type` is one of `"info"`, `"message"`, `"message_with_summary"`.
4. THE Strands_Edge_Function SHALL validate that `ui.render.text` is a non-empty string.
5. THE Strands_Edge_Function SHALL validate that `input.type` is one of `"text"`, `"weight"`, `"date"`, `"quick_pills"`, `"yes_no"`, `"dropdown"`. IF `ui.render.type` equals `"info"` AND `input` is `null`, this validation SHALL be skipped (null input is valid for info renders).
6. IF validation fails on the first attempt, THEN THE Strands_Edge_Function SHALL retry the agent call once with a JSON-correction prompt.
7. IF validation fails on the retry attempt, THEN THE Strands_Edge_Function SHALL return an `Agent_Response` with a fallback `completion` containing a generic error message and `input.type = "text"`.
8. FOR ALL valid agent responses, parsing the `completion` field as JSON and re-serializing it SHALL produce an equivalent JSON object (round-trip property).

---

### Requirement 12: Shared Module Structure

**User Story:** As a developer, I want all database operations, health calculations, and business logic to live in `supabase/functions/_shared/` as plain TypeScript functions, so that any edge function can import and call them directly without going through the agent.

#### Acceptance Criteria

1. THE Shared_Module SHALL export a `verifyJwt` function that validates a Supabase JWT and returns `{ userId, subscriptionStatus, isActive }` or `null` on failure.
2. THE Shared_Module SHALL export a `buildAgentResponse` function that constructs a well-formed `Agent_Response` object.
3. THE Shared_Module SHALL export a `buildErrorResponse` function that constructs a well-formed error `Agent_Response` object.
4. THE Shared_Module SHALL export an `accumulateDataCollected` function implementing the merge rules from Requirement 10.
5. THE Shared_Module SHALL export a `validateAgentJson` function implementing the validation rules from Requirement 11.
6. THE Shared_Module SHALL export TypeScript interfaces for `AgentResponse`, `SessionConfig`, `ConversationMessage`, and `DataCollected`.
7. THE Shared_Module SHALL export plain TypeScript functions for all database operations: `getProfile`, `saveOnboardingData`, `getOnboardingStatus`, `saveGoal`, `getSession`, `createSession`, `upsertSession`, `expireSession`, `findResumableSession`, `expireSessionsForUser`. These functions SHALL accept a Supabase client instance and typed parameters, and SHALL NOT depend on the Strands SDK.
8. THE Shared_Module SHALL export plain TypeScript functions for all health calculations: `calculateBmr`, `calculateTdee`, `calculateDailyCalories`, `validateGoal`. These functions SHALL be pure functions with no external dependencies.
9. THE Strands agent tool implementations in the Strands_Edge_Function SHALL be thin wrappers that call the corresponding Shared_Module functions — they SHALL NOT contain database or calculation logic themselves.
10. ANY future Supabase Edge Function SHALL be able to import and call Shared_Module functions directly to perform database operations or calculations without invoking the agent.

---

### Requirement 13: Subscription-Aware Model Selection

**User Story:** As a product manager, I want the Strands Edge Function to route pro users to a higher-capability model and free users to a cost-efficient model, with each tier independently configurable for both provider and model, so that the subscription tiers deliver differentiated AI quality and the team can easily swap providers per tier.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL read four environment secrets: `PRO_MODEL_PROVIDER`, `PRO_MODEL_ID`, `FREE_MODEL_PROVIDER`, and `FREE_MODEL_ID`.
2. `PRO_MODEL_PROVIDER` and `FREE_MODEL_PROVIDER` SHALL each accept values `"bedrock"`, `"openai"`, or `"deepseek"`. If absent or unrecognized, THE Strands_Edge_Function SHALL default to `"bedrock"` and log a warning.
3. WHEN `subscription_status` equals `"pro"` and `is_active` equals `true`, THE Strands_Edge_Function SHALL use `PRO_MODEL_PROVIDER` as the provider and `PRO_MODEL_ID` as the model ID.
4. WHEN `subscription_status` is `"free"` or `is_active` is `false`, THE Strands_Edge_Function SHALL use `FREE_MODEL_PROVIDER` as the provider and `FREE_MODEL_ID` as the model ID.
5. WHEN the resolved provider equals `"bedrock"`, THE Strands_Edge_Function SHALL initialize the Strands `Agent` using the AWS Bedrock provider.
6. WHEN the resolved provider equals `"openai"`, THE Strands_Edge_Function SHALL initialize the Strands `Agent` using the OpenAI provider.
7. WHEN the resolved provider equals `"deepseek"`, THE Strands_Edge_Function SHALL initialize the Strands `Agent` using the DeepSeek provider.
8. IF the model ID env var for the resolved tier is absent, THEN THE Strands_Edge_Function SHALL log a warning and fall back to a hardcoded default model ID for that provider.
9. THE pro and free tiers MAY use different providers (e.g. pro → Bedrock Claude, free → DeepSeek), allowing independent configuration of both provider and model per tier.

---

### Requirement 14: Mobile Client — AmigoClient for Strands Mode

**User Story:** As a mobile developer, I want a dedicated `AmigoClient` that calls the Supabase Edge Function session API, so that the Strands backend has a clean, purpose-built client with no Bedrock coupling.

#### Acceptance Criteria

1. A new `AgentClient` interface SHALL be introduced in the mobile shared module with methods: `createSession(hat, sessionConfig)`, `sendMessage(sessionId, message)`, and `getSession(sessionId)`.
2. `BedrockClient` SHALL implement `AgentClient` — its existing `invokeAgent` method maps to `sendMessage`, and `createSession`/`getSession` are no-ops that return a stub response (the Lambda manages session state internally).
3. A new `AmigoClient` class SHALL implement `AgentClient` and call the three Supabase Edge Function endpoints: `POST /create-session`, `GET /get-session`, and `POST /send-message`.
4. `AmigoClient` SHALL accept `supabaseEdgeFunctionUrl` and `getAuthToken` as constructor parameters — it has no dependency on AWS, Bedrock, `agentId`, or `agentAliasId`.
5. `AmigoClient.sendMessage` SHALL send only `{ sessionId, message }` in the request body — no `conversationHistory`, `data_collected`, `agentId`, or `agentAliasId`.
6. `AmigoClient.sendMessage` SHALL map the HTTP response to the existing `BedrockResponse` Kotlin data class so that `AmigoAgentConversation` requires no response-handling changes.
7. `AmigoAgentConversation` SHALL be refactored to depend on `AgentClient` instead of `BedrockClient` directly.
8. WHEN `agentClient.ownsSessionState` is `true`, THE `AmigoAgentConversation` SHALL skip the `processActionInvocations` step because `invocations` will always be `null` in Strands mode.
9. WHEN `agentClient.ownsSessionState` is `true`, THE `AmigoAgentConversation` SHALL call `createSession` on startup to obtain a `sessionId`, then pass that `sessionId` on every `sendMessage` call.
10. THE `Mobile_Client` SHALL NOT retry an `AmigoClient` request that returns a non-null `error` field (errors are terminal in Strands mode).
11. WHEN `AI_MODE` equals `"bedrockagent"`, THE existing `BedrockClient` code path SHALL remain unchanged.

---

### Requirement 17: Integration Testing with Test Account---

### Requirement 16: Conversation Session Persistence

**User Story:** As a mobile developer, I want the Strands Edge Function to persist each conversation session to the database after every turn, so that sessions can be resumed after an app kill or device restart.

#### Acceptance Criteria

1. THE Strands_Edge_Function SHALL upsert a session record to the `conversation_sessions` Supabase table after each successful turn, with columns: `session_id TEXT PK`, `user_id UUID`, `hat TEXT`, `messages JSONB`, `data_collected JSONB`, `aim_status TEXT`, `is_expired BOOLEAN DEFAULT false`, `updated_at TIMESTAMPTZ`.
2. THE upsert SHALL be non-blocking — it SHALL NOT delay the response returned to the Mobile_Client.
3. IF the upsert fails, THE Strands_Edge_Function SHALL log a warning but SHALL still return the normal `Agent_Response` to the Mobile_Client.
4. THE `messages` field SHALL contain the full `conversationHistory` array (all turns including the current one), trimmed to the last `SESSION_MAX_MESSAGES` messages before storing.
5. THE `data_collected` field SHALL be updated by merging the stored value with any new `previous_field_collected` returned by the agent — the Mobile_Client does NOT send `data_collected` in `send-message` requests.
6. THE `aim_status` field SHALL be updated to match the `status_of_aim` value returned by the agent in the current turn.
7. THE Strands_Edge_Function SHALL expire a session (set `is_expired = true`) when ANY of the following conditions are met:
   a. The session's `updated_at` is older than `SESSION_EXPIRY_HOURS` hours (default: `1`) at the time of the next request.
   b. The number of messages in the session reaches or exceeds `SESSION_MAX_MESSAGES` (default: `20`).
   c. A new session starts for the same `(user_id, hat)` pair — the previous session for that pair SHALL be marked `is_expired = true`.
8. `SESSION_EXPIRY_HOURS`, `SESSION_MAX_MESSAGES`, and `SESSION_RESUME_MESSAGES` SHALL be read from Supabase Edge Function environment secrets. If absent, the defaults (`1` hour, `20` messages, `20` resume messages) SHALL be used.
9. THE `conversation_sessions` table SHALL have a Row Level Security policy that allows a user to read and write only rows where `user_id` matches their own JWT `sub`.

---

### Requirement 17: Integration Testing with Test Account

**User Story:** As a developer, I want a dedicated Supabase test account and a test harness that can authenticate and call the deployed edge functions end-to-end, so that I can validate the backend APIs without a mobile client.

#### Acceptance Criteria

1. A dedicated test user account SHALL exist in Supabase Auth with credentials stored in `TEST_USER_EMAIL` and `TEST_USER_PASSWORD` environment secrets (never production credentials).
2. THE test harness SHALL sign in via the Supabase Auth API using the test credentials to obtain a valid JWT before each test run.
3. THE test harness SHALL use the obtained JWT as the `Authorization: Bearer` token in all HTTP requests to the edge function endpoints.
4. THE test harness SHALL be able to call all three session API endpoints (`POST /create-session`, `GET /get-session`, `POST /send-message`) against the deployed edge function URL.
5. THE test account SHALL have a known, fixed `subscription_status` (e.g. `"free"`) so that model selection behavior is deterministic in tests.
6. EACH test run SHALL clean up after itself — deleting any `conversation_sessions` rows created during the test for the test user, so tests are repeatable and idempotent.
7. THE test harness SHALL be runnable from the command line (e.g. `deno test` or `npx ts-node`) with no mobile client required.
8. THE test harness SHALL be located in `supabase/functions/tests/` and SHALL import test utilities from `supabase/functions/_shared/`.

---

### Requirement 15: Backward Compatibility and Rollback

**User Story:** As an operations engineer, I want to be able to switch back to the Lambda backend instantly by changing a single constant, so that any production issue with the Strands path can be mitigated without a code deployment.

#### Acceptance Criteria

1. THE Lambda_Proxy SHALL remain deployed and unmodified throughout the migration.
2. THE `bedrockagent` code path in the Mobile_Client SHALL remain fully functional when `AI_MODE` equals `"bedrockagent"`.
3. WHEN `AI_MODE` is changed from `"strands"` to `"bedrockagent"` and the app is rebuilt, THE Mobile_Client SHALL resume sending requests to the Lambda_Proxy with no other code changes required.
4. THE mobile `ActionGroupRegistry`, `DataOperationsActionGroup`, `GoalManagementActionGroup`, and `HealthCalculationsActionGroup` classes SHALL remain in the codebase and compile without errors until explicitly removed after full migration validation.

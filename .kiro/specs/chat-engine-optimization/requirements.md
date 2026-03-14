# Chat Engine Optimization Requirements

## Introduction

The Amigo chat engine optimization improves efficiency and reliability of AI-powered conversational sessions by restructuring session configuration, implementing proper session attribute management in Lambda, enriching responses with accumulated data, and adding JSON validation with retry logic. This ensures consistent session state across invocations, proper data accumulation, and graceful error handling for malformed agent responses.

**Note:** The agent instruction document (`infrastructure/lib/stacks/bedrock-agent/instruction.md`) has been updated to align with these requirements and is ready for implementation.

## Glossary

- **Session Config**: Configuration object defining a conversational session's structure, including responsibilities, data collection requirements, and initial message
- **Session Attributes**: Persistent key-value data stored by Bedrock across Lambda invocations for the same sessionId
- **Lambda**: AWS Lambda function serving as proxy between mobile client and Bedrock Agent
- **Bedrock Agent**: AWS Bedrock Agent that executes conversational logic and returns JSON responses
- **initial_message**: The first user message sent to the agent; NOT stored in session attributes
- **data_collected**: Accumulated field values collected across multiple agent turns
- **current_field**: The specific field the agent is currently requesting from the user
- **invocationId**: Unique identifier for a Bedrock Agent function invocation request
- **JSON Validation**: Process of verifying agent response is valid JSON in expected format
- **Retry Logic**: Mechanism to re-invoke agent when JSON validation fails, up to 3 attempts

## Requirements

### Requirement 1: Session Config Format Restructuring

**User Story:** As a developer, I want session configurations to follow a consistent, optimized format, so that session setup is clear and maintainable.

#### Acceptance Criteria

1. WHEN a session config is created, THE SessionConfig SHALL include fields: `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`, and `initial_message`
2. WHEN a session config is validated, THE SessionConfig SHALL require the `hat` field (no legacy `cap` field support)
3. WHEN session attributes are stored, THE `hat` field SHALL represent the session's name/identifier (e.g., "onboarding", "goal-setting")
4. WHEN a session config is serialized, THE SessionConfig SHALL NOT include `initial_message` in the serialized session attributes sent to Bedrock
5. WHEN a session config is accessed, THE SessionConfig SHALL provide `initial_message` as a separate field for client use

### Requirement 2: Session Attributes Structure and Initialization

**User Story:** As a Lambda function, I want a clear structure for session attributes so that all components can reliably access and manage session data.

#### Acceptance Criteria

1. WHEN a session is initialized, THE session attributes SHALL contain: `user_id`, `auth_header_name`, `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`, `data_collected`, and `json_validation_retry_count`
2. WHEN Lambda stores session attributes, THE `user_id` and `auth_header_name` fields SHALL be required
3. WHEN Lambda stores session attributes, THE `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, and `notes` fields SHALL be required
4. WHEN a session is first created, THE `data_collected` field SHALL be initialized as an empty object `{}`
5. WHEN a session is first created, THE `json_validation_retry_count` field SHALL be initialized to `0`
6. WHEN Lambda retrieves session attributes on subsequent turns, THE all fields SHALL be present and unchanged from initialization (except `data_collected` and `json_validation_retry_count`)

### Requirement 3: Lambda Session Attribute Management

**User Story:** As a Lambda function, I want to properly manage session attributes so that session configuration persists across invocations and initial_message is handled correctly.

#### Acceptance Criteria

1. WHEN Lambda receives a session config, THE Lambda SHALL extract `initial_message` from the config
2. WHEN Lambda receives a session config, THE Lambda SHALL store all other config fields (`hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`) in Bedrock session attributes
3. WHEN Lambda prepares session attributes, THE Lambda SHALL include `user_id` and `auth_header_name` alongside config fields
4. WHEN Lambda sends the first request to the agent, THE Lambda SHALL send `initial_message` as the `inputText` parameter
5. WHEN Lambda receives subsequent requests for the same sessionId, THE Lambda SHALL retrieve session attributes from Bedrock (Bedrock handles persistence automatically)
6. WHEN Lambda receives a request with returnControlInvocationResults, THE Lambda SHALL NOT include `inputText` in the invoke_agent call (only session state with results)

### Requirement 4: Data Collected Field Initialization and Updates

**User Story:** As a Lambda function, I want to properly initialize and update the data_collected field so that accumulated data is always accurate and free of duplicates.

#### Acceptance Criteria

1. WHEN a session is first created, THE `data_collected` field in session attributes SHALL be initialized as empty object `{}`
2. WHEN Lambda receives an agent response, THE Lambda SHALL parse the agent's JSON response
3. WHEN Lambda parses the agent response, THE Lambda SHALL extract the `current_field` object containing field name, label, and value
4. WHEN Lambda extracts `current_field`, THE Lambda SHALL accumulate the field value into `data_collected` in session attributes
5. WHEN Lambda accumulates a field into `data_collected`, THE Lambda SHALL check if the field already exists
6. WHEN a field already exists in `data_collected`, THE Lambda SHALL overwrite the existing value (not append or merge)
7. WHEN a field is new, THE Lambda SHALL add it to `data_collected`
8. WHEN Lambda returns response to client, THE `data_collected` in response SHALL be a copy of the accumulated data from session attributes
9. WHEN the session ends, THE `data_collected` SHALL contain all fields collected across all turns with their final values

### Requirement 5: JSON Response Validation Rules

**User Story:** As a Lambda function, I want specific validation rules for agent responses so that I can reliably detect malformed JSON.

#### Acceptance Criteria

1. WHEN Lambda validates an agent response, THE response MUST be valid JSON (parseable as JSON object)
2. WHEN Lambda validates an agent response, THE response MUST contain all required fields: `status_of_aim`, `ui`, `input`, `current_field`
3. WHEN Lambda validates `status_of_aim`, THE value SHALL be one of: "not_set", "in_progress", or "completed"
4. WHEN Lambda validates `ui`, THE object MUST contain `render` and `tone` fields
5. WHEN Lambda validates `ui.render`, THE object MUST contain `type` and `text` fields
6. WHEN Lambda validates `ui.render.type`, THE value SHALL be one of: "info", "message", or "message_with_summary"
7. WHEN Lambda validates `ui.render.text`, THE value SHALL be a non-empty string
8. WHEN Lambda validates `input`, THE object MUST contain `type` field
9. WHEN Lambda validates `input.type`, THE value SHALL be one of: "text", "weight", "date", "quick_pills", "yes_no", or "dropdown"
10. WHEN Lambda validates `current_field`, THE object MUST contain `field`, `label`, and `value` fields
11. WHEN Lambda validates `current_field.field`, THE value SHALL be a non-empty string
12. WHEN Lambda validates `current_field.label`, THE value SHALL be a non-empty string
13. WHEN Lambda validates `current_field.value`, THE value SHALL be either a string or null (not empty string)

### Requirement 6: JSON Validation Retry Mechanism

**User Story:** As a Lambda function, I want to track and manage retry attempts so that I can enforce the 3-retry limit and handle validation failures gracefully.

#### Acceptance Criteria

1. WHEN Lambda receives an agent response, THE Lambda SHALL check `json_validation_retry_count` from session attributes
2. WHEN JSON validation fails, THE Lambda SHALL increment `json_validation_retry_count` in session attributes
3. WHEN `json_validation_retry_count` is less than `3`, THE Lambda SHALL send the message "Please respond with JSON object ONLY" as a regular user message to the agent
4. WHEN a retry message is sent, THE Lambda SHALL re-invoke the agent with the retry message in the same session (not a new session)
5. WHEN `json_validation_retry_count` reaches `3`, THE Lambda SHALL NOT retry again and SHALL return error response
6. WHEN a new session starts, THE `json_validation_retry_count` SHALL reset to `0`

### Requirement 7: Lambda Response Structure and Enrichment

**User Story:** As a Lambda function, I want to return consistent response structures so that the client can reliably parse responses and has complete context.

#### Acceptance Criteria

1. WHEN Lambda returns a successful response, THE response structure SHALL be: `{ completion: <agent_json_object>, data_collected: {...}, invocationId: "..." }`
2. WHEN Lambda returns a response with function invocations, THE response structure SHALL include `invocations` array: `{ completion: <agent_json_object>, data_collected: {...}, invocations: [...], invocationId: "..." }`
3. WHEN Lambda returns a response, THE `completion` field SHALL contain the complete JSON object returned by the agent (not stringified)
4. WHEN Lambda returns a response, THE `data_collected` field SHALL always be present in successful responses (never omitted)
5. WHEN Lambda returns a response, THE `invocationId` field SHALL be present in all responses (successful and error)

### Requirement 8: Error Response Format and Handling

**User Story:** As a Lambda function, I want to return detailed error responses so that the client can handle failures appropriately and gracefully.

#### Acceptance Criteria

1. WHEN JSON validation fails 3 times, THE Lambda SHALL return an error response with structure: `{ error: "Agent failed to respond with valid JSON after 3 retries", userId: "...", timestamp: "...", invocationId: "..." }`
2. WHEN an error response is returned, THE `error` field SHALL contain a descriptive message explaining the failure
3. WHEN an error response is returned, THE `userId` field SHALL contain the user identifier
4. WHEN an error response is returned, THE `timestamp` field SHALL contain ISO8601 formatted timestamp
5. WHEN an error response is returned, THE `invocationId` field SHALL be included if available from the last agent response
6. WHEN Lambda encounters a server error (non-JSON parsing), THE Lambda SHALL return error response with message describing the server error
7. WHEN Lambda returns an error response, THE response SHALL NOT include `completion`, `data_collected`, or `invocations` fields
8. WHEN an error occurs, THE session attributes SHALL be preserved for potential recovery

### Requirement 9: Agent Instruction Updates

**User Story:** As an agent, I want to read session configuration from session attributes so that I can execute responsibilities without redundant context.

#### Acceptance Criteria

1. WHEN the agent receives a session, THE Agent SHALL read `hat`, `data_to_be_collected`, `data_to_be_calculated`, and `responsibilities` from session attributes
2. WHEN the agent generates a response, THE Agent SHALL return `status_of_aim` field (not `aimofchat.status`) with values: "not_set", "in_progress", or "completed"
3. WHEN the agent generates a response, THE Agent SHALL return only the `current_field` object (field name, label, value) without echoing session context
4. WHEN the agent completes a responsibility, THE Agent SHALL NOT repeat session configuration details in responses
5. WHEN the agent receives session attributes, THE Agent SHALL use them as the authoritative source for what to collect and calculate
6. WHEN the agent processes `current_field`, THE Agent SHALL ensure the field value is either a collected value or null

### Requirement 10: Session State Persistence Across Turns

**User Story:** As a conversational session, I want session state to persist correctly so that data is not lost between turns.

#### Acceptance Criteria

1. WHEN a session is created with a sessionId, THE Bedrock Agent SHALL maintain session attributes for that sessionId across all invocations
2. WHEN Lambda stores data in session attributes, THE data SHALL persist until the session is explicitly closed
3. WHEN the client sends a new message for the same sessionId, THE Lambda SHALL retrieve the persisted session attributes
4. WHEN session attributes are retrieved, THE `data_collected` field SHALL contain all previously accumulated field values
5. WHEN a new turn begins, THE Agent SHALL have access to all previously collected data via session attributes

### Requirement 11: Client Response Handling

**User Story:** As a mobile client, I want to handle the new response format so that I can properly display data and handle errors.

#### Acceptance Criteria

1. WHEN the client receives a Lambda response, THE BedrockClient SHALL parse the response structure: `{ completion: <json>, data_collected: {...}, invocationId: "..." }`
2. WHEN the client receives a response, THE BedrockClient SHALL check for an `error` field and handle errors gracefully
3. WHEN an error is present, THE BedrockClient SHALL display the error to the user and stop the session
4. WHEN the client receives `data_collected`, THE BedrockClient SHALL accumulate it across turns for session context
5. WHEN the client receives `invocationId`, THE BedrockClient SHALL use it for subsequent returnControlInvocationResults
6. WHERE the response contains function invocations, THE BedrockClient SHALL execute them client-side and return results with the invocationId


### Requirement 12: Session Completion and Data Persistence

**User Story:** As a system, I want to persist completed session data to the database so that I can maintain audit trails, enable analytics, and support future recovery.

#### Acceptance Criteria

1. WHEN an agent response indicates `status_of_aim` is "completed", THE Lambda SHALL recognize the session as complete
2. WHEN a session is marked complete, THE Lambda SHALL store the final `data_collected` to the database with the `sessionId` as the key
3. WHEN storing completed session data, THE Lambda SHALL include: `sessionId`, `userId`, `hat` (session type), `data_collected`, `completion_timestamp`, and `status_of_aim`
4. WHEN storing completed session data, THE Lambda SHALL use ISO8601 format for `completion_timestamp`
5. WHEN a session is stored, THE Lambda SHALL mark the Bedrock session as closed (no further invocations for that sessionId)
6. WHEN storing completed session data, THE Lambda SHALL ensure the data is immutable (no updates after storage)
7. WHEN a session is completed, THE Lambda SHALL return the final response with `data_collected` before closing the session

### Requirement 13: Client Session Completion Handling

**User Story:** As a mobile client, I want to handle session completion so that I can finalize data and close the session properly.

#### Acceptance Criteria

1. WHEN the client receives a response with `status_of_aim` = "completed", THE client SHALL recognize the session as complete
2. WHEN a session is complete, THE client SHALL display the final `data_collected` to the user
3. WHEN a session is complete, THE client SHALL stop sending further messages to the same sessionId
4. WHEN a session is complete, THE client SHALL persist the final `data_collected` locally for reference
5. WHEN a session is complete, THE client SHALL close the session UI and return to the previous screen

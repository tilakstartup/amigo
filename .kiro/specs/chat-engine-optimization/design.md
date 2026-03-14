# Chat Engine Optimization Design

## Overview

The chat engine optimization restructures the Amigo conversational AI system to improve efficiency, reliability, and maintainability. The design addresses five core areas:

1. **Session Configuration Restructuring**: Migrate from `cap` to `hat` field, separate `initial_message` from session attributes
2. **Session Attribute Management**: Implement proper session lifecycle with required fields and initialization
3. **JSON Validation with Retry Logic**: Add 3-retry mechanism for malformed agent responses
4. **Data Accumulation**: Implement `data_collected` field that accumulates across turns without duplication
5. **Error Handling**: Graceful error responses with detailed context for client recovery

This design ensures consistent session state across Lambda invocations, proper data persistence through Bedrock's session attributes, and reliable error handling for production deployments.

## Architecture

### System Components

```
Mobile Client (BedrockClient)
    ↓
Lambda Proxy (index.py)
    ├─ Session Config Extraction
    ├─ Session Attribute Management
    ├─ JSON Validation & Retry
    ├─ Data Accumulation
    └─ Error Handling
    ↓
Bedrock Agent Runtime
    ├─ Session Attributes (Persistence)
    └─ Agent Execution
```

### Data Flow

**First Turn (Initial Message)**:
1. Client sends `{ message: initial_message, sessionConfig: {...} }`
2. Lambda extracts `initial_message` from config
3. Lambda stores config fields (hat, responsibilities, etc.) in session attributes
4. Lambda invokes agent with `inputText: initial_message`
5. Agent executes with session attributes as context
6. Lambda validates agent response JSON
7. Lambda accumulates `current_field` into `data_collected`
8. Lambda returns `{ completion: {...}, data_collected: {...}, invocationId: "..." }`

**Subsequent Turns (Same Session)**:
1. Client sends `{ message: user_response, sessionId: same_id }`
2. Lambda retrieves persisted session attributes from Bedrock
3. Lambda invokes agent with `inputText: user_response` and session attributes
4. Agent continues with accumulated `data_collected` available
5. Lambda validates, accumulates, and returns response

**Function Invocation Turns**:
1. Client receives `invocationId` and function invocations (API calls)
2. Client executes API functions client-side
3. Client sends `{ returnControlInvocationResults: [...], sessionId: same_id }`
4. Lambda transforms client results to Bedrock `apiResult` format
5. Lambda sends results to agent WITHOUT `inputText` (only session state)
6. Agent continues execution with API results
7. Lambda validates and returns next response

## Session Lifecycle and State Management

### Session Initialization

When Lambda receives a session config, it performs these steps:

```python
# 1. Extract initial_message (NOT stored in session attributes)
initial_message = config.get('initial_message')

# 2. Create session attributes with all required fields
session_attributes = {
    'user_id': user_id,                          # From auth token
    'auth_header_name': 'X-Amigo-Auth',          # Fixed header name
    'hat': config['hat'],                        # Session type identifier
    'responsibilities': config['responsibilities'],
    'data_to_be_collected': config['data_to_be_collected'],
    'data_to_be_calculated': config['data_to_be_calculated'],
    'notes': config['notes'],
    'data_collected': {},                        # Empty object initially
    'json_validation_retry_count': 0             # Retry counter
}

# 3. Invoke agent with initial_message as inputText
invoke_agent(
    sessionId=session_id,
    inputText=initial_message,
    sessionState={'sessionAttributes': session_attributes}
)
```

### Session Attributes Structure

All session attributes are stored in Bedrock and automatically persisted across invocations for the same `sessionId`:

```json
{
  "user_id": "user-123",
  "auth_header_name": "X-Amigo-Auth",
  "hat": "onboarding",
  "responsibilities": ["Collect name", "Collect age", ...],
  "data_to_be_collected": ["name", "age", ...],
  "data_to_be_calculated": ["bmr", "tdee", ...],
  "notes": ["Important constraint 1", ...],
  "data_collected": {
    "name": {
      "field": "name",
      "label": "Name",
      "value": "John"
    },
    "age": {
      "field": "age",
      "label": "Age",
      "value": "30"
    },
    "target_weight": {
      "field": "target_weight",
      "label": "Target Weight",
      "value": "75"
    }
  },
  "json_validation_retry_count": 0
}
```

Note: The `label` field is provided by the agent in each `current_field` response and represents the display name for that field (e.g., "Name", "First Name", "Target Weight"). Values are stored in standard units (e.g., kg for weight, cm for height) without unit suffixes. The app handles unit conversion for display based on user preferences.

### Mutable vs Immutable Fields

**Immutable** (set at initialization, never changed):
- `user_id`, `auth_header_name`, `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`

**Mutable** (updated during session):
- `data_collected`: Accumulates field values across turns
- `json_validation_retry_count`: Incremented on validation failures

## Lambda Request/Response Handling

### Request Processing

Lambda receives all requests in a **unified format**. The `sessionConfig` field is optional and only present in the first message of a session.

**Unified Request Format**:
```json
{
  "mode": "agent",
  "message": "user input text",
  "sessionId": "session-123",
  "agentId": "agent-id",
  "agentAliasId": "alias-id",
  "sessionConfig": null,
  "returnControlInvocationResults": null
}
```

**First Message (with Session Config)**:
```json
{
  "mode": "agent",
  "message": "initial_message_text",
  "sessionId": "session-123",
  "agentId": "agent-id",
  "agentAliasId": "alias-id",
  "sessionConfig": {
    "hat": "onboarding",
    "responsibilities": ["Collect name", "Collect age"],
    "data_to_be_collected": ["name", "age"],
    "data_to_be_calculated": ["bmr"],
    "notes": ["Important constraint"],
    "initial_message": "initial_message_text"
  },
  "returnControlInvocationResults": null
}
```

**Subsequent Message (same session)**:
```json
{
  "mode": "agent",
  "message": "user input text",
  "sessionId": "session-123",
  "agentId": "agent-id",
  "agentAliasId": "alias-id",
  "sessionConfig": null,
  "returnControlInvocationResults": null
}
```

**Return Control Request (API results)**:
```json
{
  "mode": "agent",
  "message": null,
  "sessionId": "session-123",
  "agentId": "agent-id",
  "agentAliasId": "alias-id",
  "sessionConfig": null,
  "returnControlInvocationResults": [
    {
      "invocation_id": "inv-123",
      "function_results": [
        {
          "action_group": "health_calculations",
          "function_name": "calculate_bmr",
          "success": true,
          "result": "{\"bmr\": 1500}"
        }
      ]
    }
  ]
}
```

**Unified Request Field Rules**:
- `mode`: Always "agent"
- `message`: User input text, or null for return control requests
- `sessionId`: Session identifier (required)
- `agentId`: Bedrock agent ID (required)
- `agentAliasId`: Bedrock agent alias ID (required)
- `sessionConfig`: Session configuration object (only in first message), null for subsequent messages
- `returnControlInvocationResults`: Function results from client (only for return control requests), null otherwise

**Lambda Processing**:
- If `sessionConfig` is present: Extract `initial_message` and store config in session attributes
- If `returnControlInvocationResults` is present: Transform results to Bedrock format and send to agent
- Otherwise: Send `message` as normal user input to agent

Note: Client sends `function_results` (snake_case). Lambda transforms this to Bedrock's `apiResult` format.

### Response Processing

Lambda processes agent responses in this order:

1. **Parse JSON**: Verify response is valid JSON object
2. **Validate Schema**: Check all required fields and types
3. **Extract current_field**: Get field name, label, value
4. **Accumulate Data**: Add/update field in `data_collected`
5. **Check for Invocations**: Extract function invocations if present
6. **Build Response**: Return structured response to client

### Response Structure

Lambda uses a **unified response format** for all responses (success, error, with/without invocations). All fields are always present in the response structure, with null values when not applicable.

**Unified Response Format**:
```json
{
  "completion": {
    "status_of_aim": "in_progress",
    "ui": {
      "render": {
        "type": "message",
        "text": "What is your name?"
      },
      "tone": "supportive"
    },
    "input": {
      "type": "text"
    },
    "current_field": {
      "field": "name",
      "label": "Name",
      "value": null
    }
  },
  "data_collected": {
    "age": {
      "field": "age",
      "label": "Age",
      "value": 30
    }
  },
  "invocations": [
    {
      "action_group": "health_calculations",
      "function_name": "calculate_bmr",
      "params": {
        "weight": 75,
        "height": 180,
        "age": 30,
        "gender": "M"
      }
    }
  ],
  "invocationId": "inv-123",
  "error": null,
  "userId": "user-123",
  "timestamp": "2025-03-15T10:30:00Z"
}
```

**Field Presence Rules**:
- `completion`: JSON object on success, null on error
- `data_collected`: Object with accumulated fields on success, null on error
- `invocations`: Array of function invocations if present, null otherwise
- `invocationId`: String identifier for the invocation, always present
- `error`: null on success, error message string on failure
- `userId`: Always present (user identifier)
- `timestamp`: Always present (ISO8601 format)

**Response Variations**:

1. **Successful Response (no invocations)**:
   - `completion`: Agent JSON object
   - `data_collected`: Accumulated fields
   - `invocations`: null
   - `error`: null

2. **Successful Response (with invocations)**:
   - `completion`: Agent JSON object
   - `data_collected`: Accumulated fields
   - `invocations`: Array of function invocations
   - `error`: null

3. **Error Response**:
   - `completion`: null
   - `data_collected`: null
   - `invocations`: null
   - `error`: Error message string

**Client Parsing Logic**:
```kotlin
val response = bedrockClient.invokeAgent(...)

// Check for error first
if (response.error != null) {
    // Handle error
    showError(response.error)
    return
}

// Process successful response
val completion = response.completion  // Agent JSON object
val dataCollected = response.data_collected  // Accumulated fields
val invocations = response.invocations  // Function invocations or null

// Display completion to user
displayCompletion(completion)

// Accumulate data
accumulateData(dataCollected)

// Handle invocations if present
if (invocations != null && invocations.isNotEmpty()) {
    executeInvocations(invocations, response.invocationId)
}
```

## JSON Validation and Retry Logic

### Validation Rules

Agent responses must satisfy these rules:

1. **Valid JSON**: Response must parse as JSON object
2. **Required Fields**: Must contain `status_of_aim`, `ui`, `input`, `current_field`
3. **Enum Values**:
   - `status_of_aim`: "not_set" | "in_progress" | "completed"
   - `ui.render.type`: "info" | "message" | "message_with_summary"
   - `input.type`: "text" | "weight" | "date" | "quick_pills" | "yes_no" | "dropdown"
4. **String Constraints**:
   - `ui.render.text`: Non-empty string
   - `current_field.field`: Non-empty string
   - `current_field.label`: Non-empty string
5. **Value Constraints**:
   - `current_field.value`: String or null (not empty string)

### Retry Mechanism

When validation fails:

```python
retry_count = session_attributes.get('json_validation_retry_count', 0)

if retry_count < 3:
    # Increment retry count
    session_attributes['json_validation_retry_count'] = retry_count + 1
    
    # Send retry message to agent
    invoke_agent(
        sessionId=session_id,
        inputText="Please respond with JSON object ONLY",
        sessionState={'sessionAttributes': session_attributes}
    )
else:
    # Max retries exceeded - return error
    return error_response(
        error="Agent failed to respond with valid JSON after 3 retries",
        userId=user_id,
        timestamp=datetime.utcnow().isoformat(),
        invocationId=last_invocation_id
    )
```

**Key Points**:
- Retry count is stored in `session_attributes['json_validation_retry_count']` on Lambda
- Retry count is NOT sent to the client in the response
- Retry count persists in Bedrock session attributes across Lambda invocations
- Retry message is sent as a regular user message (not a system message)
- Retry uses the same `sessionId` (session attributes persist)
- Retry count is incremented in session attributes before retry
- After 3 failed attempts, error response is returned
- New sessions start with retry count = 0
- Client does not need to track or manage retry count

## Data Accumulation Mechanism

### Accumulation Rules

The `data_collected` field accumulates field values across turns in the same format as `current_field`:

```python
# Extract current_field from agent response
current_field = response.get('current_field', {})
field_name = current_field.get('field')
field_label = current_field.get('label')
field_value = current_field.get('value')

# Get current data_collected from session attributes
data_collected = session_attributes.get('data_collected', {})

# Accumulate: store entire current_field object keyed by field name
if field_value is not None:
    data_collected[field_name] = {
        'field': field_name,
        'label': field_label,
        'value': field_value
    }
elif field_name in data_collected:
    # Keep existing entry if new value is null
    pass

# Update session attributes
session_attributes['data_collected'] = data_collected
```

### Accumulation Behavior

- **New Field**: Added to `data_collected` with full `current_field` structure
- **Existing Field**: Entire entry overwritten with new `current_field` object (no append/merge)
- **Null Value**: Existing entry preserved (not deleted)
- **Empty String**: Treated as null (not stored)

### Response Enrichment

Every successful response includes `data_collected` with the accumulated field objects:

```python
response = {
    'completion': agent_response,
    'data_collected': session_attributes['data_collected'],  # Full current_field objects
    'invocationId': invocation_id
}
```

Example response:

```json
{
  "completion": {...},
  "data_collected": {
    "name": {
      "field": "name",
      "label": "Name",
      "value": "John"
    },
    "age": {
      "field": "age",
      "label": "Age",
      "value": "30"
    },
    "target_weight": {
      "field": "target_weight",
      "label": "Target Weight",
      "value": "75"
    }
  },
  "invocationId": "inv-123"
}
```

This allows the client to:
- Display accumulated data with display names (labels) to user
- Convert values to user's preferred units for display (e.g., 75 kg → 165 lbs)
- Maintain session context across turns with full field metadata
- Recover from errors with complete data state including labels

## Error Handling Strategy

### Error Categories

**1. JSON Validation Errors**:
- Invalid JSON syntax
- Missing required fields
- Invalid enum values
- Type mismatches

**Response**: Retry up to 3 times, then return error

**2. Server Errors**:
- Lambda execution errors
- Bedrock API errors
- Unexpected exceptions

**Response**: Return error with descriptive message

**3. Authentication Errors**:
- Invalid or expired token
- Missing authorization header

**Response**: Return 401 error (handled before agent invocation)

### Error Response Format

```json
{
  "error": "Descriptive error message",
  "userId": "user-123",
  "timestamp": "2025-03-15T10:30:00Z",
  "invocationId": "inv-123"
}
```

**Error Response Rules**:
- Must NOT include `completion`, `data_collected`, or `invocations`
- Must include `userId` for logging/debugging
- Must include ISO8601 `timestamp`
- Must include `invocationId` if available
- Must have descriptive `error` message

### Session Preservation

When errors occur, session attributes are preserved:

```python
try:
    # Process request
    response = invoke_agent(...)
except Exception as e:
    # Session attributes remain in Bedrock
    # Client can retry with same sessionId
    return error_response(...)
```

This allows clients to:
- Retry the same request
- Continue with same session
- Recover without data loss

## Client-Side Integration Points

### BedrockClient Updates

The mobile client's `BedrockClient` must be updated to:

1. **Send Session Config**:
   - Extract `initial_message` from config
   - Send config fields in request
   - Use `hat` instead of `cap`

2. **Parse Response Structure**:
   - Extract `completion` (JSON object, not string)
   - Extract `data_collected` (accumulate across turns)
   - Extract `invocationId` (for function results)
   - Check for `error` field

3. **Handle Errors**:
   - Check for `error` field in response
   - Display error to user
   - Stop session on error

4. **Accumulate Data**:
   - Store `data_collected` from each response
   - Maintain session context
   - Display accumulated data to user

5. **Handle Function Invocations**:
   - Extract `invocations` array
   - Execute functions client-side
   - Return results with `invocationId`

### Request Format Changes

**Old Format**:
```json
{
  "mode": "agent",
  "message": "...",
  "cap": "onboarding",
  "sessionContext": "..."
}
```

**New Format**:
```json
{
  "mode": "agent",
  "message": "...",
  "sessionId": "...",
  "agentId": "...",
  "agentAliasId": "..."
}
```

### Response Format Changes

**Old Format**:
```json
{
  "completion": "string",
  "stopReason": "...",
  "usage": {...}
}
```

**New Format**:
```json
{
  "completion": {...},
  "data_collected": {...},
  "invocationId": "...",
  "invocations": [...]
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Session Config Restructuring

For any session config, the `hat` field must be present and non-empty, and `initial_message` must be accessible separately from session attributes.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**

### Property 2: Session Attributes Initialization

For any new session, session attributes must contain all required fields (`user_id`, `auth_header_name`, `hat`, `responsibilities`, `data_to_be_collected`, `data_to_be_calculated`, `notes`, `data_collected`, `json_validation_retry_count`) with correct initial values (`data_collected` as empty object, `json_validation_retry_count` as 0).

**Validates: Requirements 2.1, 2.4, 2.5**

### Property 3: Session Attributes Persistence

For any session with a given `sessionId`, session attributes must persist unchanged across multiple invocations, except for `data_collected` and `json_validation_retry_count` which may be updated.

**Validates: Requirements 2.6, 10.1, 10.2, 10.3**

### Property 4: Initial Message Extraction and Usage

For any session config, the `initial_message` must be extracted and sent as `inputText` in the first agent invocation, while all other config fields must be stored in session attributes.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

### Property 5: Return Control Invocation Format

For any request with `returnControlInvocationResults`, the Lambda invocation must NOT include `inputText` parameter, only session state with results.

**Validates: Requirements 3.6**

### Property 6: Data Accumulation Without Duplication

For any agent response with a `current_field`, the field value must be accumulated into `data_collected` such that existing fields are overwritten (not appended) and new fields are added.

**Validates: Requirements 4.4, 4.5, 4.6, 4.7**

### Property 7: Data Collected Persistence Across Turns

For any multi-turn session, `data_collected` must contain all fields accumulated across all turns with their final values, and must be included in every successful response.

**Validates: Requirements 4.8, 4.9, 7.4**

### Property 8: JSON Response Validation

For any agent response, if it is valid JSON with all required fields and correct types/enums, validation must pass; if any required field is missing or has invalid type/enum, validation must fail.

**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 5.13**

### Property 9: JSON Validation Retry Mechanism

For any validation failure, if retry count is less than 3, the retry count must be incremented and a retry message must be sent to the agent in the same session; if retry count reaches 3, an error response must be returned without further retries.

**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**

### Property 10: Successful Response Structure

For any successful agent response, the Lambda response must contain `completion` (as JSON object), `data_collected`, and `invocationId`, with optional `invocations` array if function calls are present.

**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**

### Property 11: Error Response Structure

For any error condition, the Lambda response must contain `error`, `userId`, `timestamp` (ISO8601), and `invocationId` (if available), and must NOT contain `completion`, `data_collected`, or `invocations`.

**Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7**

### Property 12: Session Attributes Preservation on Error

For any error that occurs during session processing, session attributes must remain persisted in Bedrock for potential recovery.

**Validates: Requirements 8.8**

### Property 13: Agent Response Format

For any agent response, the `status_of_aim` field must use the new format (not legacy `aimofchat.status`), and `current_field` must contain only field name, label, and value without echoing session context.

**Validates: Requirements 9.2, 9.3, 9.4, 9.6**

### Property 14: Client Response Parsing

For any Lambda response received by the client, the BedrockClient must parse the response structure correctly, check for error field, accumulate `data_collected` across turns, and use `invocationId` for subsequent requests.

**Validates: Requirements 11.1, 11.2, 11.4, 11.5**

## Testing Strategy

### Unit Testing Approach

Unit tests verify specific examples, edge cases, and error conditions:

- **Session Config Validation**: Test configs with/without required fields
- **Session Attribute Initialization**: Test initialization with various inputs
- **JSON Validation**: Test valid/invalid responses, missing fields, type mismatches
- **Data Accumulation**: Test new fields, overwriting, null handling
- **Error Responses**: Test error structure, field presence, formatting
- **Retry Logic**: Test retry count increments, max retries, session preservation

### Property-Based Testing Approach

Property-based tests verify universal properties across generated inputs:

- **Session Persistence**: Generate random sessions, verify attributes persist across turns
- **Data Accumulation**: Generate random field sequences, verify no duplication and correct overwriting
- **JSON Validation**: Generate valid/invalid responses, verify validation rules
- **Retry Mechanism**: Generate validation failures, verify retry behavior up to limit
- **Response Structure**: Generate responses, verify structure matches specification
- **Error Handling**: Generate error conditions, verify error responses are correct

### Test Configuration

- Minimum 100 iterations per property test
- Each test tagged with design property reference
- Tag format: `Feature: chat-engine-optimization, Property {number}: {property_text}`
- Both unit and property tests required for comprehensive coverage

## Error Handling

### Lambda Error Handling

```python
try:
    # Validate request
    # Extract session config
    # Initialize session attributes
    # Invoke agent
    # Validate response
    # Accumulate data
    # Return response
except json.JSONDecodeError:
    return error_response("Invalid JSON in request body")
except ValidationError as e:
    return error_response(f"Validation failed: {e}")
except Exception as e:
    return error_response(f"Internal server error: {e}")
```

### Client Error Handling

```kotlin
val response = bedrockClient.invokeAgent(...)
if (response.error != null) {
    // Display error to user
    showError(response.error)
    // Stop session
    stopSession()
} else {
    // Process completion
    // Accumulate data_collected
    // Handle invocations if present
}
```

## Implementation Considerations

### Backward Compatibility

- Migrate from `cap` to `hat` field
- Update SessionConfig data class
- Update all session config implementations
- Update BedrockClient to use new field names

### Performance

- Session attributes stored in Bedrock (automatic persistence)
- No additional database calls needed
- Retry logic adds minimal overhead (max 3 retries)
- Data accumulation is O(1) per field

### Security

- User authentication via Supabase JWT
- Session attributes include `user_id` for audit trail
- Error responses don't leak sensitive data
- Timestamps in ISO8601 format for consistency

### Monitoring

- Log all validation failures
- Track retry counts per session
- Monitor error rates
- Alert on repeated failures for same user

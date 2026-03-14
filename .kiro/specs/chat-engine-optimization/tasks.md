# Implementation Plan: Chat Engine Optimization

## Overview

This implementation plan restructures the Amigo chat engine to improve efficiency and reliability through five core improvements: session configuration restructuring (cap → hat), proper session attribute management in Lambda, JSON validation with 3-retry mechanism, data_collected accumulation without duplication, and comprehensive error handling. The implementation focuses on Lambda proxy updates, client-side integration, and agent instruction alignment.

## Tasks

- [ ] 1. Update session configuration structure
  - [x] 1.1 Update SessionConfig data class to use `hat` field instead of `cap`
    - Modify `SessionConfig.kt` to replace `cap` field with `hat`
    - Add `initial_message` field to SessionConfig
    - Update all SessionConfig implementations (GoalSettingSessionConfig, etc.)
    - _Requirements: 1.1, 1.2, 1.3, 1.5_
  
  - [x] 1.2 Write unit tests for SessionConfig restructuring
    - Test SessionConfig serialization excludes `initial_message`
    - Test `hat` field is required and non-empty
    - Test backward compatibility handling
    - _Requirements: 1.1, 1.2, 1.4_
  
  - [x] 1.3 Write property test for session config restructuring
    - **Property 1: Session Config Restructuring**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
    - Generate random session configs
    - Verify `hat` field is always present and non-empty
    - Verify `initial_message` is accessible separately from session attributes

- [ ] 2. Implement Lambda session attribute management
  - [x] 2.1 Update Lambda to extract and handle initial_message
    - Extract `initial_message` from sessionConfig in request
    - Store all other config fields in session attributes
    - Send `initial_message` as `inputText` on first invocation
    - _Requirements: 3.1, 3.2, 3.4_
  
  - [x] 2.1.1 Write property test for initial message extraction
    - **Property 4: Initial Message Extraction and Usage**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
    - Generate random session configs with initial_message
    - Verify initial_message is extracted correctly
    - Verify initial_message is sent as inputText on first invocation
    - Verify other config fields are stored in session attributes
  
  - [x] 2.2 Implement session attributes initialization
    - Create session attributes structure with all required fields
    - Initialize `data_collected` as empty object `{}`
    - Initialize `json_validation_retry_count` to `0`
    - Include `user_id` and `auth_header_name` in session attributes
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  
  - [x] 2.2.1 Write property test for session attributes initialization
    - **Property 2: Session Attributes Initialization**
    - **Validates: Requirements 2.1, 2.4, 2.5**
    - Generate random new sessions
    - Verify all required fields are present
    - Verify `data_collected` is empty object
    - Verify `json_validation_retry_count` is 0
  
  - [x] 2.3 Implement session attributes persistence handling
    - Retrieve session attributes from Bedrock on subsequent turns
    - Preserve immutable fields across invocations
    - Update only `data_collected` and `json_validation_retry_count`
    - _Requirements: 2.6, 3.5, 10.1, 10.2, 10.3_
  
  - [x] 2.4 Write property test for session attributes persistence
    - **Property 3: Session Attributes Persistence**
    - **Validates: Requirements 2.6, 10.1, 10.2, 10.3**
    - Generate random sessions with multiple turns
    - Verify immutable fields remain unchanged
    - Verify mutable fields update correctly

- [x] 3. Implement JSON validation with retry mechanism
  - [x] 3.1 Create JSON validation function
    - Validate response is parseable JSON object
    - Check all required fields present: `status_of_aim`, `ui`, `input`, `current_field`
    - Validate enum values for `status_of_aim`, `ui.render.type`, `input.type`
    - Validate string constraints (non-empty for text, field, label)
    - Validate `current_field.value` is string or null (not empty string)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 5.13_
  
  - [x] 3.2 Implement retry logic in Lambda
    - Check `json_validation_retry_count` from session attributes
    - Increment retry count on validation failure
    - Send "Please respond with JSON object ONLY" message if count < 3
    - Return error response if retry count reaches 3
    - Reset retry count to 0 for new sessions
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  
  - [x] 3.3 Write unit tests for JSON validation
    - Test valid JSON responses pass validation
    - Test missing required fields fail validation
    - Test invalid enum values fail validation
    - Test type mismatches fail validation
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_
  
  - [x] 3.3.1 Write property test for JSON response validation
    - **Property 8: JSON Response Validation**
    - **Validates: Requirements 5.1-5.13**
    - Generate random agent responses (valid and invalid)
    - Verify valid JSON with correct fields passes validation
    - Verify missing fields or invalid types fail validation
  
  - [x] 3.4 Write property test for retry mechanism
    - **Property 9: JSON Validation Retry Mechanism**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**
    - Generate validation failures
    - Verify retry count increments correctly
    - Verify error returned after 3 retries
    - Verify session preserved during retries

- [x] 4. Checkpoint - Ensure validation and retry logic works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement data_collected accumulation
  - [x] 5.1 Create data accumulation function
    - Extract `current_field` from agent response (field, label, value)
    - Get current `data_collected` from session attributes
    - Store entire `current_field` object keyed by field name
    - Overwrite existing fields (no append/merge)
    - Preserve existing entry if new value is null
    - Update session attributes with accumulated data
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  
  - [x] 5.2 Implement response enrichment with data_collected
    - Include `data_collected` in every successful response
    - Copy accumulated data from session attributes
    - Ensure data_collected contains all fields from all turns
    - _Requirements: 4.8, 4.9, 7.4_
  
  - [x] 5.3 Write unit tests for data accumulation
    - Test new field addition
    - Test existing field overwriting
    - Test null value handling
    - Test empty string treated as null
    - _Requirements: 4.4, 4.5, 4.6, 4.7_
  
  - [x] 5.4 Write property test for data accumulation
    - **Property 6: Data Accumulation Without Duplication**
    - **Validates: Requirements 4.4, 4.5, 4.6, 4.7**
    - Generate random field sequences
    - Verify no duplication occurs
    - Verify overwriting works correctly
    - Verify null handling preserves existing data
  
  - [x] 5.5 Write property test for data collected persistence across turns
    - **Property 7: Data Collected Persistence Across Turns**
    - **Validates: Requirements 4.8, 4.9, 7.4**
    - Generate random multi-turn sessions
    - Verify data_collected contains all fields from all turns
    - Verify data_collected is included in every successful response
    - Verify final values are correct

- [x] 6. Implement unified response structure
  - [x] 6.1 Create response builder for successful responses
    - Build response with `completion` (JSON object, not string)
    - Include `data_collected` from session attributes
    - Include `invocationId` from agent response
    - Include `invocations` array if function calls present
    - Set `error`, `userId`, `timestamp` fields appropriately
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [x] 6.2 Create response builder for error responses
    - Build response with `error` message
    - Include `userId` for logging
    - Include ISO8601 `timestamp`
    - Include `invocationId` if available
    - Set `completion`, `data_collected`, `invocations` to null
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_
  
  - [x] 6.3 Write unit tests for response structure
    - Test successful response structure
    - Test error response structure
    - Test field presence rules
    - Test invocations array handling
    - _Requirements: 7.1, 7.2, 7.3, 8.1, 8.2, 8.3_
  
  - [x] 6.4 Write property test for response structure
    - **Property 10: Successful Response Structure**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**
    - Generate random agent responses
    - Verify response structure matches specification
    - Verify all required fields present
  
  - [x] 6.5 Write property test for error response structure
    - **Property 11: Error Response Structure**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7**
    - Generate random error conditions
    - Verify error response contains required fields
    - Verify error response does NOT contain completion/data_collected/invocations
  
  - [x] 6.6 Write property test for session attributes preservation on error
    - **Property 12: Session Attributes Preservation on Error**
    - **Validates: Requirements 8.8**
    - Generate random errors during session processing
    - Verify session attributes remain persisted in Bedrock
    - Verify client can retry with same sessionId

- [x] 7. Implement return control invocation handling
  - [x] 7.1 Update Lambda to handle returnControlInvocationResults
    - Check for `returnControlInvocationResults` in request
    - Transform client results to Bedrock `apiResult` format
    - Invoke agent WITHOUT `inputText` parameter
    - Send only session state with results
    - _Requirements: 3.6_
  
  - [x] 7.2 Write unit tests for return control handling
    - Test transformation from client format to Bedrock format
    - Test invocation without inputText
    - Test session state preservation
    - _Requirements: 3.6_
  
  - [x] 7.3 Write property test for return control invocation format
    - **Property 5: Return Control Invocation Format**
    - **Validates: Requirements 3.6**
    - Generate random returnControlInvocationResults requests
    - Verify Lambda invocation does NOT include inputText parameter
    - Verify only session state with results is sent

- [x] 8. Update BedrockClient for new response format
  - [x] 8.1 Update BedrockClient to parse unified response structure
    - Parse `completion` as JSON object (not string)
    - Extract `data_collected` and accumulate across turns
    - Extract `invocationId` for subsequent requests
    - Check for `error` field and handle errors
    - Extract `invocations` array if present
    - _Requirements: 11.1, 11.2, 11.4, 11.5, 11.6_
  
  - [x] 8.2 Update BedrockClient to send session config correctly
    - Send `hat` field instead of `cap`
    - Include `initial_message` in sessionConfig
    - Send sessionConfig only on first message
    - Send null for sessionConfig on subsequent messages
    - _Requirements: 1.1, 1.2, 1.5_
  
  - [x] 8.3 Write unit tests for BedrockClient updates
    - Test response parsing for all response types
    - Test error handling
    - Test data_collected accumulation
    - Test session config sending
    - _Requirements: 11.1, 11.2, 11.3, 11.4_
  
  - [x] 8.4 Write property test for client response parsing
    - **Property 14: Client Response Parsing**
    - **Validates: Requirements 11.1, 11.2, 11.4, 11.5**
    - Generate random Lambda responses
    - Verify BedrockClient parses response structure correctly
    - Verify error field is checked
    - Verify data_collected is accumulated across turns
    - Verify invocationId is used for subsequent requests

- [ ] 9. Checkpoint - Ensure Lambda and client integration works
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Implement session completion and data persistence
  - [ ] 10.1 Add session completion detection in Lambda
    - Check if `status_of_aim` is "completed"
    - Mark session as complete when detected
    - _Requirements: 12.1_
  
  - [ ] 10.2 Implement completed session data storage
    - Store final `data_collected` to database with sessionId
    - Include: sessionId, userId, hat, data_collected, completion_timestamp, status_of_aim
    - Use ISO8601 format for completion_timestamp
    - Ensure data is immutable after storage
    - _Requirements: 12.2, 12.3, 12.4, 12.6_
  
  - [ ] 10.3 Implement session closure after completion
    - Mark Bedrock session as closed
    - Return final response with data_collected before closing
    - _Requirements: 12.5, 12.7_
  
  - [ ] 10.4 Write unit tests for session completion
    - Test completion detection
    - Test data storage format
    - Test session closure
    - Test immutability after storage
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_

- [ ] 11. Update client session completion handling
  - [ ] 11.1 Implement session completion detection in client
    - Check for `status_of_aim` = "completed" in response
    - Display final `data_collected` to user
    - Stop sending further messages to same sessionId
    - _Requirements: 13.1, 13.2, 13.3_
  
  - [ ] 11.2 Implement local data persistence on completion
    - Persist final `data_collected` locally
    - Close session UI and return to previous screen
    - _Requirements: 13.4, 13.5_
  
  - [ ] 11.3 Write unit tests for client completion handling
    - Test completion detection
    - Test data display
    - Test session stopping
    - Test local persistence
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_

- [ ] 12. Update agent instruction document alignment
  - [ ] 12.1 Verify agent instruction document uses new format
    - Verify instructions reference `hat` field
    - Verify instructions use `status_of_aim` (not `aimofchat.status`)
    - Verify instructions describe `current_field` format
    - Verify instructions describe session attributes structure
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_
  
  - [ ] 12.2 Write property test for agent response format
    - **Property 13: Agent Response Format**
    - **Validates: Requirements 9.2, 9.3, 9.4, 9.6**
    - Generate random agent responses
    - Verify status_of_aim field uses new format (not aimofchat.status)
    - Verify current_field contains only field, label, value
    - Verify no session context is echoed in responses

- [ ] 13. Final checkpoint - Integration testing
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- The design uses Python for Lambda and Kotlin for mobile client
- Session attributes are automatically persisted by Bedrock across invocations
- Retry count is managed entirely in Lambda, not exposed to client

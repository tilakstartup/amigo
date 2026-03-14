# Task 8.1 Verification Checklist
## Update BedrockClient to parse unified response format

### Implementation Checks
- [x] `LambdaAgentResponse.completion` is `JsonObject?` (not `String`)
- [x] `LambdaAgentResponse.dataCollected` (`data_collected`) is `JsonObject?`
- [x] `LambdaAgentResponse.invocationId` is extracted
- [x] `LambdaAgentResponse.error` field is checked before processing completion
- [x] `LambdaAgentResponse.invocations` array is extracted when present
- [x] `BedrockResponse.completion` is `JsonObject?`
- [x] `BedrockResponse.dataCollected` is `JsonObject?`
- [x] On error response: `completion` and `dataCollected` are null, `error` is set
- [x] On success response: `error` is null, `completion` is non-null JsonObject
- [x] `AmigoAgentConversation` serializes `completion` JsonObject back to string for `parseAgentResponse()`

### Integration Points
- [x] `invokeAgentForCompletion()` handles `bedrockResponse.completion` as JsonObject
- [x] `invokeAgentForCompletion()` serializes completion back to string via `json.encodeToString()`
- [x] Error path in `invokeAgentForCompletion()` returns null when `bedrockResponse.error != null`

### Error Handling
- [x] Lambda error field triggers early return with null completion
- [x] Null completion handled gracefully in `invokeAgentForCompletion()`

### Manual Verification
- [ ] End-to-end: send a message and verify agent response is received correctly
- [ ] Verify logs show "FULL RESPONSE" with JSON object completion

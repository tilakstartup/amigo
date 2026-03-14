# Task 8.2 Verification Checklist
## Update BedrockClient to send session config correctly

### Implementation Checks
- [x] `LambdaRequest` uses `sessionConfig: SessionConfigPayload?` (no `hat` or `sessionContext` fields)
- [x] `SessionConfigPayload` has `hat` field (not `cap`)
- [x] `SessionConfigPayload` includes `initial_message` field
- [x] `AmigoAgentConversation.pendingSessionConfig` is set in `startSession()`
- [x] `pendingSessionConfig` is consumed (set to null) on first `invokeAgentForCompletion()` call
- [x] Subsequent calls send `sessionConfig = null`
- [x] `SessionConfigPayload` serializes `dataToBeCollected` as `data_to_be_collected`
- [x] `SessionConfigPayload` serializes `dataToBeCalculated` as `data_to_be_calculated`

### Integration Points
- [x] `startSession()` builds `SessionConfigPayload` with all required fields
- [x] `invokeAgentForCompletion()` reads and clears `pendingSessionConfig` atomically
- [x] `bedrockClient.invokeAgent()` receives `sessionConfig` on first call, null on subsequent

### Manual Verification
- [ ] First message of a session: verify Lambda receives `session_config` in request body
- [ ] Second message of same session: verify Lambda receives no `session_config`
- [ ] Verify `hat` field is present (not `cap`) in session config sent to Lambda

# Task 8 Verification Checklist
## Update BedrockClient for new response format

### Automated Checks (verified by tests)

#### BedrockClient Response Parsing (8.1)
- [x] `completion` parsed as `JsonObject?` not `String` — `BedrockClientTest.testSuccessResponseHasCompletionAsJsonObject`
- [x] `data_collected` extracted as `JsonObject?` — `BedrockClientTest.testSuccessResponseContainsDataCollected`
- [x] `invocationId` extracted — `BedrockClientTest.testSuccessResponseContainsInvocationId`
- [x] `invocations` array extracted — `BedrockClientTest.testSuccessResponseWithInvocations`
- [x] `error` field checked; `completion` null on error — `BedrockClientTest.testErrorResponseHasNullCompletion`
- [x] `error` null on success — `BedrockClientTest.testSuccessResponseHasNullError`
- [x] `completion` serialized back to string for `parseAgentResponse()` — verified in `AmigoAgentConversation.invokeAgentForCompletion()`

#### Session Config Sending (8.2)
- [x] `SessionConfigPayload` has `hat` field — `BedrockClientTest.testSessionConfigPayloadContainsAllFields`
- [x] `SessionConfigPayload` includes `initial_message` — `BedrockClientTest.testSessionConfigPayloadSerializesCorrectly`
- [x] `SessionConfigPayload` built from `SessionConfig` correctly — `BedrockClientTest.testSessionConfigPayloadFromSessionConfig`
- [x] `sessionConfig` null on subsequent messages — `BedrockClientTest.testSessionConfigPayloadIsNullOnSubsequentMessages`

#### Property 14 Tests (8.4)
- [x] Success response has JsonObject completion and null error — `BedrockClientPropertyTest.property14_successfulResponseHasJsonObjectCompletionAndNullError`
- [x] `data_collected` extracted from success — `BedrockClientPropertyTest.property14_dataCollectedIsExtractedFromSuccessfulResponse`
- [x] `invocationId` present for subsequent requests — `BedrockClientPropertyTest.property14_invocationIdIsPresentForSubsequentRequests`
- [x] Error field checked; completion null on error — `BedrockClientPropertyTest.property14_errorFieldIsCheckedAndCompletionIsNullOnError`
- [x] Response is either success or error, not both — `BedrockClientPropertyTest.property14_responseIsEitherSuccessOrErrorNotBoth`
- [x] `invocationId` present when invocations returned — `BedrockClientPropertyTest.property14_invocationIdIsPresentWhenInvocationsAreReturned`
- [x] `data_collected` accumulates without losing fields — `BedrockClientPropertyTest.property14_dataCollectedAccumulatesAcrossTurnsWithoutLosingFields`
- [x] Session config payload has non-empty hat and initial_message — `BedrockClientPropertyTest.property14_sessionConfigPayloadHasNonEmptyHatAndInitialMessage`
- [x] Null session config valid for subsequent messages — `BedrockClientPropertyTest.property14_nullSessionConfigIsValidForSubsequentMessages`

#### Build
- [x] Shared module compiles for Android and iOS — `./gradlew :shared:assemble` BUILD SUCCESSFUL
- [x] iOS test compilation fixed (backtick names with commas replaced with camelCase)
- [x] Lambda deployed — `AmigoBedrockProxyStack-dev` UPDATE_COMPLETE
- [x] Bedrock agent prepared — status PREPARING

### Manual Verification
- [ ] Start a new onboarding session — verify first message includes `session_config` in Lambda logs
- [ ] Send a second message — verify `session_config` is absent in Lambda logs
- [ ] Verify agent response is received and displayed correctly on Android
- [ ] Verify agent response is received and displayed correctly on iOS
- [ ] Verify `data_collected` accumulates across turns (check Lambda logs)
- [ ] Verify error responses are handled gracefully (no crash)

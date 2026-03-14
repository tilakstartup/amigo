# Task 8.3 Verification Checklist
## Write unit tests for BedrockClient updates

### Test Coverage
- [x] `testSuccessResponseHasCompletionAsJsonObject` — completion is JsonObject on success
- [x] `testSuccessResponseContainsDataCollected` — data_collected extracted correctly
- [x] `testSuccessResponseContainsInvocationId` — invocationId extracted
- [x] `testSuccessResponseWithInvocations` — invocations array parsed
- [x] `testSuccessResponseWithoutInvocations` — null invocations handled
- [x] `testErrorResponseHasErrorField` — error field present on error
- [x] `testErrorResponseHasNullCompletion` — completion null on error
- [x] `testErrorResponsePreservesInvocationId` — invocationId preserved on error
- [x] `testSuccessResponseHasNullError` — error null on success
- [x] `testDataCollectedIsEmptyOnFirstTurn` — empty data_collected on first turn
- [x] `testDataCollectedAccumulatesAcrossTurns` — accumulation across turns
- [x] `testSessionConfigPayloadContainsAllFields` — all fields present
- [x] `testSessionConfigPayloadSerializesCorrectly` — correct JSON serialization
- [x] `testSessionConfigPayloadFromSessionConfig` — built from SessionConfig correctly
- [x] `testSessionConfigPayloadIsNullOnSubsequentMessages` — null on subsequent messages
- [x] `testBedrockResponseDefaultValues` — default values are null
- [x] `testBedrockResponseCompletionIsJsonObject` — completion is JsonObject type

### Test File
- [x] `mobile/shared/src/commonTest/kotlin/com/amigo/shared/ai/BedrockClientTest.kt`

### Automated Verification
- [x] All 16 unit tests pass — verified by `./gradlew :shared:test`

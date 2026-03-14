# Task 8.4 Verification Checklist
## Write property test for client response parsing (Property 14)

### Property Tests Coverage
- [x] `property 14 - successful response has JsonObject completion and null error`
- [x] `property 14 - data_collected is extracted from successful response`
- [x] `property 14 - invocationId is present for subsequent requests`
- [x] `property 14 - error field is checked and completion is null on error`
- [x] `property 14 - response is either success or error, not both`
- [x] `property 14 - invocationId is present when invocations are returned`
- [x] `property 14 - data_collected accumulates across turns without losing fields`
- [x] `property 14 - session config payload has non-empty hat and initial_message`
- [x] `property 14 - null session config is valid for subsequent messages`

### Requirements Validated
- [x] Requirement 11.1: completion parsed as JsonObject
- [x] Requirement 11.2: error field checked; completion null on error
- [x] Requirement 11.4: data_collected extracted and accumulates
- [x] Requirement 11.5: invocationId present for subsequent requests
- [x] Requirement 11.6: invocationId present when invocations returned
- [x] Requirement 1.1/1.2/1.5: SessionConfigPayload hat and initial_message

### Test File
- [x] `mobile/shared/src/commonTest/kotlin/com/amigo/shared/ai/BedrockClientPropertyTest.kt`

### Automated Verification
- [x] All 9 property tests pass — verified by `./gradlew :shared:test`

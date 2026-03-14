# Verification Checklist - Task 6: Implement unified response structure

## Task Overview
Implement unified success and error response builders in Lambda, with comprehensive unit and property tests.

## Subtasks Completed
- [x] 6.1 Create response builder for successful responses
- [x] 6.2 Create response builder for error responses
- [x] 6.3 Write unit tests for response structure
- [x] 6.4 Write property test for response structure (Property 10)
- [x] 6.5 Write property test for error response structure (Property 11)
- [x] 6.6 Write property test for session attributes preservation on error (Property 12)

## Requirements Validated
- Requirement 7.1: error is null on success
- Requirement 7.2: invocations is array when function calls present, null otherwise
- Requirement 7.3: completion is JSON object (dict), not string
- Requirement 7.4: data_collected always present in successful responses
- Requirement 7.5: invocationId always present
- Requirement 8.1–8.7: Error response structure
- Requirement 8.8: Session attributes preserved on error

## Lambda Implementation

### Success Response Builder
- [x] **VERIFIED BY TEST**: `_build_success_response()` implemented
  - Test: `test_response_structure.py::TestBuildSuccessResponse` (10 tests)
- [x] **VERIFIED BY TEST**: completion is dict (JSON object), not string
  - Test: `test_response_structure.py::TestBuildSuccessResponse::test_completion_is_json_object_not_string`
- [x] **VERIFIED BY TEST**: data_collected always present
  - Test: `test_response_structure.py::TestBuildSuccessResponse::test_data_collected_always_present`
- [x] **VERIFIED BY TEST**: invocationId always present
  - Test: `test_response_structure.py::TestBuildSuccessResponse::test_invocation_id_always_present`
- [x] **VERIFIED BY TEST**: error is null on success
  - Test: `test_response_structure.py::TestBuildSuccessResponse::test_error_is_null_on_success`

### Error Response Builder
- [x] **VERIFIED BY TEST**: `_build_error_response()` implemented
  - Test: `test_response_structure.py::TestBuildErrorResponse` (10 tests)
- [x] **VERIFIED BY TEST**: completion/data_collected/invocations are null on error
  - Test: `test_response_structure.py::TestBuildErrorResponse::test_completion_is_null`
- [x] **VERIFIED BY TEST**: error message present and descriptive
  - Test: `test_response_structure.py::TestBuildErrorResponse::test_error_message_present`

### Property Tests
- [x] **VERIFIED**: Property 10 - Successful Response Structure (200 examples)
  - File: `test_response_structure_property.py`
- [x] **VERIFIED**: Property 11 - Error Response Structure (200 examples)
  - File: `test_response_structure_property.py`
- [x] **VERIFIED**: Property 12 - Session Attributes Preservation on Error (200 examples)
  - File: `test_response_structure_property.py`

## Test Results
- 20 unit tests passing (test_response_structure.py)
- 7 property tests passing (test_response_structure_property.py)
- All 80 Lambda tests passing

## Integration Points
- [x] `lambda_handler` uses `_build_success_response()` for all successful agent responses
- [x] `lambda_handler` uses `_build_error_response()` for max-retry errors
- [x] completion text parsed as JSON object before placing in response
- [x] data_collected extracted from session attributes and included in response

## End-to-End Validation (Manual)
- [ ] **BLOCKED**: Task 8 (Update BedrockClient) must be completed first
- [ ] Client receives unified response format with completion as JSON object
- [ ] Client receives data_collected in every successful response
- [ ] Client receives error field when agent fails JSON validation after 3 retries

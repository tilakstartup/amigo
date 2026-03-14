# Verification Checklist - Task 5.2: Implement Response Enrichment with data_collected

## Task Overview
Include data_collected in every successful Lambda response, copying accumulated data from session attributes.

## Requirements Validated
- Requirement 4.8: data_collected in response is a copy of accumulated data from session attributes
- Requirement 4.9: data_collected contains all fields from all turns with final values
- Requirement 7.4: data_collected is included in every successful response

## Lambda Implementation Checks

### Response Builder Updates
- [ ] Lambda response builder includes `data_collected` field in successful responses
- [ ] `data_collected` is extracted from session attributes
- [ ] `data_collected` is parsed from JSON string to object
- [ ] `data_collected` is included alongside `completion` and `invocationId`

### Response Structure
- [x] **VERIFIED BY TEST**: Successful responses contain data_collected field
  - Test: `test_data_collected_persistence_property.py::test_property_data_collected_persistence_across_turns`
- [x] **VERIFIED BY TEST**: data_collected contains all fields accumulated across turns
  - Test: `test_data_collected_persistence_property.py::test_property_data_collected_persistence_across_turns`
- [x] **VERIFIED BY TEST**: data_collected reflects final values for each field
  - Test: `test_data_collected_persistence_property.py::test_property_data_collected_reflects_latest_value`
- [ ] Error responses do NOT include data_collected (set to null)

### Multi-Turn Session Behavior
- [x] **VERIFIED BY TEST**: data_collected grows monotonically (never loses fields)
  - Test: `test_data_collected_persistence_property.py::test_property_data_collected_grows_monotonically`
- [x] **VERIFIED BY TEST**: data_collected is included in every turn's response
  - Test: `test_data_collected_persistence_property.py::test_property_data_collected_persistence_across_turns`
- [x] **VERIFIED BY TEST**: data_collected accumulates correctly across multiple turns
  - Test: `test_data_collected_persistence_property.py::test_property_data_collected_persistence_across_turns`

### Error Handling
- [ ] Missing data_collected in session attributes defaults to empty object
- [ ] Malformed data_collected JSON defaults to empty object
- [ ] Response includes empty object {} if no data has been collected yet

## Integration Points
- [ ] Response enrichment happens after data accumulation
- [ ] Response enrichment happens before returning to client
- [ ] Client receives data_collected in every successful response

## End-to-End Validation (Manual)
- [ ] Start a new conversational session (onboarding or goal setting)
- [ ] Verify first response includes data_collected (empty or with first field)
- [ ] Continue conversation for multiple turns
- [ ] Verify each response includes accumulated data_collected
- [ ] Verify data_collected grows with each field collected
- [ ] Verify final response contains all collected fields

## Notes
- This is a Lambda-only implementation (no mobile client changes)
- All property tests for persistence across turns are passing
- Response enrichment ensures client always has complete session context

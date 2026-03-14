# Task 2.4: Property Test for Session Attributes Persistence

## Overview

Implemented comprehensive property-based tests using Hypothesis to verify session attributes persist correctly across multiple conversation turns, ensuring immutable fields remain unchanged and mutable fields can be updated.

## Requirements Addressed

- **Property 3: Session Attributes Persistence**
- **Requirement 2.6**: Session attributes persist unchanged across invocations (except mutable fields)
- **Requirement 10.1**: Bedrock maintains session attributes for sessionId across invocations
- **Requirement 10.2**: Data persists until session is explicitly closed
- **Requirement 10.3**: Lambda retrieves persisted session attributes on new turns

## Implementation Details

### 1. Property Test Class

Added `TestPropertySessionAttributesPersistence` class with parameterized property test:
- Runs with 2, 3, and 5 turns to test various conversation lengths
- Each variation executes 100 examples (300 total test cases)
- Uses Hypothesis for generating random test data

### 2. Random Data Generation Strategies

**Session Config Strategy**:
- Generates random `hat` values (session type identifiers)
- Generates random lists of responsibilities (1-10 items)
- Generates random data_to_be_collected fields (1-20 items)
- Generates random data_to_be_calculated fields (0-10 items)
- Generates random notes (0-5 items)
- Generates random initial_message text

**Field Data Strategy**:
- Generates random field names (alphanumeric with underscores)
- Generates random labels (display names)
- Generates random values (strings, integers, or null)

### 3. Multi-Turn Session Simulation

The property test simulates a complete multi-turn conversation:

**Turn 1 (Initialization)**:
- Sends request with sessionConfig
- Initializes all session attributes
- Verifies data_collected starts as empty object
- Verifies json_validation_retry_count starts at 0

**Subsequent Turns (2-N)**:
- Sends requests without sessionConfig
- Simulates field data accumulation
- Optionally increments retry count
- Verifies Bedrock's merge behavior

### 4. Bedrock Persistence Simulation

Mock implementation simulates AWS Bedrock's session persistence:
```python
def mock_invoke_agent(**kwargs):
    """Mock invoke_agent that simulates Bedrock's session persistence."""
    # Get incoming session attributes
    incoming_attributes = kwargs['sessionState']['sessionAttributes']
    
    if persisted_session_attributes is None:
        # First call: store all attributes
        persisted_session_attributes = incoming_attributes.copy()
    else:
        # Subsequent calls: merge using _merge_session_attributes
        persisted_session_attributes = _merge_session_attributes(
            persisted_session_attributes,
            incoming_attributes
        )
```

### 5. Verification Logic

**Immutable Fields** (must remain unchanged):
- user_id
- auth_header_name
- hat
- responsibilities
- data_to_be_collected
- data_to_be_calculated
- notes

**Mutable Fields** (can be updated):
- data_collected (accumulates field values)
- json_validation_retry_count (increments on validation failures)

## Test Results

All tests pass successfully:
- ✅ 3 property test variations (2, 3, 5 turns)
- ✅ 100 examples per variation = 300 total test cases
- ✅ All existing tests continue to pass (15 total)
- ✅ No regressions introduced

## Key Design Decisions

1. **Parameterized Turn Counts**: Testing with 2, 3, and 5 turns ensures the persistence logic works for both short and longer conversations.

2. **Hypothesis Integration**: Using Hypothesis provides:
   - Automatic generation of diverse test cases
   - Edge case discovery (empty lists, special characters, etc.)
   - Reproducible failures with example shrinking

3. **Bedrock Simulation**: The mock accurately simulates Bedrock's merge behavior by using the same `_merge_session_attributes` helper function that the Lambda code uses.

4. **Comprehensive Field Coverage**: The test generates all required session config fields to ensure complete coverage of the persistence logic.

## Test Coverage

The property test validates:
- ✅ Session initialization with random configs
- ✅ Multi-turn conversations (2-5 turns)
- ✅ Immutable field preservation across all turns
- ✅ Mutable field updates across turns
- ✅ Data accumulation behavior
- ✅ Retry count increment behavior
- ✅ Bedrock's merge behavior

## Files Modified

### Tests
- `infrastructure/lib/stacks/bedrock-proxy/lambda/tests/test_session_attributes_persistence.py`
  - Added `TestPropertySessionAttributesPersistence` class
  - Added `session_config_strategy()` for generating random configs
  - Added `field_data_strategy()` for generating random field data
  - Added `test_property_immutable_fields_persist_across_turns()` parameterized test
  - Integrated with existing test suite

## Next Steps

- **Task 3.1**: Create JSON validation function
- **Task 3.2**: Implement retry logic in Lambda (will use json_validation_retry_count)
- **Task 5.1**: Implement data accumulation (will use data_collected field)

## Notes

- This task only involves test code (no production code changes)
- Property tests provide broader coverage than unit tests alone
- The test suite now has both unit tests (specific examples) and property tests (universal properties)
- All 300 generated test cases pass, providing high confidence in the persistence logic

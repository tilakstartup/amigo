# Verification Checklist - Task 2.1.1

## Task: Write property test for initial message extraction

**Property 4: Initial Message Extraction and Usage**
**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

---

## Test File Structure

- [x] Test file created at `infrastructure/lib/stacks/bedrock-proxy/lambda/tests/test_initial_message_extraction.py`
- [x] Test dependencies documented in `requirements-test.txt`
- [x] Test README created with instructions
- [x] `__init__.py` file created in tests directory

---

## Property-Based Test Coverage

### Test 1: Initial Message Extraction Property
- [x] Test generates random session configs with initial_message
- [x] Test verifies initial_message is extracted correctly
- [x] Test verifies initial_message is sent as inputText parameter
- [x] Test verifies other config fields stored in session attributes
- [x] Test verifies session attributes do NOT include initial_message
- [x] Test verifies required fields initialized (user_id, auth_header_name, data_collected, json_validation_retry_count)
- [x] Test verifies data_collected initialized as empty object {}
- [x] Test verifies json_validation_retry_count initialized to 0
- [x] Test runs 100 iterations with random inputs

### Test 2: Subsequent Invocation Property
- [x] Test generates random session configs
- [x] Test verifies sessionConfig is null/absent on subsequent messages
- [x] Test verifies inputText is the new user message (not initial_message)
- [x] Test verifies session attributes contain basic fields only
- [x] Test runs 100 iterations with random inputs

### Test 3: Unit Test with Concrete Example
- [x] Test uses specific session config example
- [x] Test verifies initial_message extraction with known values
- [x] Test verifies all session attribute fields match expected values
- [x] Test verifies initial_message not in session attributes

---

## Test Execution

- [x] Tests can be installed with `pip install -r requirements-test.txt`
- [x] Tests can be run with `pytest tests/test_initial_message_extraction.py -v`
- [x] All three test functions are discoverable by pytest
- [x] Tests properly mock Bedrock agent runtime client
- [x] Tests properly mock token verification

---

## Requirements Validation

### Requirement 3.1: Extract initial_message from sessionConfig
- [x] Property test verifies initial_message extraction from config

### Requirement 3.2: Store other config fields in session attributes
- [x] Property test verifies hat, responsibilities, data_to_be_collected, data_to_be_calculated, notes stored in session attributes
- [x] Property test verifies lists are JSON-encoded in session attributes

### Requirement 3.3: Include user_id and auth_header_name in session attributes
- [x] Property test verifies user_id present in session attributes
- [x] Property test verifies auth_header_name present in session attributes

### Requirement 3.4: Send initial_message as inputText on first invocation
- [x] Property test verifies inputText parameter equals initial_message
- [x] Property test verifies invoke_agent called with inputText

---

## Notes

- These tests are written for the expected Lambda implementation
- The current Lambda implementation needs to be updated to handle sessionConfig extraction
- Tests will fail until Lambda implementation matches requirements
- Tests serve as specification for the implementation work

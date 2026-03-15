# Lambda Property Tests

This directory contains property-based tests for the Bedrock proxy Lambda function.

## Setup

Install test dependencies:

```bash
cd infrastructure/lib/stacks/bedrock-proxy/lambda/tests
pip install -r requirements-test.txt
```

## Running Tests

Run all tests:

```bash
cd infrastructure/lib/stacks/bedrock-proxy/lambda
pytest tests/test_initial_message_extraction.py -v
```

Run with coverage:

```bash
cd infrastructure/lib/stacks/bedrock-proxy/lambda
pytest tests/test_initial_message_extraction.py -v --cov=index --cov-report=html
```

Run specific test:

```bash
cd infrastructure/lib/stacks/bedrock-proxy/lambda
pytest tests/test_initial_message_extraction.py::test_initial_message_extraction_property -v
```

## Test Structure

### Property-Based Tests

Property-based tests use Hypothesis to generate random inputs and verify universal properties:

- `test_initial_message_extraction_property`: Verifies Property 4 (Initial Message Extraction and Usage)
  - Generates random session configs with initial_message
  - Verifies initial_message is extracted correctly
  - Verifies initial_message is sent as inputText on first invocation
  - Verifies other config fields are stored in session attributes
  - Verifies session attributes do NOT include initial_message

- `test_subsequent_invocation_without_session_config`: Verifies subsequent invocations
  - Verifies sessionConfig is not sent on subsequent messages
  - Verifies inputText is the new user message (not initial_message)

### Unit Tests

Unit tests verify specific examples:

- `test_initial_message_extraction_unit`: Tests with a concrete example session config

## Property 4: Initial Message Extraction and Usage

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

For any session config with initial_message:
1. initial_message must be extracted correctly from sessionConfig
2. initial_message must be sent as inputText on first invocation
3. Other config fields (hat, responsibilities, data_to_be_collected, data_to_be_calculated, notes) must be stored in session attributes
4. Session attributes must NOT include initial_message
5. Session attributes must include required fields: user_id, auth_header_name, data_collected, json_validation_retry_count
6. data_collected must be initialized as empty object {}
7. json_validation_retry_count must be initialized to 0

## Notes

- Tests currently expect the Lambda implementation to handle sessionConfig extraction
- The current Lambda implementation (index.py) needs to be updated to handle sessionConfig as described in the design
- These tests will fail until the Lambda implementation is updated to match the requirements

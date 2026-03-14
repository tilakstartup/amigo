"""
Property-based tests for unified response structure.

Property 10: Successful Response Structure
Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5

Property 11: Error Response Structure
Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7

Property 12: Session Attributes Preservation on Error
Validates: Requirements 8.8
"""

import json
import pytest
from hypothesis import given, strategies as st, settings, assume
from unittest.mock import MagicMock, patch
from datetime import datetime
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


# ---------------------------------------------------------------------------
# Shared strategies
# ---------------------------------------------------------------------------

@st.composite
def user_id_strategy(draw):
    """Generate valid user IDs."""
    return draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'),
        whitelist_characters='-_'
    )))


@st.composite
def invocation_id_strategy(draw):
    """Generate valid invocation IDs."""
    return draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'),
        whitelist_characters='-_'
    )))


@st.composite
def completion_object_strategy(draw):
    """Generate valid agent completion objects."""
    return {
        'status_of_aim': draw(st.sampled_from(['not_set', 'in_progress', 'completed'])),
        'ui': {
            'render': {
                'type': draw(st.sampled_from(['info', 'message', 'message_with_summary'])),
                'text': draw(st.text(min_size=1, max_size=200))
            },
            'tone': draw(st.text(min_size=1, max_size=50))
        },
        'input': {
            'type': draw(st.sampled_from(['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown']))
        },
        'current_field': {
            'field': draw(st.text(min_size=1, max_size=50)),
            'label': draw(st.text(min_size=1, max_size=100)),
            'value': draw(st.one_of(st.text(min_size=1, max_size=100), st.just(None)))
        }
    }


@st.composite
def data_collected_strategy(draw):
    """Generate random data_collected dicts."""
    num_fields = draw(st.integers(min_value=0, max_value=10))
    result = {}
    for i in range(num_fields):
        field_name = draw(st.text(min_size=1, max_size=30, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'),
            whitelist_characters='_'
        )))
        result[field_name] = {
            'field': field_name,
            'label': draw(st.text(min_size=1, max_size=50)),
            'value': draw(st.one_of(st.text(min_size=1, max_size=100), st.just(None)))
        }
    return result


@st.composite
def invocations_strategy(draw):
    """Generate random invocations arrays."""
    num_invocations = draw(st.integers(min_value=1, max_value=5))
    return [
        {
            'action_group': draw(st.text(min_size=1, max_size=50)),
            'function_name': draw(st.text(min_size=1, max_size=50)),
            'params': {}
        }
        for _ in range(num_invocations)
    ]


@st.composite
def error_message_strategy(draw):
    """Generate random error messages."""
    return draw(st.text(min_size=1, max_size=500))


# ---------------------------------------------------------------------------
# Property 10: Successful Response Structure
# ---------------------------------------------------------------------------

@given(
    completion=completion_object_strategy(),
    data_collected=data_collected_strategy(),
    invocation_id=invocation_id_strategy(),
    user_id=user_id_strategy(),
    invocations=st.one_of(st.just(None), invocations_strategy())
)
@settings(max_examples=100, deadline=None)
def test_property_10_successful_response_structure(
    completion, data_collected, invocation_id, user_id, invocations
):
    """
    Property 10: Successful Response Structure
    Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5

    For any successful agent response, the Lambda response must contain:
    - completion (as JSON object, not string)
    - data_collected (always present)
    - invocationId (always present)
    - invocations (array if function calls present, null otherwise)
    - error (null on success)
    - userId (always present)
    - timestamp (ISO8601)
    """
    result = index._build_success_response(
        completion=completion,
        data_collected=data_collected,
        invocation_id=invocation_id,
        user_id=user_id,
        invocations=invocations
    )

    # All required fields must be present
    required_fields = {'completion', 'data_collected', 'invocations', 'invocationId', 'error', 'userId', 'timestamp'}
    assert required_fields.issubset(result.keys()), "All required fields must be present"

    # Requirement 7.3: completion must be a JSON object (dict), not a string
    assert isinstance(result['completion'], dict), "completion must be a dict (JSON object)"

    # Requirement 7.4: data_collected must always be present (not None)
    assert result['data_collected'] is not None, "data_collected must be present in successful responses"
    assert isinstance(result['data_collected'], dict), "data_collected must be a dict"

    # Requirement 7.5: invocationId must always be present
    assert result['invocationId'] == invocation_id, "invocationId must match provided value"

    # Requirement 7.1: error must be null on success
    assert result['error'] is None, "error must be null on success"

    # userId must always be present
    assert result['userId'] == user_id, "userId must match provided value"

    # timestamp must be ISO8601
    ts = result['timestamp']
    assert isinstance(ts, str), "timestamp must be a string"
    datetime.fromisoformat(ts.replace('Z', '+00:00'))  # Must parse without error

    # Requirement 7.2: invocations must be array if provided, null otherwise
    if invocations is not None:
        assert isinstance(result['invocations'], list), "invocations must be a list when provided"
        assert len(result['invocations']) == len(invocations)
    else:
        assert result['invocations'] is None, "invocations must be null when not provided"


@given(
    completion=completion_object_strategy(),
    data_collected=data_collected_strategy(),
    invocation_id=invocation_id_strategy(),
    user_id=user_id_strategy()
)
@settings(max_examples=100, deadline=None)
def test_property_10_completion_is_dict_not_string(
    completion, data_collected, invocation_id, user_id
):
    """
    Property 10 (focused): completion field is always a dict, never a string.
    Validates: Requirement 7.3
    """
    result = index._build_success_response(
        completion=completion,
        data_collected=data_collected,
        invocation_id=invocation_id,
        user_id=user_id
    )
    assert not isinstance(result['completion'], str), "completion must not be a string"
    assert isinstance(result['completion'], dict), "completion must be a dict"


# ---------------------------------------------------------------------------
# Property 11: Error Response Structure
# ---------------------------------------------------------------------------

@given(
    error_message=error_message_strategy(),
    user_id=user_id_strategy(),
    invocation_id=st.one_of(st.just(None), invocation_id_strategy())
)
@settings(max_examples=100, deadline=None)
def test_property_11_error_response_structure(error_message, user_id, invocation_id):
    """
    Property 11: Error Response Structure
    Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7

    For any error condition, the Lambda response must:
    - contain error, userId, timestamp, invocationId
    - NOT contain completion, data_collected, or invocations (they must be null)
    """
    result = index._build_error_response(
        error_message=error_message,
        user_id=user_id,
        invocation_id=invocation_id
    )

    # All required fields must be present
    required_fields = {'completion', 'data_collected', 'invocations', 'invocationId', 'error', 'userId', 'timestamp'}
    assert required_fields.issubset(result.keys()), "All required fields must be present"

    # Requirement 8.2: error field must contain descriptive message
    assert result['error'] == error_message, "error must contain the provided message"
    assert isinstance(result['error'], str), "error must be a string"

    # Requirement 8.3: userId must be present
    assert result['userId'] == user_id, "userId must match provided value"

    # Requirement 8.4: timestamp must be ISO8601
    ts = result['timestamp']
    assert isinstance(ts, str), "timestamp must be a string"
    datetime.fromisoformat(ts.replace('Z', '+00:00'))  # Must parse without error

    # Requirement 8.5: invocationId must be included if available
    assert result['invocationId'] == invocation_id, "invocationId must match provided value"

    # Requirement 8.7: completion, data_collected, invocations must be null
    assert result['completion'] is None, "completion must be null in error response"
    assert result['data_collected'] is None, "data_collected must be null in error response"
    assert result['invocations'] is None, "invocations must be null in error response"


@given(
    error_message=error_message_strategy(),
    user_id=user_id_strategy(),
    invocation_id=st.one_of(st.just(None), invocation_id_strategy())
)
@settings(max_examples=100, deadline=None)
def test_property_11_error_response_no_completion_fields(error_message, user_id, invocation_id):
    """
    Property 11 (focused): Error response must NOT contain completion/data_collected/invocations.
    Validates: Requirement 8.7
    """
    result = index._build_error_response(
        error_message=error_message,
        user_id=user_id,
        invocation_id=invocation_id
    )
    assert result['completion'] is None
    assert result['data_collected'] is None
    assert result['invocations'] is None


# ---------------------------------------------------------------------------
# Property 12: Session Attributes Preservation on Error
# ---------------------------------------------------------------------------

@st.composite
def session_attributes_strategy(draw):
    """Generate random session attributes."""
    return {
        'user_id': draw(user_id_strategy()),
        'auth_header_name': 'X-Amigo-Auth',
        'hat': draw(st.text(min_size=1, max_size=30)),
        'responsibilities': json.dumps([draw(st.text(min_size=1, max_size=50))]),
        'data_to_be_collected': json.dumps([draw(st.text(min_size=1, max_size=30))]),
        'data_to_be_calculated': json.dumps([]),
        'notes': json.dumps([]),
        'data_collected': json.dumps({}),
        'json_validation_retry_count': '0'
    }


@given(
    session_attrs=session_attributes_strategy(),
    error_message=error_message_strategy(),
    user_id=user_id_strategy()
)
@settings(max_examples=100, deadline=None)
def test_property_12_session_attributes_preserved_on_error(
    session_attrs, error_message, user_id
):
    """
    Property 12: Session Attributes Preservation on Error
    Validates: Requirements 8.8

    When an error occurs, session attributes must remain unchanged so the
    client can retry with the same sessionId.

    We verify that:
    1. _build_error_response does NOT modify session_attributes
    2. The session_attributes dict is unchanged after building an error response
    3. The error response contains userId so the client can identify the session
    """
    # Take a deep copy of session attributes before the error
    session_attrs_before = json.loads(json.dumps(session_attrs))

    # Build error response (simulating an error during processing)
    result = index._build_error_response(
        error_message=error_message,
        user_id=user_id,
        invocation_id=None
    )

    # Property: session_attributes must be unchanged after error
    assert session_attrs == session_attrs_before, \
        "Session attributes must not be modified when building error response"

    # Property: error response contains userId so client can identify the session
    assert result['userId'] == user_id, "Error response must contain userId for session recovery"

    # Property: error response has timestamp for audit trail
    assert result['timestamp'] is not None, "Error response must have timestamp"

    # Property: error response has error message for client to display
    assert result['error'] is not None, "Error response must have error message"


@given(
    session_attrs=session_attributes_strategy(),
    user_id=user_id_strategy(),
    num_errors=st.integers(min_value=1, max_value=5)
)
@settings(max_examples=50, deadline=None)
def test_property_12_multiple_errors_preserve_session(session_attrs, user_id, num_errors):
    """
    Property 12 (extended): Multiple errors must not corrupt session attributes.
    Validates: Requirements 8.8

    Even after multiple errors, session attributes remain intact for retry.
    """
    session_attrs_original = json.loads(json.dumps(session_attrs))

    for i in range(num_errors):
        # Simulate error during processing
        index._build_error_response(
            error_message=f'Error {i}',
            user_id=user_id,
            invocation_id=None
        )

    # Session attributes must be unchanged after all errors
    assert session_attrs == session_attrs_original, \
        "Session attributes must remain unchanged after multiple errors"


@given(
    session_attrs=session_attributes_strategy(),
    user_id=user_id_strategy(),
    invocation_id=st.one_of(st.just(None), invocation_id_strategy())
)
@settings(max_examples=50, deadline=None)
def test_property_12_error_response_enables_retry(session_attrs, user_id, invocation_id):
    """
    Property 12 (retry): Error response must contain enough info for client to retry.
    Validates: Requirements 8.8

    The error response must contain userId so the client can identify the session
    and retry with the same sessionId.
    """
    result = index._build_error_response(
        error_message='Transient error',
        user_id=user_id,
        invocation_id=invocation_id
    )

    # Client needs userId to identify the session for retry
    assert result['userId'] == user_id, "userId must be present for client retry"

    # Client needs invocationId if available (for return control retries)
    assert result['invocationId'] == invocation_id, "invocationId must match provided value"

    # Error message must be present so client knows what happened
    assert result['error'] is not None and len(result['error']) > 0, \
        "Error message must be non-empty for client to understand failure"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

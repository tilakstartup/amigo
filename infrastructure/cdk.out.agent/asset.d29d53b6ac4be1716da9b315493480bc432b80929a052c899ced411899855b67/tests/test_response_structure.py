"""
Unit tests for unified response structure.

Tests for Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7:
- Test successful response structure
- Test error response structure
- Test field presence rules
- Test invocations array handling
"""

import json
import pytest
import sys
import os
from datetime import datetime, timezone

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_data_collected():
    return {
        'name': {'field': 'name', 'label': 'Name', 'value': 'Alice'},
        'age': {'field': 'age', 'label': 'Age', 'value': '30'},
    }


def _make_completion():
    return {
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {'type': 'message', 'text': 'What is your age?'},
            'tone': 'supportive'
        },
        'input': {'type': 'text'},
        'previous_field_collected': {'field': 'age', 'label': 'Age', 'value': None}
    }


# ---------------------------------------------------------------------------
# 6.1 – Successful response builder
# ---------------------------------------------------------------------------

class TestBuildSuccessResponse:
    """Tests for _build_success_response (Requirements 7.1–7.5)."""

    def test_completion_is_json_object_not_string(self):
        """Requirement 7.3: completion field must be a JSON object, not a string."""
        completion = _make_completion()
        result = index._build_success_response(
            completion=completion,
            data_collected={},
            invocation_id='inv-001',
            user_id='user-123'
        )
        assert isinstance(result['completion'], dict), "completion must be a dict (JSON object)"

    def test_data_collected_always_present(self):
        """Requirement 7.4: data_collected must always be present in successful responses."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected=_make_data_collected(),
            invocation_id='inv-001',
            user_id='user-123'
        )
        assert 'data_collected' in result
        assert result['data_collected'] is not None

    def test_invocation_id_always_present(self):
        """Requirement 7.5: invocationId must be present in all responses."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected={},
            invocation_id='inv-abc',
            user_id='user-123'
        )
        assert result['invocationId'] == 'inv-abc'

    def test_error_is_null_on_success(self):
        """Requirement 7.1: error field must be null on success."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected={},
            invocation_id='inv-001',
            user_id='user-123'
        )
        assert result['error'] is None

    def test_user_id_always_present(self):
        """userId must always be present."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected={},
            invocation_id='inv-001',
            user_id='user-xyz'
        )
        assert result['userId'] == 'user-xyz'

    def test_timestamp_is_iso8601(self):
        """timestamp must be ISO8601 formatted."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected={},
            invocation_id='inv-001',
            user_id='user-123'
        )
        ts = result['timestamp']
        assert isinstance(ts, str)
        # Should parse without error
        datetime.fromisoformat(ts.replace('Z', '+00:00'))

    def test_invocations_null_when_not_provided(self):
        """invocations must be null when no function calls are present."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected={},
            invocation_id='inv-001',
            user_id='user-123'
        )
        assert result['invocations'] is None

    def test_invocations_array_when_provided(self):
        """Requirement 7.2: invocations must be an array when function calls are present."""
        invocations = [
            {'action_group': 'health_calculations', 'function_name': 'calculate_bmr', 'params': {}}
        ]
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected={},
            invocation_id='inv-001',
            user_id='user-123',
            invocations=invocations
        )
        assert isinstance(result['invocations'], list)
        assert len(result['invocations']) == 1

    def test_all_required_fields_present(self):
        """All required fields must be present in successful response."""
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected=_make_data_collected(),
            invocation_id='inv-001',
            user_id='user-123'
        )
        required_fields = {'completion', 'data_collected', 'invocations', 'invocationId', 'error', 'userId', 'timestamp'}
        assert required_fields.issubset(result.keys())

    def test_data_collected_content_preserved(self):
        """data_collected content must match what was passed in."""
        dc = _make_data_collected()
        result = index._build_success_response(
            completion=_make_completion(),
            data_collected=dc,
            invocation_id='inv-001',
            user_id='user-123'
        )
        assert result['data_collected'] == dc


# ---------------------------------------------------------------------------
# 6.2 – Error response builder
# ---------------------------------------------------------------------------

class TestBuildErrorResponse:
    """Tests for _build_error_response (Requirements 8.1–8.7)."""

    def test_error_message_present(self):
        """Requirement 8.2: error field must contain a descriptive message."""
        result = index._build_error_response(
            error_message='Something went wrong',
            user_id='user-123'
        )
        assert result['error'] == 'Something went wrong'

    def test_user_id_present(self):
        """Requirement 8.3: userId must be present in error response."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-abc'
        )
        assert result['userId'] == 'user-abc'

    def test_timestamp_is_iso8601(self):
        """Requirement 8.4: timestamp must be ISO8601 formatted."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123'
        )
        ts = result['timestamp']
        assert isinstance(ts, str)
        datetime.fromisoformat(ts.replace('Z', '+00:00'))

    def test_invocation_id_included_when_available(self):
        """Requirement 8.5: invocationId must be included if available."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123',
            invocation_id='inv-999'
        )
        assert result['invocationId'] == 'inv-999'

    def test_invocation_id_none_when_not_available(self):
        """invocationId must be None when not available."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123'
        )
        assert result['invocationId'] is None

    def test_completion_is_null(self):
        """Requirement 8.7: completion must NOT be present (null) in error response."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123'
        )
        assert result['completion'] is None

    def test_data_collected_is_null(self):
        """Requirement 8.7: data_collected must NOT be present (null) in error response."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123'
        )
        assert result['data_collected'] is None

    def test_invocations_is_null(self):
        """Requirement 8.7: invocations must NOT be present (null) in error response."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123'
        )
        assert result['invocations'] is None

    def test_all_required_fields_present(self):
        """All required fields must be present in error response."""
        result = index._build_error_response(
            error_message='Error',
            user_id='user-123',
            invocation_id='inv-001'
        )
        required_fields = {'completion', 'data_collected', 'invocations', 'invocationId', 'error', 'userId', 'timestamp'}
        assert required_fields.issubset(result.keys())

    def test_json_validation_error_message(self):
        """Requirement 8.1: JSON validation failure error message."""
        result = index._build_error_response(
            error_message='Agent failed to respond with valid JSON after 3 retries',
            user_id='user-123',
            invocation_id='inv-001'
        )
        assert 'Agent failed to respond with valid JSON after 3 retries' in result['error']


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

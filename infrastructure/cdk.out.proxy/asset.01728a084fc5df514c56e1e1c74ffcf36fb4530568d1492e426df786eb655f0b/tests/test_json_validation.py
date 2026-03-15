"""
Unit tests for JSON validation function.

Tests validation rules for agent responses:
- Valid JSON parsing
- Required fields presence
- Enum value validation
- Type constraints
- String constraints

Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
"""

import json
import pytest
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


def _valid_response(**overrides):
    """Build a valid agent response dict, with optional field overrides."""
    base = {
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {'type': 'message', 'text': 'What is your name?'},
            'tone': 'supportive'
        },
        'input': {'type': 'text'},
        'previous_field_collected': {'field': 'name', 'label': 'Name', 'value': None}
    }
    base.update(overrides)
    return base


def test_valid_json_response_passes():
    """Test that a valid JSON response passes validation."""
    is_valid, error_message, parsed = index._validate_json_response(
        json.dumps(_valid_response())
    )
    assert is_valid is True
    assert error_message is None
    assert parsed is not None
    assert parsed['status_of_aim'] == 'in_progress'


def test_invalid_json_fails():
    """Test that invalid JSON fails validation (Requirement 5.1)."""
    is_valid, error_message, parsed = index._validate_json_response("not a json object")
    assert is_valid is False
    assert "Invalid JSON" in error_message
    assert parsed is None


def test_missing_required_field_fails():
    """Test that missing required fields fail validation (Requirement 5.2)."""
    # Missing 'input' field
    r = _valid_response()
    del r['input']
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "Missing required field: input" in error_message


def test_invalid_status_of_aim_enum_fails():
    """Test that invalid status_of_aim enum value fails (Requirement 5.3)."""
    r = _valid_response(status_of_aim='invalid_status')
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "Invalid status_of_aim value" in error_message


def test_invalid_render_type_enum_fails():
    """Test that invalid ui.render.type enum value fails (Requirement 5.6)."""
    r = _valid_response()
    r['ui']['render']['type'] = 'invalid_type'
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "Invalid ui.render.type value" in error_message


def test_empty_render_text_fails():
    """Test that empty ui.render.text fails validation (Requirement 5.7)."""
    r = _valid_response()
    r['ui']['render']['text'] = ''
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "ui.render.text must be a non-empty string" in error_message


def test_invalid_input_type_enum_fails():
    """Test that invalid input.type enum value fails (Requirement 5.9)."""
    r = _valid_response()
    r['input']['type'] = 'invalid_input_type'
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "Invalid input.type value" in error_message


def test_empty_previous_field_collected_field_fails():
    """Test that empty previous_field_collected.field fails (Requirement 5.11)."""
    r = _valid_response()
    r['previous_field_collected'] = {'field': '', 'label': 'Test', 'value': None}
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "previous_field_collected.field must be a non-empty string" in error_message


def test_empty_previous_field_collected_label_fails():
    """Test that empty previous_field_collected.label fails (Requirement 5.12)."""
    r = _valid_response()
    r['previous_field_collected'] = {'field': 'test', 'label': '', 'value': None}
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "previous_field_collected.label must be a non-empty string" in error_message


def test_empty_string_value_fails():
    """Test that empty string value fails (Requirement 5.13)."""
    r = _valid_response()
    r['previous_field_collected'] = {'field': 'test', 'label': 'Test', 'value': ''}
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is False
    assert "previous_field_collected.value cannot be an empty string" in error_message


def test_valid_string_value_passes():
    """Test that non-empty string value passes (Requirement 5.13)."""
    r = _valid_response()
    r['previous_field_collected'] = {'field': 'test', 'label': 'Test', 'value': 'John'}
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is True
    assert error_message is None


def test_null_value_passes():
    """Test that null value passes (Requirement 5.13)."""
    r = _valid_response()
    r['previous_field_collected'] = {'field': 'test', 'label': 'Test', 'value': None}
    is_valid, error_message, parsed = index._validate_json_response(json.dumps(r))
    assert is_valid is True
    assert error_message is None


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

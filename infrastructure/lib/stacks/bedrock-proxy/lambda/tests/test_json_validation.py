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


def test_valid_json_response_passes():
    """Test that a valid JSON response passes validation."""
    valid_response = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'What is your name?'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'name',
            'label': 'Name',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(valid_response)
    
    assert is_valid is True
    assert error_message is None
    assert parsed is not None
    assert parsed['status_of_aim'] == 'in_progress'


def test_invalid_json_fails():
    """Test that invalid JSON fails validation (Requirement 5.1)."""
    invalid_json = "not a json object"
    
    is_valid, error_message, parsed = index._validate_json_response(invalid_json)
    
    assert is_valid is False
    assert "Invalid JSON" in error_message
    assert parsed is None


def test_missing_required_field_fails():
    """Test that missing required fields fail validation (Requirement 5.2)."""
    # Missing 'input' field
    incomplete_response = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(incomplete_response)
    
    assert is_valid is False
    assert "Missing required field: input" in error_message


def test_invalid_status_of_aim_enum_fails():
    """Test that invalid status_of_aim enum value fails (Requirement 5.3)."""
    invalid_status = json.dumps({
        'status_of_aim': 'invalid_status',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(invalid_status)
    
    assert is_valid is False
    assert "Invalid status_of_aim value" in error_message


def test_invalid_render_type_enum_fails():
    """Test that invalid ui.render.type enum value fails (Requirement 5.6)."""
    invalid_render_type = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'invalid_type',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(invalid_render_type)
    
    assert is_valid is False
    assert "Invalid ui.render.type value" in error_message


def test_empty_render_text_fails():
    """Test that empty ui.render.text fails validation (Requirement 5.7)."""
    empty_text = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': ''
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(empty_text)
    
    assert is_valid is False
    assert "ui.render.text must be a non-empty string" in error_message


def test_invalid_input_type_enum_fails():
    """Test that invalid input.type enum value fails (Requirement 5.9)."""
    invalid_input_type = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'invalid_input_type'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(invalid_input_type)
    
    assert is_valid is False
    assert "Invalid input.type value" in error_message


def test_empty_current_field_field_fails():
    """Test that empty current_field.field fails (Requirement 5.11)."""
    empty_field = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': '',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(empty_field)
    
    assert is_valid is False
    assert "current_field.field must be a non-empty string" in error_message


def test_empty_current_field_label_fails():
    """Test that empty current_field.label fails (Requirement 5.12)."""
    empty_label = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': '',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(empty_label)
    
    assert is_valid is False
    assert "current_field.label must be a non-empty string" in error_message


def test_empty_string_value_fails():
    """Test that empty string value fails (Requirement 5.13)."""
    empty_value = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': ''
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(empty_value)
    
    assert is_valid is False
    assert "current_field.value cannot be an empty string" in error_message


def test_valid_string_value_passes():
    """Test that non-empty string value passes (Requirement 5.13)."""
    valid_value = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': 'John'
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(valid_value)
    
    assert is_valid is True
    assert error_message is None


def test_null_value_passes():
    """Test that null value passes (Requirement 5.13)."""
    null_value = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {
                'type': 'message',
                'text': 'Test'
            },
            'tone': 'supportive'
        },
        'input': {
            'type': 'text'
        },
        'current_field': {
            'field': 'test',
            'label': 'Test',
            'value': None
        }
    })
    
    is_valid, error_message, parsed = index._validate_json_response(null_value)
    
    assert is_valid is True
    assert error_message is None


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

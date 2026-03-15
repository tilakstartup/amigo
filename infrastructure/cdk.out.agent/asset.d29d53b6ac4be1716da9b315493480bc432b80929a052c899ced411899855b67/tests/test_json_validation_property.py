"""
Property-based tests for JSON response validation.

Property 8: JSON Response Validation
Validates: Requirements 5.1-5.13

For any agent response, if it is valid JSON with all required fields and correct
types/enums, validation must pass; if any required field is missing or has invalid
type/enum, validation must fail.
"""

import json
import pytest
from hypothesis import given, strategies as st, settings, assume
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


# Strategy for generating valid status_of_aim values
status_of_aim_strategy = st.sampled_from(['not_set', 'in_progress', 'completed'])

# Strategy for generating valid render types
render_type_strategy = st.sampled_from(['info', 'message', 'message_with_summary'])

# Strategy for generating valid input types
input_type_strategy = st.sampled_from(['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown'])

# Strategy for generating non-empty strings
non_empty_string_strategy = st.text(min_size=1, max_size=200)

# Strategy for generating field names (alphanumeric + underscore)
field_name_strategy = st.text(
    min_size=1,
    max_size=50,
    alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_')
)

# Strategy for generating valid field values (string or null, not empty string)
@st.composite
def field_value_strategy(draw):
    """Generate valid field values: non-empty string or null."""
    is_null = draw(st.booleans())
    if is_null:
        return None
    else:
        return draw(st.text(min_size=1, max_size=100))


@st.composite
def valid_agent_response_strategy(draw):
    """Generate valid agent responses."""
    return {
        'status_of_aim': draw(status_of_aim_strategy),
        'ui': {
            'render': {
                'type': draw(render_type_strategy),
                'text': draw(non_empty_string_strategy)
            },
            'tone': draw(st.text(min_size=1, max_size=50))
        },
        'input': {
            'type': draw(input_type_strategy)
        },
        'previous_field_collected': {
            'field': draw(field_name_strategy),
            'label': draw(non_empty_string_strategy),
            'value': draw(field_value_strategy())
        }
    }


@given(response=valid_agent_response_strategy())
@settings(max_examples=100, deadline=None)
def test_property_valid_json_responses_pass_validation(response):
    """
    Property 8: JSON Response Validation (Valid Case)
    
    For any valid agent response with all required fields and correct types/enums,
    validation must pass.
    """
    response_json = json.dumps(response)
    
    is_valid, error_message, parsed = index._validate_json_response(response_json)
    
    # Property: Valid responses must pass validation
    assert is_valid is True, f"Valid response failed validation: {error_message}"
    assert error_message is None
    assert parsed is not None
    assert parsed == response


@st.composite
def invalid_agent_response_strategy(draw):
    """Generate invalid agent responses with various issues."""
    issue_type = draw(st.sampled_from([
        'missing_field',
        'invalid_status_enum',
        'invalid_render_type_enum',
        'invalid_input_type_enum',
        'empty_render_text',
        'empty_field_name',
        'empty_field_label',
        'empty_string_value'
    ]))
    
    # Start with a valid response
    response = {
        'status_of_aim': draw(status_of_aim_strategy),
        'ui': {
            'render': {
                'type': draw(render_type_strategy),
                'text': draw(non_empty_string_strategy)
            },
            'tone': draw(st.text(min_size=1, max_size=50))
        },
        'input': {
            'type': draw(input_type_strategy)
        },
        'previous_field_collected': {
            'field': draw(field_name_strategy),
            'label': draw(non_empty_string_strategy),
            'value': draw(field_value_strategy())
        }
    }
    
    # Introduce an issue based on issue_type
    if issue_type == 'missing_field':
        field_to_remove = draw(st.sampled_from(['status_of_aim', 'ui', 'input', 'previous_field_collected']))
        del response[field_to_remove]
    elif issue_type == 'invalid_status_enum':
        response['status_of_aim'] = draw(st.text(min_size=1, max_size=50).filter(
            lambda x: x not in ['not_set', 'in_progress', 'completed']
        ))
    elif issue_type == 'invalid_render_type_enum':
        response['ui']['render']['type'] = draw(st.text(min_size=1, max_size=50).filter(
            lambda x: x not in ['info', 'message', 'message_with_summary']
        ))
    elif issue_type == 'invalid_input_type_enum':
        response['input']['type'] = draw(st.text(min_size=1, max_size=50).filter(
            lambda x: x not in ['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown']
        ))
    elif issue_type == 'empty_render_text':
        response['ui']['render']['text'] = ''
    elif issue_type == 'empty_field_name':
        response['previous_field_collected']['field'] = ''
    elif issue_type == 'empty_field_label':
        response['previous_field_collected']['label'] = ''
    elif issue_type == 'empty_string_value':
        response['previous_field_collected']['value'] = ''
    
    return response, issue_type


@given(response_data=invalid_agent_response_strategy())
@settings(max_examples=100, deadline=None)
def test_property_invalid_json_responses_fail_validation(response_data):
    """
    Property 8: JSON Response Validation (Invalid Case)
    
    For any agent response with missing fields or invalid types/enums,
    validation must fail.
    """
    response, issue_type = response_data
    response_json = json.dumps(response)
    
    is_valid, error_message, parsed = index._validate_json_response(response_json)
    
    # Property: Invalid responses must fail validation
    assert is_valid is False, f"Invalid response ({issue_type}) passed validation"
    assert error_message is not None, f"No error message for invalid response ({issue_type})"


def test_property_non_json_text_fails():
    """Test that non-JSON text fails validation."""
    non_json_texts = [
        "This is plain text",
        "123",
        "true",
        "[1, 2, 3]",  # Array, not object
        ""
    ]
    
    for text in non_json_texts:
        is_valid, error_message, parsed = index._validate_json_response(text)
        assert is_valid is False, f"Non-JSON text '{text}' should fail validation"
        assert error_message is not None


def test_property_all_enum_values_accepted():
    """Test that all valid enum values are accepted."""
    # Test all valid status_of_aim values
    for status in ['not_set', 'in_progress', 'completed']:
        response = {
            'status_of_aim': status,
            'ui': {
                'render': {'type': 'message', 'text': 'Test'},
                'tone': 'supportive'
            },
            'input': {'type': 'text'},
            'previous_field_collected': {'field': 'test', 'label': 'Test', 'value': None}
        }
        is_valid, _, _ = index._validate_json_response(json.dumps(response))
        assert is_valid is True, f"Valid status_of_aim '{status}' should pass"
    
    # Test all valid render types
    for render_type in ['info', 'message', 'message_with_summary']:
        response = {
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {'type': render_type, 'text': 'Test'},
                'tone': 'supportive'
            },
            'input': {'type': 'text'},
            'previous_field_collected': {'field': 'test', 'label': 'Test', 'value': None}
        }
        is_valid, _, _ = index._validate_json_response(json.dumps(response))
        assert is_valid is True, f"Valid render type '{render_type}' should pass"
    
    # Test all valid input types
    for input_type in ['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown']:
        response = {
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {'type': 'message', 'text': 'Test'},
                'tone': 'supportive'
            },
            'input': {'type': input_type},
            'previous_field_collected': {'field': 'test', 'label': 'Test', 'value': None}
        }
        is_valid, _, _ = index._validate_json_response(json.dumps(response))
        assert is_valid is True, f"Valid input type '{input_type}' should pass"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

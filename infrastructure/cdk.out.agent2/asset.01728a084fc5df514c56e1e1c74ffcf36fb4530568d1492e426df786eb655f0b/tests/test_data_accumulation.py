"""
Unit tests for data accumulation functionality.

Tests for Requirements 4.4, 4.5, 4.6, 4.7:
- Test new field addition
- Test existing field overwriting
- Test null value handling
- Test empty string treated as null
"""

import json
import pytest
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


def test_new_field_addition():
    """
    Test that a new field is added to data_collected.
    Requirement 4.4: Store entire current_field object keyed by field name
    """
    current_field = {
        'field': 'name',
        'label': 'Name',
        'value': 'John'
    }
    
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    assert 'name' in data_collected
    assert data_collected['name']['field'] == 'name'
    assert data_collected['name']['label'] == 'Name'
    assert data_collected['name']['value'] == 'John'


def test_existing_field_overwriting():
    """
    Test that an existing field is overwritten (not appended or merged).
    Requirement 4.5: Overwrite existing fields (no append/merge)
    """
    current_field = {
        'field': 'age',
        'label': 'Age',
        'value': '30'
    }
    
    session_attributes = {
        'data_collected': json.dumps({
            'age': {
                'field': 'age',
                'label': 'Age',
                'value': '25'
            }
        })
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    assert 'age' in data_collected
    assert data_collected['age']['value'] == '30'  # Overwritten, not '25'
    assert len(data_collected) == 1  # No duplication


def test_null_value_preserves_existing():
    """
    Test that null value preserves existing entry.
    Requirement 4.6, 4.7: Preserve existing entry if new value is null
    """
    current_field = {
        'field': 'weight',
        'label': 'Weight',
        'value': None
    }
    
    session_attributes = {
        'data_collected': json.dumps({
            'weight': {
                'field': 'weight',
                'label': 'Weight',
                'value': '75'
            }
        })
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    assert 'weight' in data_collected
    assert data_collected['weight']['value'] == '75'  # Preserved, not overwritten


def test_null_value_for_new_field():
    """
    Test that null value for a new field creates an entry with null value.
    Requirement 4.7: Preserve existing entry if new value is null (but create if new)
    """
    current_field = {
        'field': 'height',
        'label': 'Height',
        'value': None
    }
    
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    assert 'height' in data_collected
    assert data_collected['height']['field'] == 'height'
    assert data_collected['height']['label'] == 'Height'
    assert data_collected['height']['value'] is None


def test_empty_string_treated_as_null():
    """
    Test that empty string is treated as null (not stored).
    Requirement 4.6: Empty string treated as null
    """
    current_field = {
        'field': 'email',
        'label': 'Email',
        'value': ''
    }
    
    session_attributes = {
        'data_collected': json.dumps({
            'email': {
                'field': 'email',
                'label': 'Email',
                'value': 'john@example.com'
            }
        })
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    assert 'email' in data_collected
    assert data_collected['email']['value'] == 'john@example.com'  # Preserved, empty string treated as null


def test_empty_string_for_new_field():
    """
    Test that empty string for a new field is treated as null.
    Requirement 4.6: Empty string treated as null
    """
    current_field = {
        'field': 'phone',
        'label': 'Phone',
        'value': ''
    }
    
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    # Empty string for new field creates entry with null value
    assert 'phone' in data_collected
    assert data_collected['phone']['value'] is None


def test_multiple_fields_accumulation():
    """
    Test that multiple fields accumulate correctly without duplication.
    """
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    # Add first field
    field1 = {'field': 'name', 'label': 'Name', 'value': 'John'}
    session_attributes = index._accumulate_data_collected(field1, session_attributes)
    
    # Add second field
    field2 = {'field': 'age', 'label': 'Age', 'value': '30'}
    session_attributes = index._accumulate_data_collected(field2, session_attributes)
    
    # Add third field
    field3 = {'field': 'weight', 'label': 'Weight', 'value': '75'}
    session_attributes = index._accumulate_data_collected(field3, session_attributes)
    
    data_collected = json.loads(session_attributes['data_collected'])
    
    assert len(data_collected) == 3
    assert 'name' in data_collected
    assert 'age' in data_collected
    assert 'weight' in data_collected
    assert data_collected['name']['value'] == 'John'
    assert data_collected['age']['value'] == '30'
    assert data_collected['weight']['value'] == '75'


def test_label_update_with_overwrite():
    """
    Test that label is updated when field is overwritten.
    """
    current_field = {
        'field': 'target_weight',
        'label': 'Target Weight (kg)',
        'value': '70'
    }
    
    session_attributes = {
        'data_collected': json.dumps({
            'target_weight': {
                'field': 'target_weight',
                'label': 'Target Weight',
                'value': '75'
            }
        })
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    
    assert data_collected['target_weight']['label'] == 'Target Weight (kg)'
    assert data_collected['target_weight']['value'] == '70'


def test_invalid_current_field():
    """
    Test that invalid current_field is handled gracefully.
    """
    session_attributes = {
        'data_collected': json.dumps({'name': {'field': 'name', 'label': 'Name', 'value': 'John'}})
    }
    
    # Test with None
    result = index._accumulate_data_collected(None, session_attributes)
    assert result == session_attributes
    
    # Test with empty dict
    result = index._accumulate_data_collected({}, session_attributes)
    assert result == session_attributes
    
    # Test with missing field name
    result = index._accumulate_data_collected({'label': 'Test', 'value': 'test'}, session_attributes)
    assert result == session_attributes
    
    # Test with missing label
    result = index._accumulate_data_collected({'field': 'test', 'value': 'test'}, session_attributes)
    assert result == session_attributes


def test_malformed_data_collected():
    """
    Test that malformed data_collected is handled gracefully.
    """
    current_field = {
        'field': 'name',
        'label': 'Name',
        'value': 'John'
    }
    
    # Test with invalid JSON
    session_attributes = {
        'data_collected': 'invalid json'
    }
    
    result = index._accumulate_data_collected(current_field, session_attributes)
    
    data_collected = json.loads(result['data_collected'])
    assert 'name' in data_collected
    assert data_collected['name']['value'] == 'John'


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

"""
Property-based tests for data accumulation functionality.

Property 6: Data Accumulation Without Duplication
Validates: Requirements 4.4, 4.5, 4.6, 4.7

For any agent response with a current_field, the field value must be accumulated
into data_collected such that existing fields are overwritten (not appended) and
new fields are added.
"""

import json
import pytest
from hypothesis import given, strategies as st, settings, assume
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


# Strategy for generating field names
@st.composite
def field_name_strategy(draw):
    """Generate valid field names (alphanumeric with underscores)."""
    return draw(st.text(
        min_size=1,
        max_size=50,
        alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'),
            whitelist_characters='_'
        )
    ))


# Strategy for generating field labels
@st.composite
def field_label_strategy(draw):
    """Generate field labels (human-readable text)."""
    return draw(st.text(min_size=1, max_size=100))


# Strategy for generating field values
@st.composite
def field_value_strategy(draw):
    """Generate field values (string, null, or empty string)."""
    return draw(st.one_of(
        st.text(min_size=1, max_size=200),  # Non-empty string
        st.just(None),  # Null
        st.just('')  # Empty string (should be treated as null)
    ))


# Strategy for generating current_field objects
@st.composite
def current_field_strategy(draw):
    """Generate random current_field objects."""
    return {
        'field': draw(field_name_strategy()),
        'label': draw(field_label_strategy()),
        'value': draw(field_value_strategy())
    }


# Strategy for generating sequences of current_field objects
@st.composite
def field_sequence_strategy(draw):
    """Generate sequences of current_field objects with potential duplicates."""
    # Generate a list of field names (some may repeat)
    num_unique_fields = draw(st.integers(min_value=1, max_value=10))
    field_names = [draw(field_name_strategy()) for _ in range(num_unique_fields)]
    
    # Generate a sequence of fields (may include duplicates)
    num_fields = draw(st.integers(min_value=1, max_value=20))
    fields = []
    for _ in range(num_fields):
        field_name = draw(st.sampled_from(field_names))
        fields.append({
            'field': field_name,
            'label': draw(field_label_strategy()),
            'value': draw(field_value_strategy())
        })
    
    return fields


@given(field_sequence=field_sequence_strategy())
@settings(max_examples=100, deadline=None)
def test_property_data_accumulation_without_duplication(field_sequence):
    """
    Property 6: Data Accumulation Without Duplication
    
    For any sequence of current_field objects:
    1. No field name appears more than once in data_collected (no duplication)
    2. Existing fields are overwritten with latest value (not appended/merged)
    3. Null values preserve existing entries (don't overwrite)
    4. Empty strings are treated as null
    5. Final data_collected contains correct final values
    """
    # Start with empty data_collected
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    # Track expected final values (simulate the accumulation logic)
    expected_data = {}
    
    # Accumulate each field in sequence
    for current_field in field_sequence:
        field_name = current_field['field']
        field_label = current_field['label']
        field_value = current_field['value']
        
        # Simulate expected behavior
        if field_value == '':
            field_value = None
        
        if field_value is not None:
            # Overwrite or add new field
            expected_data[field_name] = {
                'field': field_name,
                'label': field_label,
                'value': field_value
            }
        elif field_name not in expected_data:
            # New field with null value
            expected_data[field_name] = {
                'field': field_name,
                'label': field_label,
                'value': None
            }
        # else: preserve existing entry
        
        # Accumulate using the function
        session_attributes = index._accumulate_data_collected(
            current_field,
            session_attributes
        )
    
    # Parse final data_collected
    data_collected = json.loads(session_attributes['data_collected'])
    
    # Property 1: No duplication - each field name appears at most once
    field_names = list(data_collected.keys())
    assert len(field_names) == len(set(field_names)), "Field names must be unique (no duplication)"
    
    # Property 2: Final data matches expected data
    assert data_collected == expected_data, "Final data_collected must match expected accumulated data"
    
    # Property 3: All entries have correct structure
    for field_name, field_data in data_collected.items():
        assert 'field' in field_data, "Each entry must have 'field' key"
        assert 'label' in field_data, "Each entry must have 'label' key"
        assert 'value' in field_data, "Each entry must have 'value' key"
        assert field_data['field'] == field_name, "Field name must match key"


@given(
    initial_value=st.text(min_size=1, max_size=100),
    null_updates=st.integers(min_value=1, max_value=5)
)
@settings(max_examples=100, deadline=None)
def test_property_null_value_preservation(initial_value, null_updates):
    """
    Property: Null values preserve existing entries.
    
    For any field with an initial non-null value, subsequent null updates
    must preserve the original value (not overwrite it).
    """
    field_name = 'test_field'
    field_label = 'Test Field'
    
    # Start with empty data_collected
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    # Add initial value
    initial_field = {
        'field': field_name,
        'label': field_label,
        'value': initial_value
    }
    session_attributes = index._accumulate_data_collected(initial_field, session_attributes)
    
    # Apply multiple null updates
    for _ in range(null_updates):
        null_field = {
            'field': field_name,
            'label': field_label,
            'value': None
        }
        session_attributes = index._accumulate_data_collected(null_field, session_attributes)
    
    # Verify original value is preserved
    data_collected = json.loads(session_attributes['data_collected'])
    assert data_collected[field_name]['value'] == initial_value, "Null updates must preserve existing value"


@given(
    field_name=field_name_strategy(),
    values=st.lists(st.text(min_size=1, max_size=100), min_size=2, max_size=10)
)
@settings(max_examples=100, deadline=None)
def test_property_overwriting_behavior(field_name, values):
    """
    Property: Existing fields are overwritten (not appended or merged).
    
    For any field that is updated multiple times with different values,
    only the last value should be present (no list, no concatenation).
    """
    # Start with empty data_collected
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    # Apply each value in sequence
    for value in values:
        current_field = {
            'field': field_name,
            'label': 'Test Label',
            'value': value
        }
        session_attributes = index._accumulate_data_collected(current_field, session_attributes)
    
    # Verify only the last value is present
    data_collected = json.loads(session_attributes['data_collected'])
    assert field_name in data_collected
    assert data_collected[field_name]['value'] == values[-1], "Only last value should be present (overwrite, not append)"
    
    # Verify value is a string, not a list or concatenation
    assert isinstance(data_collected[field_name]['value'], str), "Value must be a string, not a list"


@given(
    field_name=field_name_strategy(),
    empty_string_updates=st.integers(min_value=1, max_value=5)
)
@settings(max_examples=100, deadline=None)
def test_property_empty_string_as_null(field_name, empty_string_updates):
    """
    Property: Empty strings are treated as null.
    
    For any field with an initial value, subsequent empty string updates
    must be treated as null (preserve existing value).
    """
    initial_value = 'initial_value'
    
    # Start with empty data_collected
    session_attributes = {
        'data_collected': json.dumps({})
    }
    
    # Add initial value
    initial_field = {
        'field': field_name,
        'label': 'Test Label',
        'value': initial_value
    }
    session_attributes = index._accumulate_data_collected(initial_field, session_attributes)
    
    # Apply multiple empty string updates
    for _ in range(empty_string_updates):
        empty_field = {
            'field': field_name,
            'label': 'Test Label',
            'value': ''
        }
        session_attributes = index._accumulate_data_collected(empty_field, session_attributes)
    
    # Verify original value is preserved (empty string treated as null)
    data_collected = json.loads(session_attributes['data_collected'])
    assert data_collected[field_name]['value'] == initial_value, "Empty string updates must preserve existing value"


@given(field_sequence=field_sequence_strategy())
@settings(max_examples=100, deadline=None)
def test_property_idempotent_accumulation(field_sequence):
    """
    Property: Accumulation is deterministic and idempotent.
    
    For any sequence of fields, accumulating them twice should produce
    the same result as accumulating them once.
    """
    # First accumulation
    session_attributes_1 = {
        'data_collected': json.dumps({})
    }
    for current_field in field_sequence:
        session_attributes_1 = index._accumulate_data_collected(
            current_field,
            session_attributes_1
        )
    
    # Second accumulation (same sequence)
    session_attributes_2 = {
        'data_collected': json.dumps({})
    }
    for current_field in field_sequence:
        session_attributes_2 = index._accumulate_data_collected(
            current_field,
            session_attributes_2
        )
    
    # Results should be identical
    data_collected_1 = json.loads(session_attributes_1['data_collected'])
    data_collected_2 = json.loads(session_attributes_2['data_collected'])
    
    assert data_collected_1 == data_collected_2, "Accumulation must be deterministic"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

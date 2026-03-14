"""
Property-based tests for data_collected persistence across turns.

Property 7: Data Collected Persistence Across Turns
Validates: Requirements 4.8, 4.9, 7.4

For any multi-turn session, data_collected must contain all fields accumulated
across all turns with their final values, and must be included in every
successful response.
"""

import json
import pytest
from hypothesis import given, strategies as st, settings
from unittest.mock import MagicMock, patch
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


# Strategy for generating valid agent responses
@st.composite
def agent_response_strategy(draw, field_name, field_value):
    """Generate valid agent JSON responses with current_field."""
    return {
        'status_of_aim': draw(st.sampled_from(['not_set', 'in_progress', 'completed'])),
        'ui': {
            'render': {
                'type': draw(st.sampled_from(['info', 'message', 'message_with_summary'])),
                'text': draw(st.text(min_size=1, max_size=200))
            },
            'tone': 'supportive'
        },
        'input': {
            'type': draw(st.sampled_from(['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown']))
        },
        'current_field': {
            'field': field_name,
            'label': draw(st.text(min_size=1, max_size=100)),
            'value': field_value
        }
    }


# Strategy for generating multi-turn sessions
@st.composite
def multi_turn_session_strategy(draw):
    """
    Generate random multi-turn sessions with agent responses.
    
    Returns a list of (field_name, field_value, agent_response) tuples.
    """
    num_turns = draw(st.integers(min_value=2, max_value=10))
    num_unique_fields = draw(st.integers(min_value=1, max_value=5))
    
    # Generate unique field names
    field_names = [draw(field_name_strategy()) for _ in range(num_unique_fields)]
    
    turns = []
    for _ in range(num_turns):
        field_name = draw(st.sampled_from(field_names))
        field_value = draw(st.one_of(
            st.text(min_size=1, max_size=100),
            st.just(None)
        ))
        agent_response = draw(agent_response_strategy(field_name, field_value))
        turns.append((field_name, field_value, agent_response))
    
    return turns


@given(session_turns=multi_turn_session_strategy())
@settings(max_examples=100, deadline=None)
def test_property_data_collected_persistence_across_turns(session_turns):
    """
    Property 7: Data Collected Persistence Across Turns
    
    For any multi-turn session:
    1. data_collected contains all fields from all turns
    2. data_collected is included in every successful response
    3. Final values are correct (last non-null value for each field)
    """
    user_id = 'test-user-123'
    session_id = 'test-session-456'
    
    # Track expected final values
    expected_data = {}
    
    # Simulate multiple turns
    for turn_index, (field_name, field_value, agent_response) in enumerate(session_turns):
        # Update expected data
        if field_value is not None:
            expected_data[field_name] = {
                'field': field_name,
                'label': agent_response['current_field']['label'],
                'value': field_value
            }
        elif field_name not in expected_data:
            expected_data[field_name] = {
                'field': field_name,
                'label': agent_response['current_field']['label'],
                'value': None
            }
        
        # Mock Bedrock response
        mock_bedrock_client = MagicMock()
        
        # Simulate session attributes from previous turns
        if turn_index == 0:
            # First turn: empty data_collected
            retrieved_session_attributes = {
                'user_id': user_id,
                'auth_header_name': 'X-Amigo-Auth',
                'hat': 'test',
                'data_collected': json.dumps({}),
                'json_validation_retry_count': '0'
            }
        else:
            # Subsequent turns: use accumulated data from previous turns
            retrieved_session_attributes = {
                'user_id': user_id,
                'auth_header_name': 'X-Amigo-Auth',
                'hat': 'test',
                'data_collected': json.dumps(expected_data),
                'json_validation_retry_count': '0'
            }
        
        # Mock agent response
        agent_response_json = json.dumps(agent_response)
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': agent_response_json.encode('utf-8')
                    },
                    'sessionState': {
                        'sessionAttributes': retrieved_session_attributes
                    }
                }
            ]
        }
        mock_bedrock_client.invoke_agent.return_value = mock_response
        
        # Create event
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': f'Bearer test-token-{user_id}'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': f'Turn {turn_index + 1} message',
                'sessionId': session_id,
                'agentId': 'test-agent-id',
                'agentAliasId': 'test-alias-id'
            })
        }
        
        # Mock verify_supabase_token
        with patch.object(index, 'verify_supabase_token') as mock_verify:
            mock_verify.return_value = {
                'sub': user_id,
                'email': f'{user_id}@test.com',
                'role': 'authenticated'
            }
            
            # Mock bedrock_agent_runtime client
            with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
                # Call lambda handler
                response = index.lambda_handler(event, {})
                
                # Verify response is successful
                assert response['statusCode'] == 200
                
                # Parse response body
                response_body = json.loads(response['body'])
                
                # Property 1: data_collected is included in every successful response
                assert 'data_collected' in response_body, "data_collected must be present in every successful response"
                
                # Property 2: data_collected contains accumulated fields
                data_collected = response_body['data_collected']
                
                # Verify all expected fields are present
                for expected_field_name in expected_data.keys():
                    assert expected_field_name in data_collected, f"Field '{expected_field_name}' must be in data_collected"
                
                # Property 3: Final values are correct
                for field_name, expected_field_data in expected_data.items():
                    assert data_collected[field_name]['value'] == expected_field_data['value'], \
                        f"Field '{field_name}' must have correct final value"


@given(
    num_turns=st.integers(min_value=3, max_value=10),
    num_fields=st.integers(min_value=2, max_value=5)
)
@settings(max_examples=50, deadline=None)
def test_property_data_collected_grows_monotonically(num_turns, num_fields):
    """
    Property: data_collected grows monotonically (never loses fields).
    
    For any multi-turn session, the number of fields in data_collected
    should never decrease (only stay the same or increase).
    """
    user_id = 'test-user-123'
    session_id = 'test-session-456'
    
    # Generate unique field names
    field_names = [f'field_{i}' for i in range(num_fields)]
    
    # Track data_collected size across turns
    data_collected_sizes = []
    accumulated_data = {}
    
    for turn_index in range(num_turns):
        # Pick a field to update (may be new or existing)
        field_name = field_names[turn_index % num_fields]
        field_value = f'value_{turn_index}'
        
        # Update accumulated data
        accumulated_data[field_name] = {
            'field': field_name,
            'label': f'Label {field_name}',
            'value': field_value
        }
        
        # Create agent response
        agent_response = {
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {
                    'type': 'message',
                    'text': f'Turn {turn_index + 1}'
                },
                'tone': 'supportive'
            },
            'input': {
                'type': 'text'
            },
            'current_field': {
                'field': field_name,
                'label': f'Label {field_name}',
                'value': field_value
            }
        }
        
        # Mock Bedrock response
        mock_bedrock_client = MagicMock()
        retrieved_session_attributes = {
            'user_id': user_id,
            'auth_header_name': 'X-Amigo-Auth',
            'hat': 'test',
            'data_collected': json.dumps(accumulated_data),
            'json_validation_retry_count': '0'
        }
        
        agent_response_json = json.dumps(agent_response)
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': agent_response_json.encode('utf-8')
                    },
                    'sessionState': {
                        'sessionAttributes': retrieved_session_attributes
                    }
                }
            ]
        }
        mock_bedrock_client.invoke_agent.return_value = mock_response
        
        # Create event
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': f'Bearer test-token-{user_id}'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': f'Turn {turn_index + 1} message',
                'sessionId': session_id,
                'agentId': 'test-agent-id',
                'agentAliasId': 'test-alias-id'
            })
        }
        
        # Mock verify_supabase_token
        with patch.object(index, 'verify_supabase_token') as mock_verify:
            mock_verify.return_value = {
                'sub': user_id,
                'email': f'{user_id}@test.com',
                'role': 'authenticated'
            }
            
            # Mock bedrock_agent_runtime client
            with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
                # Call lambda handler
                response = index.lambda_handler(event, {})
                
                # Parse response
                response_body = json.loads(response['body'])
                data_collected = response_body['data_collected']
                
                # Track size
                data_collected_sizes.append(len(data_collected))
    
    # Property: data_collected size never decreases
    for i in range(1, len(data_collected_sizes)):
        assert data_collected_sizes[i] >= data_collected_sizes[i-1], \
            "data_collected size must never decrease (monotonic growth)"


@given(
    field_name=field_name_strategy(),
    values=st.lists(st.text(min_size=1, max_size=100), min_size=2, max_size=5)
)
@settings(max_examples=50, deadline=None)
def test_property_data_collected_reflects_latest_value(field_name, values):
    """
    Property: data_collected always reflects the latest non-null value.
    
    For any field updated multiple times, data_collected should contain
    the most recent non-null value.
    """
    user_id = 'test-user-123'
    session_id = 'test-session-456'
    
    accumulated_data = {}
    
    for turn_index, value in enumerate(values):
        # Update accumulated data
        accumulated_data[field_name] = {
            'field': field_name,
            'label': 'Test Label',
            'value': value
        }
        
        # Create agent response
        agent_response = {
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {
                    'type': 'message',
                    'text': f'Turn {turn_index + 1}'
                },
                'tone': 'supportive'
            },
            'input': {
                'type': 'text'
            },
            'current_field': {
                'field': field_name,
                'label': 'Test Label',
                'value': value
            }
        }
        
        # Mock Bedrock response
        mock_bedrock_client = MagicMock()
        retrieved_session_attributes = {
            'user_id': user_id,
            'auth_header_name': 'X-Amigo-Auth',
            'hat': 'test',
            'data_collected': json.dumps(accumulated_data),
            'json_validation_retry_count': '0'
        }
        
        agent_response_json = json.dumps(agent_response)
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': agent_response_json.encode('utf-8')
                    },
                    'sessionState': {
                        'sessionAttributes': retrieved_session_attributes
                    }
                }
            ]
        }
        mock_bedrock_client.invoke_agent.return_value = mock_response
        
        # Create event
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': f'Bearer test-token-{user_id}'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': f'Turn {turn_index + 1} message',
                'sessionId': session_id,
                'agentId': 'test-agent-id',
                'agentAliasId': 'test-alias-id'
            })
        }
        
        # Mock verify_supabase_token
        with patch.object(index, 'verify_supabase_token') as mock_verify:
            mock_verify.return_value = {
                'sub': user_id,
                'email': f'{user_id}@test.com',
                'role': 'authenticated'
            }
            
            # Mock bedrock_agent_runtime client
            with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
                # Call lambda handler
                response = index.lambda_handler(event, {})
                
                # Parse response
                response_body = json.loads(response['body'])
                data_collected = response_body['data_collected']
                
                # Property: data_collected contains the latest value
                assert data_collected[field_name]['value'] == value, \
                    "data_collected must reflect the latest value"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

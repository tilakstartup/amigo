"""
Property-based tests for session attributes initialization.

Property 2: Session Attributes Initialization
Validates: Requirements 2.1, 2.4, 2.5

For any new session, session attributes must contain all required fields
(user_id, auth_header_name, hat, responsibilities, data_to_be_collected,
data_to_be_calculated, notes, data_collected, json_validation_retry_count)
with correct initial values (data_collected as empty object,
json_validation_retry_count as 0).
"""

import json
import pytest
from hypothesis import given, strategies as st, settings
from unittest.mock import Mock, patch, MagicMock
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


# Strategy for generating session configs
@st.composite
def session_config_strategy(draw):
    """Generate random session configs with all required fields."""
    hat = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    
    # Generate lists of responsibilities, data fields, and notes
    responsibilities = draw(st.lists(
        st.text(min_size=1, max_size=100),
        min_size=1,
        max_size=10
    ))
    
    data_to_be_collected = draw(st.lists(
        st.text(min_size=1, max_size=50, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        )),
        min_size=1,
        max_size=20
    ))
    
    data_to_be_calculated = draw(st.lists(
        st.text(min_size=1, max_size=50, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        )),
        min_size=0,
        max_size=10
    ))
    
    notes = draw(st.lists(
        st.text(min_size=1, max_size=200),
        min_size=0,
        max_size=5
    ))
    
    initial_message = draw(st.text(min_size=1, max_size=500))
    
    return {
        'hat': hat,
        'responsibilities': responsibilities,
        'data_to_be_collected': data_to_be_collected,
        'data_to_be_calculated': data_to_be_calculated,
        'notes': notes,
        'initial_message': initial_message
    }


@st.composite
def user_id_strategy(draw):
    """Generate random user IDs."""
    return draw(st.text(min_size=1, max_size=100, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))


@given(
    session_config=session_config_strategy(),
    user_id=user_id_strategy()
)
@settings(max_examples=100, deadline=None)
def test_property_session_attributes_initialization(session_config, user_id):
    """
    Property 2: Session Attributes Initialization
    
    For any new session with a session config, verify:
    1. All required fields are present in session attributes
    2. data_collected is initialized as empty object {}
    3. json_validation_retry_count is initialized to 0
    4. Config fields are properly stored (lists as JSON strings)
    """
    # Mock the Bedrock agent runtime client
    mock_bedrock_client = MagicMock()
    mock_response = {
        'completion': [
            {
                'chunk': {
                    'bytes': b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Test"}, "tone": "supportive"}, "input": {"type": "text"}, "current_field": {"field": "test", "label": "Test", "value": null}}'
                }
            }
        ]
    }
    mock_bedrock_client.invoke_agent.return_value = mock_response
    
    # Create event with session config
    event = {
        'httpMethod': 'POST',
        'headers': {
            'Authorization': f'Bearer test-token-{user_id}'
        },
        'body': json.dumps({
            'mode': 'agent',
            'message': session_config['initial_message'],
            'sessionId': f'test-session-{user_id}',
            'agentId': 'test-agent-id',
            'agentAliasId': 'test-alias-id',
            'sessionConfig': session_config
        })
    }
    
    # Mock verify_supabase_token to return valid user
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
            
            # Extract the invoke_agent call arguments
            assert mock_bedrock_client.invoke_agent.called
            call_args = mock_bedrock_client.invoke_agent.call_args
            
            # Get session attributes from the call
            session_state = call_args[1]['sessionState']
            session_attributes = session_state['sessionAttributes']
            
            # Property verification: All required fields must be present
            required_fields = [
                'user_id',
                'auth_header_name',
                'hat',
                'responsibilities',
                'data_to_be_collected',
                'data_to_be_calculated',
                'notes',
                'data_collected',
                'json_validation_retry_count'
            ]
            
            for field in required_fields:
                assert field in session_attributes, f"Required field '{field}' missing from session attributes"
            
            # Verify user_id and auth_header_name
            assert session_attributes['user_id'] == user_id
            assert session_attributes['auth_header_name'] == 'X-Amigo-Auth'
            
            # Verify config fields are stored correctly
            assert session_attributes['hat'] == session_config['hat']
            assert json.loads(session_attributes['responsibilities']) == session_config['responsibilities']
            assert json.loads(session_attributes['data_to_be_collected']) == session_config['data_to_be_collected']
            assert json.loads(session_attributes['data_to_be_calculated']) == session_config['data_to_be_calculated']
            assert json.loads(session_attributes['notes']) == session_config['notes']
            
            # Property verification: data_collected must be empty object
            data_collected = json.loads(session_attributes['data_collected'])
            assert data_collected == {}, f"data_collected should be empty object, got: {data_collected}"
            assert isinstance(data_collected, dict), "data_collected must be a dict/object"
            
            # Property verification: json_validation_retry_count must be 0
            retry_count = int(session_attributes['json_validation_retry_count'])
            assert retry_count == 0, f"json_validation_retry_count should be 0, got: {retry_count}"
            
            # Verify initial_message is NOT in session attributes
            assert 'initial_message' not in session_attributes, "initial_message should not be in session attributes"
            
            # Verify initial_message was sent as inputText
            assert call_args[1]['inputText'] == session_config['initial_message']


def test_session_attributes_without_config():
    """
    Test that session attributes are created even without session config.
    This handles the case of subsequent messages in the same session.
    """
    user_id = 'test-user-123'
    
    # Mock the Bedrock agent runtime client
    mock_bedrock_client = MagicMock()
    mock_response = {
        'completion': [
            {
                'chunk': {
                    'bytes': b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Test"}, "tone": "supportive"}, "input": {"type": "text"}, "current_field": {"field": "test", "label": "Test", "value": null}}'
                }
            }
        ]
    }
    mock_bedrock_client.invoke_agent.return_value = mock_response
    
    # Create event without session config (subsequent message)
    event = {
        'httpMethod': 'POST',
        'headers': {
            'Authorization': f'Bearer test-token-{user_id}'
        },
        'body': json.dumps({
            'mode': 'agent',
            'message': 'Follow-up message',
            'sessionId': f'test-session-{user_id}',
            'agentId': 'test-agent-id',
            'agentAliasId': 'test-alias-id',
            'sessionConfig': None
        })
    }
    
    # Mock verify_supabase_token to return valid user
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
            
            # Extract the invoke_agent call arguments
            call_args = mock_bedrock_client.invoke_agent.call_args
            session_state = call_args[1]['sessionState']
            session_attributes = session_state['sessionAttributes']
            
            # Verify minimal session attributes are present
            assert 'user_id' in session_attributes
            assert 'auth_header_name' in session_attributes
            assert session_attributes['user_id'] == user_id
            assert session_attributes['auth_header_name'] == 'X-Amigo-Auth'
            
            # Config-specific fields should NOT be present without session config
            assert 'data_collected' not in session_attributes
            assert 'json_validation_retry_count' not in session_attributes


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

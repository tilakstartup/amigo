"""
Property-Based Test for Initial Message Extraction

Feature: chat-engine-optimization
Property 4: Initial Message Extraction and Usage
Validates: Requirements 3.1, 3.2, 3.4

This test verifies that:
1. initial_message is extracted correctly from sessionConfig
2. initial_message is sent as inputText on first invocation
3. Other config fields (hat, responsibilities, data_to_be_collected, data_to_be_calculated, notes) are stored in session attributes
4. Session attributes do NOT include initial_message

Note: This test does NOT validate session attributes initialization (data_collected, json_validation_retry_count)
as that is covered by task 2.2.
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


# Strategy for generating valid session configs
@st.composite
def session_config_strategy(draw):
    """Generate random session configs with initial_message"""
    hat = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_')))
    
    # Generate list of responsibilities
    num_responsibilities = draw(st.integers(min_value=1, max_value=5))
    responsibilities = [
        draw(st.text(min_size=5, max_size=100))
        for _ in range(num_responsibilities)
    ]
    
    # Generate list of data fields
    num_fields = draw(st.integers(min_value=1, max_value=10))
    data_to_be_collected = [
        draw(st.text(min_size=1, max_size=30, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_')))
        for _ in range(num_fields)
    ]
    
    # Generate list of calculated fields
    num_calculated = draw(st.integers(min_value=0, max_value=5))
    data_to_be_calculated = [
        draw(st.text(min_size=1, max_size=30, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_')))
        for _ in range(num_calculated)
    ]
    
    # Generate notes
    num_notes = draw(st.integers(min_value=0, max_value=3))
    notes = [
        draw(st.text(min_size=5, max_size=100))
        for _ in range(num_notes)
    ]
    
    # Generate initial_message
    initial_message = draw(st.text(min_size=1, max_size=200))
    
    return {
        'hat': hat,
        'responsibilities': responsibilities,
        'data_to_be_collected': data_to_be_collected,
        'data_to_be_calculated': data_to_be_calculated,
        'notes': notes,
        'initial_message': initial_message
    }


@given(session_config=session_config_strategy())
@settings(max_examples=100, deadline=None)
def test_initial_message_extraction_property(session_config):
    """
    Property 4: Initial Message Extraction and Usage
    
    For any session config with initial_message:
    1. initial_message must be extracted correctly
    2. initial_message must be sent as inputText on first invocation
    3. Other config fields must be stored in session attributes
    4. Session attributes must NOT include initial_message
    """
    
    # Mock Bedrock agent runtime client
    with patch('index.bedrock_agent_runtime') as mock_bedrock:
        # Setup mock response
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Test"}, "tone": "supportive"}, "input": {"type": "text"}, "current_field": {"field": "test", "label": "Test", "value": null}}'
                    }
                }
            ]
        }
        mock_bedrock.invoke_agent.return_value = mock_response
        
        # Create Lambda event with session config
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer test-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': session_config['initial_message'],
                'sessionId': 'test-session-123',
                'agentId': 'test-agent-id',
                'agentAliasId': 'test-alias-id',
                'sessionConfig': session_config
            })
        }
        
        # Mock token verification
        with patch('index.verify_supabase_token') as mock_verify:
            mock_verify.return_value = {
                'sub': 'test-user-123',
                'email': 'test@example.com',
                'role': 'authenticated'
            }
            
            # Call Lambda handler
            context = Mock()
            response = index.lambda_handler(event, context)
            
            # Verify response is successful
            assert response['statusCode'] == 200
            
            # Get the invoke_agent call arguments
            assert mock_bedrock.invoke_agent.called
            call_args = mock_bedrock.invoke_agent.call_args
            
            # PROPERTY 1: initial_message must be sent as inputText
            assert 'inputText' in call_args[1]
            assert call_args[1]['inputText'] == session_config['initial_message']
            
            # PROPERTY 2: Session attributes must be present
            assert 'sessionState' in call_args[1]
            assert 'sessionAttributes' in call_args[1]['sessionState']
            session_attributes = call_args[1]['sessionState']['sessionAttributes']
            
            # PROPERTY 3: Session attributes must contain config fields (except initial_message)
            assert 'hat' in session_attributes
            assert session_attributes['hat'] == session_config['hat']
            
            assert 'responsibilities' in session_attributes
            # Session attributes are strings, so lists are JSON-encoded
            assert json.loads(session_attributes['responsibilities']) == session_config['responsibilities']
            
            assert 'data_to_be_collected' in session_attributes
            assert json.loads(session_attributes['data_to_be_collected']) == session_config['data_to_be_collected']
            
            assert 'data_to_be_calculated' in session_attributes
            assert json.loads(session_attributes['data_to_be_calculated']) == session_config['data_to_be_calculated']
            
            assert 'notes' in session_attributes
            assert json.loads(session_attributes['notes']) == session_config['notes']
            
            # PROPERTY 4: Session attributes must NOT include initial_message
            assert 'initial_message' not in session_attributes


@given(session_config=session_config_strategy())
@settings(max_examples=100, deadline=None)
def test_subsequent_invocation_without_session_config(session_config):
    """
    Property: Subsequent invocations should NOT include sessionConfig
    
    For any subsequent message in the same session:
    1. sessionConfig must be null or absent
    2. Session attributes must be retrieved from Bedrock (not re-initialized)
    3. inputText must be the new user message (not initial_message)
    """
    
    # Mock Bedrock agent runtime client
    with patch('index.bedrock_agent_runtime') as mock_bedrock:
        # Setup mock response
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Test"}, "tone": "supportive"}, "input": {"type": "text"}, "current_field": {"field": "test", "label": "Test", "value": "test_value"}}'
                    }
                }
            ]
        }
        mock_bedrock.invoke_agent.return_value = mock_response
        
        # Create Lambda event WITHOUT session config (subsequent message)
        subsequent_message = "This is a follow-up message"
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer test-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': subsequent_message,
                'sessionId': 'test-session-123',
                'agentId': 'test-agent-id',
                'agentAliasId': 'test-alias-id',
                'sessionConfig': None  # No session config on subsequent messages
            })
        }
        
        # Mock token verification
        with patch('index.verify_supabase_token') as mock_verify:
            mock_verify.return_value = {
                'sub': 'test-user-123',
                'email': 'test@example.com',
                'role': 'authenticated'
            }
            
            # Call Lambda handler
            context = Mock()
            response = index.lambda_handler(event, context)
            
            # Verify response is successful
            assert response['statusCode'] == 200
            
            # Get the invoke_agent call arguments
            assert mock_bedrock.invoke_agent.called
            call_args = mock_bedrock.invoke_agent.call_args
            
            # PROPERTY 1: inputText must be the subsequent message (not initial_message)
            assert 'inputText' in call_args[1]
            assert call_args[1]['inputText'] == subsequent_message
            assert call_args[1]['inputText'] != session_config['initial_message']
            
            # PROPERTY 2: Session attributes should only contain basic fields (no config fields)
            # Because Bedrock persists session attributes automatically
            assert 'sessionState' in call_args[1]
            assert 'sessionAttributes' in call_args[1]['sessionState']
            session_attributes = call_args[1]['sessionState']['sessionAttributes']
            
            # Basic fields should be present
            assert 'user_id' in session_attributes
            assert 'auth_header_name' in session_attributes


def test_initial_message_extraction_unit():
    """
    Unit test: Verify initial_message extraction with specific example
    """
    session_config = {
        'hat': 'onboarding',
        'responsibilities': ['Collect name', 'Collect age'],
        'data_to_be_collected': ['name', 'age'],
        'data_to_be_calculated': ['bmr'],
        'notes': ['Important constraint'],
        'initial_message': 'Hello, I want to start my fitness journey'
    }
    
    # Mock Bedrock agent runtime client
    with patch('index.bedrock_agent_runtime') as mock_bedrock:
        # Setup mock response
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Great!"}, "tone": "supportive"}, "input": {"type": "text"}, "current_field": {"field": "name", "label": "Name", "value": null}}'
                    }
                }
            ]
        }
        mock_bedrock.invoke_agent.return_value = mock_response
        
        # Create Lambda event
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer test-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': session_config['initial_message'],
                'sessionId': 'test-session-123',
                'agentId': 'test-agent-id',
                'agentAliasId': 'test-alias-id',
                'sessionConfig': session_config
            })
        }
        
        # Mock token verification
        with patch('index.verify_supabase_token') as mock_verify:
            mock_verify.return_value = {
                'sub': 'test-user-123',
                'email': 'test@example.com',
                'role': 'authenticated'
            }
            
            # Call Lambda handler
            context = Mock()
            response = index.lambda_handler(event, context)
            
            # Verify response
            assert response['statusCode'] == 200
            
            # Verify invoke_agent was called with correct parameters
            call_args = mock_bedrock.invoke_agent.call_args[1]
            
            # Verify initial_message is sent as inputText
            assert call_args['inputText'] == 'Hello, I want to start my fitness journey'
            
            # Verify session attributes contain config fields
            session_attrs = call_args['sessionState']['sessionAttributes']
            assert session_attrs['hat'] == 'onboarding'
            assert json.loads(session_attrs['responsibilities']) == ['Collect name', 'Collect age']
            assert json.loads(session_attrs['data_to_be_collected']) == ['name', 'age']
            assert json.loads(session_attrs['data_to_be_calculated']) == ['bmr']
            assert json.loads(session_attrs['notes']) == ['Important constraint']
            
            # Verify initial_message is NOT in session attributes
            assert 'initial_message' not in session_attrs


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

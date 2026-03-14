"""
Property-based tests for JSON validation retry mechanism.

Property 9: JSON Validation Retry Mechanism
Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6

For any validation failure, if retry count is less than 3, the retry count must be
incremented and a retry message must be sent to the agent in the same session; if
retry count reaches 3, an error response must be returned without further retries.
"""

import json
import pytest
from hypothesis import given, strategies as st, settings
from unittest.mock import Mock, patch, MagicMock, call
import sys
import os

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


@st.composite
def invalid_json_response_strategy(draw):
    """Generate invalid JSON responses that will fail validation."""
    issue_type = draw(st.sampled_from([
        'missing_field',
        'invalid_enum',
        'empty_string',
        'malformed_json'
    ]))
    
    if issue_type == 'malformed_json':
        return "not valid json at all"
    
    # Create response with an issue
    response = {
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
    }
    
    if issue_type == 'missing_field':
        del response['input']
    elif issue_type == 'invalid_enum':
        response['status_of_aim'] = 'invalid_status'
    elif issue_type == 'empty_string':
        response['current_field']['field'] = ''
    
    return json.dumps(response)


@given(
    retry_count=st.integers(min_value=0, max_value=2),
    invalid_response=invalid_json_response_strategy()
)
@settings(max_examples=50, deadline=None)
def test_property_retry_increments_count_when_below_limit(retry_count, invalid_response):
    """
    Property 9: JSON Validation Retry Mechanism (Retry Case)
    
    For any validation failure with retry count < 3, verify:
    1. Retry count is incremented
    2. Retry message is sent to agent
    3. Same session is used
    """
    user_id = 'test-user-123'
    session_id = 'test-session-123'
    
    # Mock Bedrock client
    mock_bedrock_client = MagicMock()
    
    # First response: invalid JSON
    # Second response: valid JSON (after retry)
    valid_response = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {'type': 'message', 'text': 'Valid response'},
            'tone': 'supportive'
        },
        'input': {'type': 'text'},
        'current_field': {'field': 'test', 'label': 'Test', 'value': None}
    })

    
    # Setup mock responses
    mock_response_invalid = {
        'completion': [
            {
                'chunk': {'bytes': invalid_response.encode('utf-8')},
                'sessionState': {
                    'sessionAttributes': {
                        'user_id': user_id,
                        'json_validation_retry_count': str(retry_count)
                    }
                }
            }
        ]
    }
    
    mock_response_valid = {
        'completion': [
            {
                'chunk': {'bytes': valid_response.encode('utf-8')},
                'sessionState': {
                    'sessionAttributes': {
                        'user_id': user_id,
                        'json_validation_retry_count': str(retry_count + 1)
                    }
                }
            }
        ]
    }
    
    # First call returns invalid, second call returns valid
    mock_bedrock_client.invoke_agent.side_effect = [mock_response_invalid, mock_response_valid]
    
    # Create event
    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'message': 'Test message',
            'sessionId': session_id,
            'agentId': 'test-agent-id',
            'agentAliasId': 'test-alias-id'
        })
    }
    
    with patch.object(index, 'verify_supabase_token') as mock_verify:
        mock_verify.return_value = {'sub': user_id, 'email': 'test@test.com', 'role': 'authenticated'}
        
        with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
            response = index.lambda_handler(event, {})
            
            # Verify response is successful (retry succeeded)
            assert response['statusCode'] == 200
            
            # Property: invoke_agent should be called twice (initial + retry)
            assert mock_bedrock_client.invoke_agent.call_count == 2
            
            # Property: Second call should have incremented retry count
            second_call_args = mock_bedrock_client.invoke_agent.call_args_list[1]
            session_attrs = second_call_args[1]['sessionState']['sessionAttributes']
            assert int(session_attrs['json_validation_retry_count']) == retry_count + 1
            
            # Property: Second call should have retry message
            assert second_call_args[1]['inputText'] == "Please respond with JSON object ONLY"
            
            # Property: Same session ID should be used
            assert second_call_args[1]['sessionId'] == session_id


@given(retry_count=st.just(3))
@settings(max_examples=10, deadline=None)
def test_property_error_returned_after_max_retries(retry_count):
    """
    Property 9: JSON Validation Retry Mechanism (Max Retries Case)
    
    For any validation failure with retry count = 3, verify:
    1. No retry is attempted
    2. Error response is returned
    3. Error message indicates max retries exceeded
    """
    user_id = 'test-user-123'
    session_id = 'test-session-123'
    
    # Mock Bedrock client
    mock_bedrock_client = MagicMock()
    
    # Invalid JSON response
    invalid_response = json.dumps({'invalid': 'response'})
    
    mock_response = {
        'completion': [
            {
                'chunk': {'bytes': invalid_response.encode('utf-8')},
                'sessionState': {
                    'sessionAttributes': {
                        'user_id': user_id,
                        'json_validation_retry_count': str(retry_count)
                    }
                }
            }
        ]
    }
    
    mock_bedrock_client.invoke_agent.return_value = mock_response
    
    # Create event
    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'message': 'Test message',
            'sessionId': session_id,
            'agentId': 'test-agent-id',
            'agentAliasId': 'test-alias-id'
        })
    }
    
    with patch.object(index, 'verify_supabase_token') as mock_verify:
        mock_verify.return_value = {'sub': user_id, 'email': 'test@test.com', 'role': 'authenticated'}
        
        with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
            response = index.lambda_handler(event, {})
            
            # Verify response contains error
            assert response['statusCode'] == 200
            body = json.loads(response['body'])
            
            # Property: Error field must be present
            assert 'error' in body
            assert 'failed to respond with valid JSON after 3 retries' in body['error']
            
            # Property: invoke_agent should only be called once (no retry)
            assert mock_bedrock_client.invoke_agent.call_count == 1
            
            # Property: userId and timestamp must be present
            assert 'userId' in body
            assert 'timestamp' in body
            assert body['userId'] == user_id



def test_property_new_session_starts_with_zero_retry_count():
    """
    Property 9: JSON Validation Retry Mechanism (New Session Case)
    
    For any new session, verify retry count is initialized to 0.
    """
    user_id = 'test-user-123'
    
    # Mock Bedrock client
    mock_bedrock_client = MagicMock()
    
    valid_response = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {'type': 'message', 'text': 'Test'},
            'tone': 'supportive'
        },
        'input': {'type': 'text'},
        'current_field': {'field': 'test', 'label': 'Test', 'value': None}
    })
    
    mock_response = {
        'completion': [
            {'chunk': {'bytes': valid_response.encode('utf-8')}}
        ]
    }
    
    mock_bedrock_client.invoke_agent.return_value = mock_response
    
    # Create event with session config (new session)
    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'message': 'Initial message',
            'sessionId': 'new-session-123',
            'agentId': 'test-agent-id',
            'agentAliasId': 'test-alias-id',
            'sessionConfig': {
                'hat': 'onboarding',
                'responsibilities': ['Collect name'],
                'data_to_be_collected': ['name'],
                'data_to_be_calculated': [],
                'notes': [],
                'initial_message': 'Initial message'
            }
        })
    }
    
    with patch.object(index, 'verify_supabase_token') as mock_verify:
        mock_verify.return_value = {'sub': user_id, 'email': 'test@test.com', 'role': 'authenticated'}
        
        with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
            response = index.lambda_handler(event, {})
            
            # Verify response is successful
            assert response['statusCode'] == 200
            
            # Property: Session attributes should have retry count = 0
            call_args = mock_bedrock_client.invoke_agent.call_args
            session_attrs = call_args[1]['sessionState']['sessionAttributes']
            assert 'json_validation_retry_count' in session_attrs
            assert session_attrs['json_validation_retry_count'] == '0'


def test_property_session_preserved_during_retries():
    """
    Property 9: JSON Validation Retry Mechanism (Session Preservation)
    
    For any retry, verify session attributes are preserved and only retry count is updated.
    """
    user_id = 'test-user-123'
    session_id = 'test-session-123'
    
    # Mock Bedrock client
    mock_bedrock_client = MagicMock()
    
    # Invalid response
    invalid_response = json.dumps({'missing': 'fields'})
    
    # Valid response
    valid_response = json.dumps({
        'status_of_aim': 'in_progress',
        'ui': {
            'render': {'type': 'message', 'text': 'Test'},
            'tone': 'supportive'
        },
        'input': {'type': 'text'},
        'current_field': {'field': 'test', 'label': 'Test', 'value': None}
    })
    
    # Session attributes to preserve
    session_attrs = {
        'user_id': user_id,
        'auth_header_name': 'X-Amigo-Auth',
        'hat': 'onboarding',
        'responsibilities': json.dumps(['Collect name']),
        'data_to_be_collected': json.dumps(['name']),
        'data_to_be_calculated': json.dumps([]),
        'notes': json.dumps([]),
        'data_collected': json.dumps({}),
        'json_validation_retry_count': '0'
    }
    
    mock_response_invalid = {
        'completion': [
            {
                'chunk': {'bytes': invalid_response.encode('utf-8')},
                'sessionState': {'sessionAttributes': session_attrs.copy()}
            }
        ]
    }
    
    mock_response_valid = {
        'completion': [
            {
                'chunk': {'bytes': valid_response.encode('utf-8')}
            }
        ]
    }
    
    mock_bedrock_client.invoke_agent.side_effect = [mock_response_invalid, mock_response_valid]
    
    # Create event
    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'message': 'Test message',
            'sessionId': session_id,
            'agentId': 'test-agent-id',
            'agentAliasId': 'test-alias-id'
        })
    }
    
    with patch.object(index, 'verify_supabase_token') as mock_verify:
        mock_verify.return_value = {'sub': user_id, 'email': 'test@test.com', 'role': 'authenticated'}
        
        with patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client):
            response = index.lambda_handler(event, {})
            
            # Verify retry was attempted
            assert mock_bedrock_client.invoke_agent.call_count == 2
            
            # Property: Immutable fields should be preserved in retry
            retry_call_args = mock_bedrock_client.invoke_agent.call_args_list[1]
            retry_session_attrs = retry_call_args[1]['sessionState']['sessionAttributes']
            
            # Check immutable fields are preserved
            assert retry_session_attrs['user_id'] == user_id
            assert retry_session_attrs['hat'] == 'onboarding'
            assert retry_session_attrs['responsibilities'] == json.dumps(['Collect name'])
            
            # Check retry count was incremented
            assert retry_session_attrs['json_validation_retry_count'] == '1'


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

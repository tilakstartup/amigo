"""
Property-based tests for session attributes initialization.

Property 2: Session Attributes Initialization
Validates: Requirements 2.1, 2.4, 2.5
"""

import json
import os
import pytest
from hypothesis import given, strategies as st, settings
from unittest.mock import MagicMock, patch
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


_FREE_AGENT_ENV = {
    'BEDROCK_FREE_AGENT_ID': 'free-agent-id',
    'BEDROCK_FREE_AGENT_ALIAS_ID': 'free-alias-id',
}

_MOCK_USER_PAYLOAD = lambda uid: {
    'sub': uid,
    'subscription_status': 'free',
    'is_active': True,
    'credits': {},
}

_VALID_AGENT_RESPONSE = b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Test"}, "tone": "supportive"}, "input": {"type": "text"}, "previous_field_collected": null}'


@st.composite
def session_config_strategy(draw):
    hat = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    responsibilities = draw(st.lists(st.text(min_size=1, max_size=100), min_size=1, max_size=10))
    data_to_be_collected = draw(st.lists(
        st.text(min_size=1, max_size=50, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        )), min_size=1, max_size=20
    ))
    data_to_be_calculated = draw(st.lists(
        st.text(min_size=1, max_size=50, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        )), min_size=0, max_size=10
    ))
    notes = draw(st.lists(st.text(min_size=1, max_size=200), min_size=0, max_size=5))
    initial_message = draw(st.text(min_size=1, max_size=500))
    return {
        'hat': hat,
        'responsibilities': responsibilities,
        'data_to_be_collected': data_to_be_collected,
        'data_to_be_calculated': data_to_be_calculated,
        'notes': notes,
        'initial_message': initial_message,
    }


@st.composite
def user_id_strategy(draw):
    return draw(st.text(min_size=1, max_size=100, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))


@given(session_config=session_config_strategy(), user_id=user_id_strategy())
@settings(max_examples=20, deadline=None)
def test_property_session_attributes_initialization(session_config, user_id):
    """
    Property 2: Session Attributes Initialization

    For any new session with a session config, verify:
    1. All required fields are present in session attributes
    2. data_collected is initialized as empty object {}
    3. json_validation_retry_count is initialized to 0
    4. Config fields are properly stored (lists as JSON strings)
    """
    mock_bedrock_client = MagicMock()
    mock_bedrock_client.invoke_agent.return_value = {
        'completion': [{'chunk': {'bytes': _VALID_AGENT_RESPONSE}}]
    }

    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': f'Bearer test-token-{user_id}'},
        'body': json.dumps({
            'mode': 'agent',
            'message': session_config['initial_message'],
            'sessionId': f'test-session-{user_id}',
            'sessionConfig': session_config,
        }),
    }

    with patch.object(index, 'verify_supabase_token', return_value=_MOCK_USER_PAYLOAD(user_id)), \
         patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client), \
         patch.dict(os.environ, _FREE_AGENT_ENV):
        response = index.lambda_handler(event, {})

    assert response['statusCode'] == 200

    call_args = mock_bedrock_client.invoke_agent.call_args
    session_attributes = call_args[1]['sessionState']['sessionAttributes']

    required_fields = [
        'user_id', 'auth_header_name', 'hat', 'responsibilities',
        'data_to_be_collected', 'data_to_be_calculated', 'notes',
        'data_collected', 'json_validation_retry_count',
    ]
    for field in required_fields:
        assert field in session_attributes, f"Required field '{field}' missing"

    assert session_attributes['user_id'] == user_id
    assert session_attributes['auth_header_name'] == 'X-Amigo-Auth'
    assert session_attributes['hat'] == session_config['hat']
    assert json.loads(session_attributes['responsibilities']) == session_config['responsibilities']
    assert json.loads(session_attributes['data_to_be_collected']) == session_config['data_to_be_collected']
    assert json.loads(session_attributes['data_to_be_calculated']) == session_config['data_to_be_calculated']
    assert json.loads(session_attributes['notes']) == session_config['notes']

    data_collected = json.loads(session_attributes['data_collected'])
    assert data_collected == {}, f"data_collected should be empty, got: {data_collected}"

    retry_count = int(session_attributes['json_validation_retry_count'])
    assert retry_count == 0, f"json_validation_retry_count should be 0, got: {retry_count}"

    assert 'initial_message' not in session_attributes
    assert call_args[1]['inputText'] == session_config['initial_message']


def test_session_attributes_without_config():
    """
    Test that session attributes are created even without session config.
    """
    user_id = 'test-user-123'

    mock_bedrock_client = MagicMock()
    mock_bedrock_client.invoke_agent.return_value = {
        'completion': [{'chunk': {'bytes': _VALID_AGENT_RESPONSE}}]
    }

    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': f'Bearer test-token-{user_id}'},
        'body': json.dumps({
            'mode': 'agent',
            'message': 'Follow-up message',
            'sessionId': f'test-session-{user_id}',
            'sessionConfig': None,
        }),
    }

    with patch.object(index, 'verify_supabase_token', return_value=_MOCK_USER_PAYLOAD(user_id)), \
         patch.object(index, 'bedrock_agent_runtime', mock_bedrock_client), \
         patch.dict(os.environ, _FREE_AGENT_ENV):
        response = index.lambda_handler(event, {})

    assert response['statusCode'] == 200

    call_args = mock_bedrock_client.invoke_agent.call_args
    session_attributes = call_args[1]['sessionState']['sessionAttributes']

    assert 'user_id' in session_attributes
    assert 'auth_header_name' in session_attributes
    assert session_attributes['user_id'] == user_id
    assert session_attributes['auth_header_name'] == 'X-Amigo-Auth'

    # Config-specific fields should NOT be present without session config
    assert 'data_collected' not in session_attributes
    assert 'json_validation_retry_count' not in session_attributes


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

"""
Unit tests for subscription caching in Bedrock session attributes.

Feature: subscription-tiered-agents
Requirements: 8.1, 8.2, 8.3
"""

import json
import sys
import os
from unittest.mock import patch, MagicMock

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index

_AGENT_RESPONSE = {
    'completion': [
        {'chunk': {'bytes': b'{"status_of_aim":"in_progress","ui":{"render":{"type":"message","text":"Hi"}},"input":{"type":"text"},"previous_field_collected":null}'}}
    ]
}

_ENV = {
    "BEDROCK_PRO_AGENT_ID": "pro-id",
    "BEDROCK_PRO_AGENT_ALIAS_ID": "pro-alias",
    "BEDROCK_FREE_AGENT_ID": "free-id",
    "BEDROCK_FREE_AGENT_ALIAS_ID": "free-alias",
}


def _make_event(body: dict, token: str = "tok") -> dict:
    return {
        'httpMethod': 'POST',
        'headers': {'Authorization': f'Bearer {token}'},
        'body': json.dumps(body),
    }


def _call(event, subscription_status='free', is_active=True):
    mock_client = MagicMock()
    mock_client.invoke_agent.return_value = _AGENT_RESPONSE
    with patch.object(index, 'verify_supabase_token', return_value={
        'sub': 'user-1', 'subscription_status': subscription_status, 'is_active': is_active, 'credits': {}
    }), patch.object(index, 'bedrock_agent_runtime', mock_client), patch.dict(os.environ, _ENV):
        response = index.lambda_handler(event, {})
    return response, mock_client


def _first_turn_event(status='free'):
    return _make_event({
        'mode': 'agent',
        'message': 'hello',
        'sessionId': 'sess-init',
        'sessionConfig': {'hat': 'general', 'responsibilities': [], 'data_to_be_collected': [],
                          'data_to_be_calculated': [], 'notes': [], 'initial_message': 'hello'},
    })


def test_first_turn_writes_subscription_status_to_session_attrs():
    """Req 8.1: First turn writes subscription_status to session_attributes."""
    event = _first_turn_event()
    _, mock_client = _call(event, subscription_status='pro', is_active=True)

    attrs = mock_client.invoke_agent.call_args[1]['sessionState']['sessionAttributes']
    assert attrs['subscription_status'] == 'pro'
    assert attrs['subscription_is_active'] == 'true'


def test_first_turn_writes_free_subscription_to_session_attrs():
    """Req 8.1: First turn writes free subscription_status to session_attributes."""
    event = _first_turn_event()
    _, mock_client = _call(event, subscription_status='free', is_active=True)

    attrs = mock_client.invoke_agent.call_args[1]['sessionState']['sessionAttributes']
    assert attrs['subscription_status'] == 'free'
    assert attrs['subscription_is_active'] == 'true'


def test_first_turn_inactive_writes_false_to_session_attrs():
    """Req 8.1: is_active=False is stored as 'false' string."""
    event = _first_turn_event()
    _, mock_client = _call(event, subscription_status='pro', is_active=False)

    attrs = mock_client.invoke_agent.call_args[1]['sessionState']['sessionAttributes']
    assert attrs['subscription_is_active'] == 'false'


def test_subsequent_turn_reads_subscription_from_session_attrs():
    """Req 8.2: Subsequent turn reads subscription from sessionAttributes in body."""
    event = _make_event({
        'mode': 'agent',
        'message': 'follow-up',
        'sessionId': 'sess-2',
        'sessionAttributes': {
            'subscription_status': 'pro',
            'subscription_is_active': 'true',
        },
    })
    # JWT returns 'free' — session attrs should take precedence for routing
    _, mock_client = _call(event, subscription_status='free', is_active=False)

    used_agent_id = mock_client.invoke_agent.call_args[1]['agentId']
    assert used_agent_id == 'pro-id', "Session-cached pro subscription should route to pro agent"


def test_subsequent_turn_missing_keys_triggers_re_extraction():
    """Req 8.3: Missing subscription keys in session attrs triggers re-extraction from JWT."""
    event = _make_event({
        'mode': 'agent',
        'message': 'follow-up',
        'sessionId': 'sess-3',
        # No sessionAttributes — missing keys
    })
    _, mock_client = _call(event, subscription_status='pro', is_active=True)

    # Should fall back to JWT-derived values and route to pro
    used_agent_id = mock_client.invoke_agent.call_args[1]['agentId']
    assert used_agent_id == 'pro-id'

    # And should write them into session attrs for next turn
    attrs = mock_client.invoke_agent.call_args[1]['sessionState']['sessionAttributes']
    assert attrs.get('subscription_status') == 'pro'
    assert attrs.get('subscription_is_active') == 'true'


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

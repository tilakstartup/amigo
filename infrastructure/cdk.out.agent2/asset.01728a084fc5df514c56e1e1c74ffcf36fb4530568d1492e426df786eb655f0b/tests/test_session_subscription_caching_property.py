"""
Property-based tests for subscription caching in Bedrock session attributes.

Feature: subscription-tiered-agents

Property 11: Subscription cached in session attributes on init
  On first turn (sessionConfig present), subscription_status and
  subscription_is_active are written into session_attributes.
  Validates: Requirement 8.1

Property 12: Subscription read from session attributes on subsequent turns
  On subsequent turns (no sessionConfig), subscription is read from
  sessionAttributes passed in the request body.
  Validates: Requirement 8.2
"""

import json
import sys
import os
import time
from unittest.mock import patch, MagicMock

import pytest
from hypothesis import given, strategies as st, settings

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


# ---------------------------------------------------------------------------
# Property 11: Subscription cached in session attributes on init
# ---------------------------------------------------------------------------

@given(
    status=st.sampled_from(['free', 'pro']),
    is_active=st.booleans(),
)
@settings(max_examples=20, deadline=None)
def test_property_11_subscription_cached_on_init(status, is_active):
    """
    Property 11: On first turn, subscription_status and subscription_is_active
    are written into session_attributes.
    Validates: Requirement 8.1
    """
    event = _make_event({
        'mode': 'agent',
        'message': 'hello',
        'sessionId': 'sess-1',
        'sessionConfig': {'hat': 'general', 'responsibilities': [], 'data_to_be_collected': [],
                          'data_to_be_calculated': [], 'notes': [], 'initial_message': 'hello'},
    })
    _, mock_client = _call(event, subscription_status=status, is_active=is_active)

    call_args = mock_client.invoke_agent.call_args[1]
    attrs = call_args['sessionState']['sessionAttributes']

    assert attrs.get('subscription_status') == status, (
        f"Expected subscription_status={status!r}, got {attrs.get('subscription_status')!r}"
    )
    expected_active = 'true' if is_active else 'false'
    assert attrs.get('subscription_is_active') == expected_active, (
        f"Expected subscription_is_active={expected_active!r}, got {attrs.get('subscription_is_active')!r}"
    )


# ---------------------------------------------------------------------------
# Property 12: Subscription read from session attributes on subsequent turns
# ---------------------------------------------------------------------------

@given(
    status=st.sampled_from(['free', 'pro']),
    is_active=st.booleans(),
)
@settings(max_examples=20, deadline=None)
def test_property_12_subscription_read_from_session_on_subsequent_turn(status, is_active):
    """
    Property 12: On subsequent turns, subscription is read from sessionAttributes
    in the request body (not re-parsed from JWT).
    Validates: Requirement 8.2
    """
    active_str = 'true' if is_active else 'false'
    event = _make_event({
        'mode': 'agent',
        'message': 'follow-up',
        'sessionId': 'sess-2',
        # No sessionConfig — subsequent turn
        'sessionAttributes': {
            'subscription_status': status,
            'subscription_is_active': active_str,
        },
    })
    # JWT returns opposite values to confirm session attrs take precedence
    opposite_status = 'pro' if status == 'free' else 'free'
    _, mock_client = _call(event, subscription_status=opposite_status, is_active=not is_active)

    assert mock_client.invoke_agent.called
    # The routing should use the session-cached values, not the JWT values
    call_args = mock_client.invoke_agent.call_args[1]
    used_agent_id = call_args['agentId']

    if status == 'pro' and is_active:
        assert used_agent_id == 'pro-id', f"Expected pro-id, got {used_agent_id}"
    else:
        assert used_agent_id == 'free-id', f"Expected free-id, got {used_agent_id}"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

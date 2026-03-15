"""
Property-based tests for agent routing and subscription status in response.

Feature: subscription-tiered-agents

Property 4: Agent routing correctness
  For any (subscription_status, is_active) pair, _resolve_agent returns the
  correct agent ID and alias ID from env vars.
  Validates: Requirements 4.1, 4.2, 4.4

Property 5: Subscription status included in response
  _build_success_response always includes subscription_status in the returned dict.
  Validates: Requirement 4.5
"""

import sys
import os
from unittest.mock import patch

import pytest
from hypothesis import given, strategies as st, settings

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index

_PRO_AGENT_ID = "pro-agent-id"
_PRO_ALIAS_ID = "pro-alias-id"
_FREE_AGENT_ID = "free-agent-id"
_FREE_ALIAS_ID = "free-alias-id"

_ENV = {
    "BEDROCK_PRO_AGENT_ID": _PRO_AGENT_ID,
    "BEDROCK_PRO_AGENT_ALIAS_ID": _PRO_ALIAS_ID,
    "BEDROCK_FREE_AGENT_ID": _FREE_AGENT_ID,
    "BEDROCK_FREE_AGENT_ALIAS_ID": _FREE_ALIAS_ID,
}


def _resolve(status, active):
    with patch.dict(os.environ, _ENV):
        return index._resolve_agent(status, active)


# ---------------------------------------------------------------------------
# Property 4: Agent routing correctness
# ---------------------------------------------------------------------------

@given(is_active=st.booleans())
@settings(max_examples=20, deadline=None)
def test_property_4_pro_active_routes_to_pro(is_active):
    """
    Property 4 (pro path): pro + active=True always routes to pro agent.
    Validates: Requirements 4.1, 4.4
    """
    agent_id, alias_id = _resolve("pro", True)
    assert agent_id == _PRO_AGENT_ID
    assert alias_id == _PRO_ALIAS_ID


@given(is_active=st.booleans())
@settings(max_examples=20, deadline=None)
def test_property_4_free_always_routes_to_free(is_active):
    """
    Property 4 (free path): free status always routes to free agent regardless of is_active.
    Validates: Requirements 4.2, 4.4
    """
    agent_id, alias_id = _resolve("free", is_active)
    assert agent_id == _FREE_AGENT_ID
    assert alias_id == _FREE_ALIAS_ID


def test_property_4_pro_inactive_routes_to_free():
    """
    Property 4 (inactive pro): pro + active=False routes to free agent.
    Validates: Requirement 4.2
    """
    agent_id, alias_id = _resolve("pro", False)
    assert agent_id == _FREE_AGENT_ID
    assert alias_id == _FREE_ALIAS_ID


# ---------------------------------------------------------------------------
# Property 5: Subscription status included in response
# ---------------------------------------------------------------------------

@given(status=st.sampled_from(["free", "pro"]))
@settings(max_examples=20, deadline=None)
def test_property_5_subscription_status_in_response(status):
    """
    Property 5: subscription_status is always present in _build_success_response output.
    Validates: Requirement 4.5
    """
    result = index._build_success_response(
        completion=None,
        data_collected={},
        invocation_id=None,
        user_id="user-1",
        subscription_status=status,
    )
    assert "subscription_status" in result
    assert result["subscription_status"] == status


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

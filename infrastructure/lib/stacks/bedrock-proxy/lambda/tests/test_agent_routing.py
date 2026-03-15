"""
Unit tests for _resolve_agent() and subscription-aware routing.

Feature: subscription-tiered-agents
Requirements: 4.1–4.5
"""

import sys
import os
from unittest.mock import patch

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index

_ENV = {
    "BEDROCK_PRO_AGENT_ID": "pro-agent-id",
    "BEDROCK_PRO_AGENT_ALIAS_ID": "pro-alias-id",
    "BEDROCK_FREE_AGENT_ID": "free-agent-id",
    "BEDROCK_FREE_AGENT_ALIAS_ID": "free-alias-id",
}


def _resolve(status, active):
    with patch.dict(os.environ, _ENV):
        return index._resolve_agent(status, active)


def test_pro_active_routes_to_pro_agent():
    """Req 4.1: pro + is_active=True → pro agent IDs."""
    agent_id, alias_id = _resolve("pro", True)
    assert agent_id == "pro-agent-id"
    assert alias_id == "pro-alias-id"


def test_free_routes_to_free_agent():
    """Req 4.2: free → free agent IDs."""
    agent_id, alias_id = _resolve("free", True)
    assert agent_id == "free-agent-id"
    assert alias_id == "free-alias-id"


def test_pro_inactive_routes_to_free_agent():
    """Req 4.2: pro + is_active=False → free agent IDs."""
    agent_id, alias_id = _resolve("pro", False)
    assert agent_id == "free-agent-id"
    assert alias_id == "free-alias-id"


def test_free_inactive_routes_to_free_agent():
    """Req 4.2: free + is_active=False → free agent IDs."""
    agent_id, alias_id = _resolve("free", False)
    assert agent_id == "free-agent-id"
    assert alias_id == "free-alias-id"


def test_subscription_status_in_success_response():
    """Req 4.5: subscription_status is present in success response."""
    result = index._build_success_response(
        completion={"status_of_aim": "in_progress"},
        data_collected={},
        invocation_id="inv-1",
        user_id="user-abc",
        subscription_status="pro",
    )
    assert result["subscription_status"] == "pro"


def test_subscription_status_free_in_response():
    """Req 4.5: subscription_status 'free' is present in success response."""
    result = index._build_success_response(
        completion=None,
        data_collected={},
        invocation_id=None,
        user_id="user-xyz",
        subscription_status="free",
    )
    assert result["subscription_status"] == "free"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

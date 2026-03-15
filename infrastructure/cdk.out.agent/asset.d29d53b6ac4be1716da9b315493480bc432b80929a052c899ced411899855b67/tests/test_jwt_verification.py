"""
Unit tests for JWT verification in verify_supabase_token.

Feature: subscription-tiered-agents
Requirements: 3.1–3.8
"""

import json
import sys
import os
import time
from unittest.mock import patch

import pytest
from cryptography.hazmat.primitives.asymmetric.ec import generate_private_key, SECP256R1
import jwt

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index

# ---------------------------------------------------------------------------
# Shared key fixtures
# ---------------------------------------------------------------------------

_PRIVATE_KEY = generate_private_key(SECP256R1())
_KID = "unit-test-kid"
_PUBLIC_KEY_OBJ = jwt.algorithms.ECAlgorithm.from_jwk(
    json.dumps({
        "kty": "EC",
        "crv": "P-256",
        "kid": _KID,
        **json.loads(jwt.algorithms.ECAlgorithm.to_jwk(_PRIVATE_KEY.public_key())),
    })
)

_OTHER_PRIVATE_KEY = generate_private_key(SECP256R1())


def _make_jwt(payload: dict, kid: str = _KID, private_key=None) -> str:
    if private_key is None:
        private_key = _PRIVATE_KEY
    return jwt.encode(payload, private_key, algorithm="ES256", headers={"kid": kid, "alg": "ES256"})


def _base_payload(exp_offset: int = 3600) -> dict:
    now = int(time.time())
    return {"sub": "user-abc", "iat": now, "exp": now + exp_offset}


def _call(token: str):
    with patch.object(index, "_JWT_PUBLIC_KEY", _PUBLIC_KEY_OBJ), \
         patch.object(index, "_JWT_KID", _KID):
        return index.verify_supabase_token(token)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_valid_jwt_accepted():
    """Req 3.1: Valid JWT with correct key/kid returns payload dict."""
    token = _make_jwt({**_base_payload(), "user_subscription": {"subscription_status": "pro", "is_active": True, "credits": {}}})
    result = _call(token)
    assert result is not None
    assert result["sub"] == "user-abc"
    assert result["subscription_status"] == "pro"
    assert result["is_active"] is True


def test_expired_jwt_rejected():
    """Req 3.4: Expired JWT returns None."""
    token = _make_jwt(_base_payload(exp_offset=-1))
    result = _call(token)
    assert result is None


def test_wrong_algorithm_rejected():
    """Req 3.5: JWT signed with HS256 (wrong algorithm) returns None."""
    payload = _base_payload()
    token = jwt.encode(payload, "secret", algorithm="HS256", headers={"kid": _KID})
    result = _call(token)
    assert result is None


def test_missing_user_subscription_defaults_to_free():
    """Req 3.8: Missing user_subscription claim defaults to free/True/{}."""
    token = _make_jwt(_base_payload())
    result = _call(token)
    assert result is not None
    assert result["subscription_status"] == "free"
    assert result["is_active"] is True
    assert result["credits"] == {}


def test_kid_mismatch_returns_none():
    """Req 3.2, 3.3: JWT with wrong kid returns None."""
    token = _make_jwt(_base_payload(), kid="wrong-kid")
    result = _call(token)
    assert result is None


def test_wrong_key_returns_none():
    """Req 3.4: JWT signed with a different EC key returns None."""
    token = _make_jwt(_base_payload(), private_key=_OTHER_PRIVATE_KEY)
    result = _call(token)
    assert result is None


def test_partial_user_subscription_uses_defaults():
    """Req 3.6, 3.8: Partial user_subscription claim fills missing fields with defaults."""
    token = _make_jwt({**_base_payload(), "user_subscription": {"subscription_status": "pro"}})
    result = _call(token)
    assert result is not None
    assert result["subscription_status"] == "pro"
    assert result["is_active"] is True   # default
    assert result["credits"] == {}       # default


def test_sub_extracted():
    """Req 3.7: sub field is extracted from payload."""
    payload = {**_base_payload(), "sub": "user-xyz-999"}
    token = _make_jwt(payload)
    result = _call(token)
    assert result is not None
    assert result["sub"] == "user-xyz-999"


def test_malformed_token_returns_none():
    """Req 3.1: Completely malformed token string returns None."""
    result = _call("not.a.jwt")
    assert result is None


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

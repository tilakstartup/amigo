"""
Property-based tests for JWT verification in verify_supabase_token.

Feature: subscription-tiered-agents

Property 1: JWT kid mismatch is rejected
  For any JWT whose kid header field does not match the loaded _JWT_KID,
  verify_supabase_token shall return None.
  Validates: Requirements 3.2, 3.3

Property 2: JWT signature verification correctness
  For any JWT signed with a different EC key, verify_supabase_token shall return None.
  Validates: Requirements 3.4, 3.5

Property 3: Subscription claim extraction
  For any valid JWT with arbitrary user_subscription claim values,
  verify_supabase_token returns the correct subscription_status, is_active, credits.
  Validates: Requirements 3.6, 3.8
"""

import json
import sys
import os
import time
from unittest.mock import patch

import pytest
from hypothesis import given, strategies as st, settings, assume

from cryptography.hazmat.primitives.asymmetric.ec import generate_private_key, SECP256R1
import jwt

# Add parent directory to path to import index
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index

# ---------------------------------------------------------------------------
# Test key pair — generated once at module load
# ---------------------------------------------------------------------------

_TEST_PRIVATE_KEY = generate_private_key(SECP256R1())
_TEST_KID = "test-kid-abc123"

# Build the public key object the same way index._load_jwt_public_key() does
_TEST_PUBLIC_KEY_OBJ = jwt.algorithms.ECAlgorithm.from_jwk(
    json.dumps({
        "kty": "EC",
        "crv": "P-256",
        "kid": _TEST_KID,
        **json.loads(jwt.algorithms.ECAlgorithm.to_jwk(_TEST_PRIVATE_KEY.public_key())),
    })
)


def _make_jwt(payload: dict, kid: str = _TEST_KID, private_key=None) -> str:
    """Sign a JWT with ES256 using the given (or default test) private key."""
    if private_key is None:
        private_key = _TEST_PRIVATE_KEY
    return jwt.encode(payload, private_key, algorithm="ES256", headers={"kid": kid, "alg": "ES256"})


def _base_payload() -> dict:
    """Minimal valid JWT payload (not expired)."""
    return {
        "sub": "user-test",
        "iat": int(time.time()),
        "exp": int(time.time()) + 3600,
    }


def _call(token: str):
    """Invoke verify_supabase_token with the test key/kid patched in."""
    with patch.object(index, "_JWT_PUBLIC_KEY", _TEST_PUBLIC_KEY_OBJ), \
         patch.object(index, "_JWT_KID", _TEST_KID):
        return index.verify_supabase_token(token)


# ---------------------------------------------------------------------------
# Property 1: JWT kid mismatch is rejected
# Feature: subscription-tiered-agents, Property 1: JWT kid mismatch is rejected
# ---------------------------------------------------------------------------

@given(mismatched_kid=st.text(min_size=1, max_size=100))
@settings(max_examples=20, deadline=None)
def test_property_1_kid_mismatch_rejected(mismatched_kid):
    """
    Property 1: JWT kid mismatch is rejected
    Validates: Requirements 3.2, 3.3

    For any JWT signed with the correct key but with a kid that differs from
    the loaded _JWT_KID, verify_supabase_token returns None.
    """
    assume(mismatched_kid != _TEST_KID)

    token = _make_jwt(_base_payload(), kid=mismatched_kid)
    result = _call(token)

    assert result is None, (
        f"Expected None for kid mismatch, "
        f"mismatched_kid={mismatched_kid!r}, expected_kid={_TEST_KID!r}, got={result!r}"
    )


# ---------------------------------------------------------------------------
# Property 2: JWT signature verification correctness
# Feature: subscription-tiered-agents, Property 2: JWT signature verification correctness
# ---------------------------------------------------------------------------

@given(sub=st.text(min_size=1, max_size=50))
@settings(max_examples=20, deadline=None)
def test_property_2_wrong_key_rejected(sub):
    """
    Property 2: JWT signature verification correctness
    Validates: Requirements 3.4, 3.5

    For any JWT signed with a DIFFERENT EC key (not the loaded one),
    verify_supabase_token returns None.
    """
    wrong_private_key = generate_private_key(SECP256R1())

    payload = {**_base_payload(), "sub": sub}
    # Use the correct kid so the kid check passes — only the signature is wrong
    token = _make_jwt(payload, kid=_TEST_KID, private_key=wrong_private_key)
    result = _call(token)

    assert result is None, (
        f"Expected None for wrong-key JWT, sub={sub!r}, got={result!r}"
    )


# ---------------------------------------------------------------------------
# Property 3: Subscription claim extraction
# Feature: subscription-tiered-agents, Property 3: Subscription claim extraction
# ---------------------------------------------------------------------------

_credits_strategy = st.dictionaries(
    keys=st.text(
        min_size=1,
        max_size=20,
        alphabet=st.characters(
            whitelist_categories=("Ll", "Lu", "Nd"),
            whitelist_characters="_",
        ),
    ),
    values=st.text(min_size=1, max_size=20),
    max_size=5,
)


@given(
    status=st.sampled_from(["free", "pro"]),
    is_active=st.booleans(),
    credits=_credits_strategy,
)
@settings(max_examples=20, deadline=None)
def test_property_3_subscription_claim_extraction(status, is_active, credits):
    """
    Property 3: Subscription claim extraction
    Validates: Requirements 3.6, 3.8

    For any valid JWT with arbitrary user_subscription claim values,
    verify_supabase_token returns the correct subscription_status, is_active, credits.
    """
    payload = {
        **_base_payload(),
        "user_subscription": {
            "subscription_status": status,
            "is_active": is_active,
            "credits": credits,
        },
    }
    token = _make_jwt(payload)
    result = _call(token)

    assert result is not None, "Expected non-None result for valid JWT"
    assert result["subscription_status"] == status, (
        f"subscription_status mismatch: expected {status!r}, got {result['subscription_status']!r}"
    )
    assert result["is_active"] == is_active, (
        f"is_active mismatch: expected {is_active!r}, got {result['is_active']!r}"
    )
    assert result["credits"] == credits, (
        f"credits mismatch: expected {credits!r}, got {result['credits']!r}"
    )


def test_property_3_missing_user_subscription_defaults():
    """
    Property 3 (edge case): Missing user_subscription claim defaults to free/True/{}.
    Validates: Requirement 3.8
    """
    token = _make_jwt(_base_payload())
    result = _call(token)

    assert result is not None, "Expected non-None result for valid JWT without user_subscription"
    assert result["subscription_status"] == "free"
    assert result["is_active"] is True
    assert result["credits"] == {}


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

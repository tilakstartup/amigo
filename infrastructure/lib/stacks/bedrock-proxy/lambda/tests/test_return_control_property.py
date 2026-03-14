"""
Property-based tests for return control invocation format.

Property 5: Return Control Invocation Format
Validates: Requirement 3.6

For any returnControlInvocationResults request:
- Lambda invocation does NOT include inputText parameter
- Only session state with results is sent
- All function results are transformed to apiResult format
- invocationId is placed at sessionState level
"""

import json
import pytest
from hypothesis import given, strategies as st, settings, assume
from unittest.mock import MagicMock, patch
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


# ---------------------------------------------------------------------------
# Strategies
# ---------------------------------------------------------------------------

@st.composite
def function_name_strategy(draw):
    """Generate valid function names (snake_case)."""
    prefix = draw(st.sampled_from(['calculate_', 'save_', 'get_', 'update_', 'delete_', 'validate_']))
    suffix = draw(st.text(min_size=2, max_size=20, alphabet='abcdefghijklmnopqrstuvwxyz_'))
    return prefix + suffix


@st.composite
def action_group_strategy(draw):
    """Generate valid action group names."""
    return draw(st.sampled_from([
        'health_calculations', 'data_operations', 'goal_management'
    ]))


@st.composite
def function_result_strategy(draw):
    """Generate a single function result."""
    success = draw(st.booleans())
    return {
        'actionGroup': draw(action_group_strategy()),
        'functionName': draw(function_name_strategy()),
        'success': success,
        'result': draw(st.just('{}')) if success else None,
        'error': None if success else draw(st.text(min_size=1, max_size=100))
    }


@st.composite
def return_control_results_strategy(draw):
    """Generate a list of return control results (1-3 invocations, each with 1-3 function results)."""
    num_invocations = draw(st.integers(min_value=1, max_value=3))
    results = []
    for _ in range(num_invocations):
        num_functions = draw(st.integers(min_value=1, max_value=3))
        results.append({
            'invocationId': draw(st.text(min_size=5, max_size=30, alphabet='abcdefghijklmnopqrstuvwxyz0123456789-')),
            'functionResults': [draw(function_result_strategy()) for _ in range(num_functions)]
        })
    return results


def _make_agent_response():
    mock_response = MagicMock()
    mock_response.get.return_value = [
        {'chunk': {'bytes': b'{"status_of_aim":"in_progress","ui":{"render":{"type":"message","text":"ok"},"tone":"friendly"},"input":{"type":"text"},"current_field":{"field":"name","label":"Name","value":null}}'}}
    ]
    return mock_response


# ---------------------------------------------------------------------------
# Property 5: Return Control Invocation Format
# ---------------------------------------------------------------------------

@given(return_control_results=return_control_results_strategy())
@settings(max_examples=50, deadline=None)
@patch('index.verify_supabase_token')
@patch('index.bedrock_agent_runtime')
def test_property_5_no_input_text_with_return_control(mock_bedrock, mock_verify, return_control_results):
    """
    Property 5: Return Control Invocation Format
    Validates: Requirement 3.6

    For ANY returnControlInvocationResults request, Lambda must NEVER include
    inputText in the invoke_agent call.
    """
    mock_verify.return_value = {'sub': 'user-abc', 'email': 'test@example.com', 'role': 'authenticated'}
    mock_bedrock.invoke_agent.return_value = _make_agent_response()

    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'agentId': 'AGENT123',
            'agentAliasId': 'ALIAS123',
            'sessionId': 'session-prop-test',
            'returnControlInvocationResults': return_control_results
        })
    }

    index.lambda_handler(event, {})

    call_kwargs = mock_bedrock.invoke_agent.call_args[1]

    # CRITICAL property: inputText must NEVER be present
    assert 'inputText' not in call_kwargs, \
        f"inputText must NOT be present when sending returnControlInvocationResults. Got keys: {list(call_kwargs.keys())}"


@given(return_control_results=return_control_results_strategy())
@settings(max_examples=50, deadline=None)
@patch('index.verify_supabase_token')
@patch('index.bedrock_agent_runtime')
def test_property_5_results_in_session_state(mock_bedrock, mock_verify, return_control_results):
    """
    Property 5: Results must be placed in sessionState, not at top level.
    Validates: Requirement 3.6
    """
    mock_verify.return_value = {'sub': 'user-abc', 'email': 'test@example.com', 'role': 'authenticated'}
    mock_bedrock.invoke_agent.return_value = _make_agent_response()

    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'agentId': 'AGENT123',
            'agentAliasId': 'ALIAS123',
            'sessionId': 'session-prop-test',
            'returnControlInvocationResults': return_control_results
        })
    }

    index.lambda_handler(event, {})

    call_kwargs = mock_bedrock.invoke_agent.call_args[1]
    session_state = call_kwargs.get('sessionState', {})

    # Results must be in sessionState
    assert 'returnControlInvocationResults' in session_state, \
        "returnControlInvocationResults must be in sessionState"

    # invocationId must be at sessionState level
    assert 'invocationId' in session_state, \
        "invocationId must be at sessionState level"


@given(return_control_results=return_control_results_strategy())
@settings(max_examples=50, deadline=None)
@patch('index.verify_supabase_token')
@patch('index.bedrock_agent_runtime')
def test_property_5_all_results_use_api_result_format(mock_bedrock, mock_verify, return_control_results):
    """
    Property 5: All function results must be transformed to apiResult format.
    Validates: Requirement 3.6
    """
    mock_verify.return_value = {'sub': 'user-abc', 'email': 'test@example.com', 'role': 'authenticated'}
    mock_bedrock.invoke_agent.return_value = _make_agent_response()

    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'agentId': 'AGENT123',
            'agentAliasId': 'ALIAS123',
            'sessionId': 'session-prop-test',
            'returnControlInvocationResults': return_control_results
        })
    }

    index.lambda_handler(event, {})

    call_kwargs = mock_bedrock.invoke_agent.call_args[1]
    session_state = call_kwargs.get('sessionState', {})
    bedrock_results = session_state.get('returnControlInvocationResults', [])

    # Count total function results from input
    total_input_functions = sum(
        len(item.get('functionResults', []))
        for item in return_control_results
    )

    # Every result must use apiResult format (default for all action groups)
    assert len(bedrock_results) == total_input_functions, \
        f"Expected {total_input_functions} results, got {len(bedrock_results)}"

    for result in bedrock_results:
        assert 'apiResult' in result, \
            f"All results must use apiResult format. Got: {list(result.keys())}"
        assert result['apiResult']['httpStatusCode'] == 200, \
            "HTTP status must always be 200"


@given(return_control_results=return_control_results_strategy())
@settings(max_examples=50, deadline=None)
@patch('index.verify_supabase_token')
@patch('index.bedrock_agent_runtime')
def test_property_5_api_path_derived_from_function_name(mock_bedrock, mock_verify, return_control_results):
    """
    Property 5: API path must be derived from function name (underscores → dashes).
    Validates: Requirement 3.6
    """
    mock_verify.return_value = {'sub': 'user-abc', 'email': 'test@example.com', 'role': 'authenticated'}
    mock_bedrock.invoke_agent.return_value = _make_agent_response()

    event = {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'agentId': 'AGENT123',
            'agentAliasId': 'ALIAS123',
            'sessionId': 'session-prop-test',
            'returnControlInvocationResults': return_control_results
        })
    }

    index.lambda_handler(event, {})

    call_kwargs = mock_bedrock.invoke_agent.call_args[1]
    session_state = call_kwargs.get('sessionState', {})
    bedrock_results = session_state.get('returnControlInvocationResults', [])

    # Collect all input function names
    input_functions = []
    for item in return_control_results:
        for func in item.get('functionResults', []):
            fn = func.get('functionName') or func.get('function_name', '')
            input_functions.append(fn)

    for i, result in enumerate(bedrock_results):
        api_path = result['apiResult']['apiPath']
        expected_path = '/' + input_functions[i].replace('_', '-')
        assert api_path == expected_path, \
            f"API path must be /{input_functions[i].replace('_', '-')}, got {api_path}"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

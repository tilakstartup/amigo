"""
Unit tests for return control invocation handling.

Tests for Requirement 3.6:
- Transformation from client format to Bedrock apiResult format
- Lambda invocation does NOT include inputText when sending return control results
- Session state preservation during return control flow
"""

import json
import pytest
import sys
import os
from unittest.mock import MagicMock, patch, call

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index


_FREE_AGENT_ENV = {
    'BEDROCK_FREE_AGENT_ID': 'free-agent-id',
    'BEDROCK_FREE_AGENT_ALIAS_ID': 'free-alias-id',
}


@pytest.fixture(autouse=True)
def patch_agent_env(monkeypatch):
    for k, v in _FREE_AGENT_ENV.items():
        monkeypatch.setenv(k, v)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_event(return_control_results, session_id='session-123'):
    """Build a Lambda event with returnControlInvocationResults."""
    return {
        'httpMethod': 'POST',
        'headers': {'Authorization': 'Bearer test-token'},
        'body': json.dumps({
            'mode': 'agent',
            'agentId': 'AGENT123',
            'agentAliasId': 'ALIAS123',
            'sessionId': session_id,
            'returnControlInvocationResults': return_control_results
        })
    }


def _make_agent_response(text='{"status_of_aim":"in_progress","ui":{"render":{"type":"message","text":"ok"},"tone":"friendly"},"input":{"type":"text"},"previous_field_collected":null}'):
    """Build a mock Bedrock agent response."""
    mock_response = MagicMock()
    mock_response.get.return_value = [
        {'chunk': {'bytes': text.encode('utf-8')}}
    ]
    return mock_response


def _make_user_payload():
    return {'sub': 'user-abc', 'subscription_status': 'free', 'is_active': True, 'credits': {}}


# ---------------------------------------------------------------------------
# 7.1 – Transformation from client format to Bedrock apiResult format
# ---------------------------------------------------------------------------

class TestReturnControlTransformation:
    """Tests for client → Bedrock format transformation (Requirement 3.6)."""

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_api_result_format_used_for_post_functions(self, mock_bedrock, mock_verify):
        """Non-GET functions must use apiResult format (not functionResult)."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-001',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': True,
                'result': '{"bmr": 1800}'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        session_state = call_kwargs['sessionState']
        results = session_state['returnControlInvocationResults']

        assert len(results) == 1
        assert 'apiResult' in results[0], "POST functions must use apiResult format"
        assert 'functionResult' not in results[0]

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_api_result_format_used_for_get_functions(self, mock_bedrock, mock_verify):
        """GET functions (starting with get_) must also use apiResult format."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-002',
            'functionResults': [{
                'actionGroup': 'data_operations',
                'functionName': 'get_user_profile',
                'success': True,
                'result': '{"name": "Alice"}'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        results = call_kwargs['sessionState']['returnControlInvocationResults']
        assert 'apiResult' in results[0]
        assert results[0]['apiResult']['httpMethod'] == 'GET'

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_function_name_converted_to_api_path(self, mock_bedrock, mock_verify):
        """Function name must be converted to API path (underscores → dashes, prefixed with /)."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-003',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': True,
                'result': '{}'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        results = call_kwargs['sessionState']['returnControlInvocationResults']
        assert results[0]['apiResult']['apiPath'] == '/calculate-bmr'

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_http_status_always_200(self, mock_bedrock, mock_verify):
        """HTTP status code must always be 200 (errors use responseState=FAILURE)."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-004',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': False,
                'error': 'Calculation failed'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        results = call_kwargs['sessionState']['returnControlInvocationResults']
        assert results[0]['apiResult']['httpStatusCode'] == 200

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_failure_sets_response_state(self, mock_bedrock, mock_verify):
        """Failed function results must set responseState=FAILURE."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-005',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': False,
                'error': 'Calculation failed'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        results = call_kwargs['sessionState']['returnControlInvocationResults']
        assert results[0]['apiResult'].get('responseState') == 'FAILURE'

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_success_does_not_set_response_state(self, mock_bedrock, mock_verify):
        """Successful function results must NOT set responseState."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-006',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': True,
                'result': '{"bmr": 1800}'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        results = call_kwargs['sessionState']['returnControlInvocationResults']
        assert 'responseState' not in results[0]['apiResult']

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_invocation_id_placed_at_session_state_level(self, mock_bedrock, mock_verify):
        """invocationId must be at sessionState level, not inside results."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-007',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': True,
                'result': '{}'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        session_state = call_kwargs['sessionState']
        assert session_state.get('invocationId') == 'inv-007'


# ---------------------------------------------------------------------------
# 7.1 – No inputText when sending return control results
# ---------------------------------------------------------------------------

class TestNoInputTextWithReturnControl:
    """Requirement 3.6: Lambda must NOT include inputText when sending return control results."""

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_no_input_text_when_return_control_results_present(self, mock_bedrock, mock_verify):
        """CRITICAL: inputText must NOT be in invoke_agent params when returnControlInvocationResults provided."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-008',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': True,
                'result': '{"bmr": 1800}'
            }]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        assert 'inputText' not in call_kwargs, \
            "inputText must NOT be present when sending returnControlInvocationResults"

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_input_text_present_for_normal_messages(self, mock_bedrock, mock_verify):
        """inputText must be present for normal message requests (no return control)."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = {
            'httpMethod': 'POST',
            'headers': {'Authorization': 'Bearer test-token'},
            'body': json.dumps({
                'mode': 'agent',
                'agentId': 'AGENT123',
                'agentAliasId': 'ALIAS123',
                'sessionId': 'session-123',
                'message': 'Hello'
            })
        }

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        assert 'inputText' in call_kwargs, "inputText must be present for normal messages"
        assert call_kwargs['inputText'] == 'Hello'


# ---------------------------------------------------------------------------
# 7.2 – Session state preservation during return control
# ---------------------------------------------------------------------------

class TestSessionStatePreservation:
    """Session state must be preserved during return control flow."""

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_session_id_preserved_in_return_control(self, mock_bedrock, mock_verify):
        """Session ID must be the same in return control invocation."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        session_id = 'my-session-xyz'
        event = _make_event([{
            'invocationId': 'inv-009',
            'functionResults': [{
                'actionGroup': 'health_calculations',
                'functionName': 'calculate_bmr',
                'success': True,
                'result': '{}'
            }]
        }], session_id=session_id)

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        assert call_kwargs['sessionId'] == session_id

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_multiple_function_results_all_transformed(self, mock_bedrock, mock_verify):
        """All function results in a single invocation must be transformed."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        event = _make_event([{
            'invocationId': 'inv-010',
            'functionResults': [
                {
                    'actionGroup': 'health_calculations',
                    'functionName': 'calculate_bmr',
                    'success': True,
                    'result': '{"bmr": 1800}'
                },
                {
                    'actionGroup': 'data_operations',
                    'functionName': 'save_profile',
                    'success': True,
                    'result': '{"saved": true}'
                }
            ]
        }])

        index.lambda_handler(event, {})

        call_kwargs = mock_bedrock.invoke_agent.call_args[1]
        results = call_kwargs['sessionState']['returnControlInvocationResults']
        assert len(results) == 2, "All function results must be transformed"

    @patch('index.verify_supabase_token')
    @patch('index.bedrock_agent_runtime')
    def test_snake_case_field_names_accepted(self, mock_bedrock, mock_verify):
        """Client may send snake_case field names (invocation_id, function_results)."""
        mock_verify.return_value = _make_user_payload()
        mock_bedrock.invoke_agent.return_value = _make_agent_response()

        # Client sends snake_case
        event = _make_event([{
            'invocation_id': 'inv-011',
            'function_results': [{
                'action_group': 'health_calculations',
                'function_name': 'calculate_bmr',
                'success': True,
                'result': '{}'
            }]
        }])

        response = index.lambda_handler(event, {})
        body = json.loads(response['body'])

        # Should succeed, not error
        assert response['statusCode'] == 200
        assert body.get('error') is None


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

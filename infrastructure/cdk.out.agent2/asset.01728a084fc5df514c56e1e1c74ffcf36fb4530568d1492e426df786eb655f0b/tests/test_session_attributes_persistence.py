"""
Unit tests for session attributes persistence handling.

Tests verify that:
1. Session attributes are retrieved from Bedrock on subsequent turns
2. Immutable fields are preserved across invocations
3. Only data_collected and json_validation_retry_count can be updated

Requirements: 2.6, 3.5, 10.1, 10.2, 10.3
"""

import json
import pytest
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from index import lambda_handler, _merge_session_attributes


_FREE_AGENT_ENV = {
    'BEDROCK_FREE_AGENT_ID': 'free-agent-id',
    'BEDROCK_FREE_AGENT_ALIAS_ID': 'free-alias-id',
}


@pytest.fixture(autouse=True)
def patch_agent_env(monkeypatch):
    for k, v in _FREE_AGENT_ENV.items():
        monkeypatch.setenv(k, v)


_MOCK_USER_PAYLOAD = {
    'sub': 'user-123',
    'subscription_status': 'free',
    'is_active': True,
    'credits': {},
}


class TestSessionAttributesMerging:
    """Test the _merge_session_attributes helper function"""
    
    def test_merge_preserves_immutable_fields(self):
        """Test that immutable fields are preserved when merging"""
        existing = {
            'user_id': 'user-123',
            'auth_header_name': 'X-Amigo-Auth',
            'hat': 'onboarding',
            'responsibilities': json.dumps(['Collect name', 'Collect age']),
            'data_to_be_collected': json.dumps(['name', 'age']),
            'data_to_be_calculated': json.dumps(['bmr']),
            'notes': json.dumps(['Important note']),
            'data_collected': json.dumps({}),
            'json_validation_retry_count': '0'
        }
        
        # Try to update immutable fields (should be ignored)
        updates = {
            'hat': 'goal-setting',  # Immutable - should be ignored
            'responsibilities': json.dumps(['New responsibility']),  # Immutable - should be ignored
            'data_collected': json.dumps({'name': {'field': 'name', 'label': 'Name', 'value': 'John'}}),  # Mutable - should be updated
            'json_validation_retry_count': '1'  # Mutable - should be updated
        }
        
        merged = _merge_session_attributes(existing, updates)
        
        # Immutable fields should remain unchanged
        assert merged['hat'] == 'onboarding'
        assert merged['responsibilities'] == json.dumps(['Collect name', 'Collect age'])
        
        # Mutable fields should be updated
        assert merged['data_collected'] == json.dumps({'name': {'field': 'name', 'label': 'Name', 'value': 'John'}})
        assert merged['json_validation_retry_count'] == '1'
    
    def test_merge_allows_mutable_field_updates(self):
        """Test that mutable fields can be updated"""
        existing = {
            'user_id': 'user-123',
            'data_collected': json.dumps({}),
            'json_validation_retry_count': '0'
        }
        
        updates = {
            'data_collected': json.dumps({'age': {'field': 'age', 'label': 'Age', 'value': '30'}}),
            'json_validation_retry_count': '2'
        }
        
        merged = _merge_session_attributes(existing, updates)
        
        assert merged['data_collected'] == json.dumps({'age': {'field': 'age', 'label': 'Age', 'value': '30'}})
        assert merged['json_validation_retry_count'] == '2'
    
    def test_merge_with_empty_existing(self):
        """Test merging when existing attributes are empty"""
        updates = {
            'data_collected': json.dumps({'name': {'field': 'name', 'label': 'Name', 'value': 'John'}}),
            'json_validation_retry_count': '1'
        }
        
        merged = _merge_session_attributes(None, updates)
        
        assert merged == updates
    
    def test_merge_with_empty_updates(self):
        """Test merging when updates are empty"""
        existing = {
            'user_id': 'user-123',
            'data_collected': json.dumps({}),
            'json_validation_retry_count': '0'
        }
        
        merged = _merge_session_attributes(existing, {})
        
        assert merged == existing


class TestSessionAttributesPersistence:
    """Test session attributes persistence across invocations"""
    
    @patch('index.bedrock_agent_runtime')
    @patch('index.verify_supabase_token')
    def test_new_session_initializes_all_attributes(self, mock_verify, mock_bedrock):
        """Test that new sessions initialize all required session attributes"""
        # Mock token verification
        mock_verify.return_value = {
            'sub': 'user-123',
            
            'subscription_status': 'free', 'is_active': True, 'credits': {}
        }
        
        # Mock Bedrock response with valid JSON
        valid_json_response = json.dumps({
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {
                    'type': 'message',
                    'text': 'What is your name?'
                },
                'tone': 'supportive'
            },
            'input': {
                'type': 'text'
            },
            'previous_field_collected': None
        })
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': valid_json_response.encode('utf-8')
                    }
                }
            ]
        }
        mock_bedrock.invoke_agent.return_value = mock_response
        
        # Create request with session config (new session)
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer valid-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': 'Hello',
                'sessionId': 'session-123',
                'agentId': 'agent-123',
                'agentAliasId': 'alias-123',
                'sessionConfig': {
                    'hat': 'onboarding',
                    'responsibilities': ['Collect name', 'Collect age'],
                    'data_to_be_collected': ['name', 'age'],
                    'data_to_be_calculated': ['bmr'],
                    'notes': ['Important note'],
                    'initial_message': 'Hello'
                }
            })
        }
        
        response = lambda_handler(event, {})
        
        # Verify invoke_agent was called with correct session attributes
        call_args = mock_bedrock.invoke_agent.call_args
        session_state = call_args[1]['sessionState']
        session_attrs = session_state['sessionAttributes']
        
        # Verify all required fields are present
        assert session_attrs['user_id'] == 'user-123'
        assert session_attrs['auth_header_name'] == 'X-Amigo-Auth'
        assert session_attrs['hat'] == 'onboarding'
        assert session_attrs['responsibilities'] == json.dumps(['Collect name', 'Collect age'])
        assert session_attrs['data_to_be_collected'] == json.dumps(['name', 'age'])
        assert session_attrs['data_to_be_calculated'] == json.dumps(['bmr'])
        assert session_attrs['notes'] == json.dumps(['Important note'])
        assert session_attrs['data_collected'] == json.dumps({})
        assert session_attrs['json_validation_retry_count'] == '0'
    
    @patch('index.bedrock_agent_runtime')
    @patch('index.verify_supabase_token')
    def test_existing_session_uses_minimal_attributes(self, mock_verify, mock_bedrock):
        """Test that existing sessions use minimal attributes (Bedrock handles persistence)"""
        # Mock token verification
        mock_verify.return_value = {
            'sub': 'user-123',
            
            'subscription_status': 'free', 'is_active': True, 'credits': {}
        }
        
        # Mock Bedrock response with valid JSON
        valid_json_response = json.dumps({
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {
                    'type': 'message',
                    'text': 'What is your name?'
                },
                'tone': 'supportive'
            },
            'input': {
                'type': 'text'
            },
            'previous_field_collected': {
                'field': 'name',
                'label': 'Name',
                'value': None
            }
        })
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': valid_json_response.encode('utf-8')
                    }
                }
            ]
        }
        mock_bedrock.invoke_agent.return_value = mock_response
        
        # Create request WITHOUT session config (existing session)
        event = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer valid-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': 'My name is John',
                'sessionId': 'session-123',
                'agentId': 'agent-123',
                'agentAliasId': 'alias-123'
            })
        }
        
        response = lambda_handler(event, {})
        
        # Verify invoke_agent was called with minimal session attributes
        call_args = mock_bedrock.invoke_agent.call_args
        session_state = call_args[1]['sessionState']
        session_attrs = session_state['sessionAttributes']
        
        # Should only have minimal attributes (Bedrock will merge with persisted)
        assert session_attrs['user_id'] == 'user-123'
        assert session_attrs['auth_header_name'] == 'X-Amigo-Auth'
        
        # Should NOT have config fields (Bedrock retrieves them automatically)
        assert 'hat' not in session_attrs
        assert 'responsibilities' not in session_attrs
        assert 'data_to_be_collected' not in session_attrs
    
    @patch('index.bedrock_agent_runtime')
    @patch('index.verify_supabase_token')
    def test_session_attributes_not_reinitialized_on_subsequent_turns(self, mock_verify, mock_bedrock):
        """Test that session attributes are not reinitialized on subsequent turns"""
        # Mock token verification
        mock_verify.return_value = {
            'sub': 'user-123',
            
            'subscription_status': 'free', 'is_active': True, 'credits': {}
        }
        
        # Mock Bedrock response with valid JSON
        valid_json_response = json.dumps({
            'status_of_aim': 'in_progress',
            'ui': {
                'render': {
                    'type': 'message',
                    'text': 'What is your name?'
                },
                'tone': 'supportive'
            },
            'input': {
                'type': 'text'
            },
            'previous_field_collected': None
        })
        mock_response = {
            'completion': [
                {
                    'chunk': {
                        'bytes': valid_json_response.encode('utf-8')
                    }
                }
            ]
        }
        mock_bedrock.invoke_agent.return_value = mock_response
        
        # First request: Initialize session
        event1 = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer valid-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': 'Hello',
                'sessionId': 'session-123',
                'agentId': 'agent-123',
                'agentAliasId': 'alias-123',
                'sessionConfig': {
                    'hat': 'onboarding',
                    'responsibilities': ['Collect name'],
                    'data_to_be_collected': ['name'],
                    'data_to_be_calculated': [],
                    'notes': [],
                    'initial_message': 'Hello'
                }
            })
        }
        
        response1 = lambda_handler(event1, {})
        
        # Get session attributes from first call
        call_args1 = mock_bedrock.invoke_agent.call_args
        session_attrs1 = call_args1[1]['sessionState']['sessionAttributes']
        
        # Verify initialization
        assert session_attrs1['data_collected'] == json.dumps({})
        assert session_attrs1['json_validation_retry_count'] == '0'
        
        # Second request: Same session, no config
        event2 = {
            'httpMethod': 'POST',
            'headers': {
                'Authorization': 'Bearer valid-token'
            },
            'body': json.dumps({
                'mode': 'agent',
                'message': 'My name is John',
                'sessionId': 'session-123',
                'agentId': 'agent-123',
                'agentAliasId': 'alias-123'
            })
        }
        
        response2 = lambda_handler(event2, {})
        
        # Get session attributes from second call
        call_args2 = mock_bedrock.invoke_agent.call_args
        session_attrs2 = call_args2[1]['sessionState']['sessionAttributes']
        
        # Should NOT reinitialize data_collected or json_validation_retry_count
        # Should only have minimal attributes (Bedrock handles persistence)
        assert 'data_collected' not in session_attrs2
        assert 'json_validation_retry_count' not in session_attrs2


class TestPropertySessionAttributesPersistence:
    """Property-based tests for session attributes persistence across turns"""
    
    @pytest.mark.parametrize('num_turns', [2, 3, 5])
    def test_property_immutable_fields_persist_across_turns(self, num_turns):
        """
        Property 3: Session Attributes Persistence
        
        For any session with multiple turns, verify:
        1. Immutable fields remain unchanged across all invocations
        2. Mutable fields (data_collected, json_validation_retry_count) can be updated
        
        Validates: Requirements 2.6, 10.1, 10.2, 10.3
        """
        from hypothesis import given, strategies as st, settings
        
        # Strategy for generating session configs
        @st.composite
        def session_config_strategy(draw):
            """Generate random session configs with all required fields."""
            hat = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
                whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
            )))
            
            responsibilities = draw(st.lists(
                st.text(min_size=1, max_size=100),
                min_size=1,
                max_size=10
            ))
            
            data_to_be_collected = draw(st.lists(
                st.text(min_size=1, max_size=50, alphabet=st.characters(
                    whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
                )),
                min_size=1,
                max_size=20
            ))
            
            data_to_be_calculated = draw(st.lists(
                st.text(min_size=1, max_size=50, alphabet=st.characters(
                    whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
                )),
                min_size=0,
                max_size=10
            ))
            
            notes = draw(st.lists(
                st.text(min_size=1, max_size=200),
                min_size=0,
                max_size=5
            ))
            
            initial_message = draw(st.text(min_size=1, max_size=500))
            
            return {
                'hat': hat,
                'responsibilities': responsibilities,
                'data_to_be_collected': data_to_be_collected,
                'data_to_be_calculated': data_to_be_calculated,
                'notes': notes,
                'initial_message': initial_message
            }
        
        # Strategy for generating field data
        @st.composite
        def field_data_strategy(draw):
            """Generate random field data for data_collected."""
            field_name = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
                whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
            )))
            label = draw(st.text(min_size=1, max_size=100))
            value = draw(st.one_of(
                st.text(min_size=1, max_size=200),
                st.integers(min_value=0, max_value=1000).map(str),
                st.none()
            ))
            
            return {
                'field': field_name,
                'label': label,
                'value': value
            }
        
        @given(
            session_config=session_config_strategy(),
            user_id=st.text(min_size=1, max_size=100, alphabet=st.characters(
                whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
            )),
            field_updates=st.lists(field_data_strategy(), min_size=num_turns-1, max_size=num_turns-1)
        )
        @settings(max_examples=100, deadline=None)
        def run_property_test(session_config, user_id, field_updates):
            """Run the property test with generated data."""
            # Mock the Bedrock agent runtime client
            mock_bedrock_client = MagicMock()
            
            # Track session attributes across turns
            persisted_session_attributes = None
            
            def mock_invoke_agent(**kwargs):
                """Mock invoke_agent that simulates Bedrock's session persistence."""
                nonlocal persisted_session_attributes
                
                # Get session attributes from the call
                session_state = kwargs.get('sessionState', {})
                incoming_attributes = session_state.get('sessionAttributes', {})
                
                # Simulate Bedrock's behavior: merge incoming with persisted
                if persisted_session_attributes is None:
                    # First call: store all attributes
                    persisted_session_attributes = incoming_attributes.copy()
                else:
                    # Subsequent calls: merge with persisted (only update mutable fields)
                    persisted_session_attributes = _merge_session_attributes(
                        persisted_session_attributes,
                        incoming_attributes
                    )
                
                # Return mock response
                return {
                    'completion': [
                        {
                            'chunk': {
                                'bytes': b'{"status_of_aim": "in_progress", "ui": {"render": {"type": "message", "text": "Test"}, "tone": "supportive"}, "input": {"type": "text"}, "previous_field_collected": null}'
                            }
                        }
                    ]
                }
            
            mock_bedrock_client.invoke_agent.side_effect = mock_invoke_agent
            
            # Mock verify_supabase_token
            with patch('index.verify_supabase_token') as mock_verify:
                mock_verify.return_value = {
                    'sub': user_id,
                    
                    'subscription_status': 'free', 'is_active': True, 'credits': {}
                }
                
                # Mock bedrock_agent_runtime client
                with patch('index.bedrock_agent_runtime', mock_bedrock_client):
                    # Turn 1: Initialize session with config
                    event1 = {
                        'httpMethod': 'POST',
                        'headers': {
                            'Authorization': f'Bearer test-token-{user_id}'
                        },
                        'body': json.dumps({
                            'mode': 'agent',
                            'message': session_config['initial_message'],
                            'sessionId': f'test-session-{user_id}',
                            'agentId': 'test-agent-id',
                            'agentAliasId': 'test-alias-id',
                            'sessionConfig': session_config
                        })
                    }
                    
                    response1 = lambda_handler(event1, {})
                    assert response1['statusCode'] == 200
                    
                    # Capture initial session attributes
                    initial_attributes = persisted_session_attributes.copy()
                    
                    # Verify initial state
                    assert initial_attributes['user_id'] == user_id
                    assert initial_attributes['hat'] == session_config['hat']
                    assert json.loads(initial_attributes['data_collected']) == {}
                    assert initial_attributes['json_validation_retry_count'] == '0'
                    
                    # Subsequent turns: Send messages without config
                    for turn_idx, field_update in enumerate(field_updates, start=2):
                        # Simulate updating data_collected (would normally happen in Lambda)
                        # For this test, we manually update to simulate the accumulation
                        current_data_collected = json.loads(persisted_session_attributes['data_collected'])
                        if field_update['value'] is not None:
                            current_data_collected[field_update['field']] = field_update
                        persisted_session_attributes['data_collected'] = json.dumps(current_data_collected)
                        
                        # Optionally increment retry count
                        if turn_idx % 2 == 0:
                            persisted_session_attributes['json_validation_retry_count'] = str(turn_idx // 2)
                        
                        # Send subsequent message
                        event_n = {
                            'httpMethod': 'POST',
                            'headers': {
                                'Authorization': f'Bearer test-token-{user_id}'
                            },
                            'body': json.dumps({
                                'mode': 'agent',
                                'message': f'Turn {turn_idx} message',
                                'sessionId': f'test-session-{user_id}',
                                'agentId': 'test-agent-id',
                                'agentAliasId': 'test-alias-id'
                            })
                        }
                        
                        response_n = lambda_handler(event_n, {})
                        assert response_n['statusCode'] == 200
                    
                    # Final verification: Check that immutable fields are unchanged
                    final_attributes = persisted_session_attributes
                    
                    # Immutable fields must remain unchanged
                    assert final_attributes['user_id'] == initial_attributes['user_id'], \
                        "user_id should not change across turns"
                    assert final_attributes['auth_header_name'] == initial_attributes['auth_header_name'], \
                        "auth_header_name should not change across turns"
                    assert final_attributes['hat'] == initial_attributes['hat'], \
                        "hat should not change across turns"
                    assert final_attributes['responsibilities'] == initial_attributes['responsibilities'], \
                        "responsibilities should not change across turns"
                    assert final_attributes['data_to_be_collected'] == initial_attributes['data_to_be_collected'], \
                        "data_to_be_collected should not change across turns"
                    assert final_attributes['data_to_be_calculated'] == initial_attributes['data_to_be_calculated'], \
                        "data_to_be_calculated should not change across turns"
                    assert final_attributes['notes'] == initial_attributes['notes'], \
                        "notes should not change across turns"
                    
                    # Mutable fields should be allowed to change
                    final_data_collected = json.loads(final_attributes['data_collected'])
                    initial_data_collected = json.loads(initial_attributes['data_collected'])
                    
                    # data_collected should have accumulated fields
                    assert len(final_data_collected) >= len(initial_data_collected), \
                        "data_collected should accumulate fields across turns"
                    
                    # json_validation_retry_count can change
                    # (we don't assert specific value, just that it's allowed to change)
                    assert 'json_validation_retry_count' in final_attributes
        
        # Run the property test
        run_property_test()


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

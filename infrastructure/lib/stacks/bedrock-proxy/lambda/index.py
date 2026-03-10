import json
import boto3
import os
from datetime import datetime
from urllib import request as urllib_request
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin

# Initialize Bedrock client (AWS_REGION is automatically available in Lambda)
bedrock_runtime = boto3.client('bedrock-runtime')
bedrock_agent_runtime = boto3.client('bedrock-agent-runtime')

# Supabase JWT verification
SUPABASE_URL = os.environ.get('SUPABASE_URL')
SUPABASE_EDGE_BASE = os.environ.get('SUPABASE_EDGE_BASE') or f"{SUPABASE_URL}/functions/v1"


def _read_agent_events(response):
    text = ""
    return_control = None
    for event in response.get('completion', []):
        if 'chunk' in event and 'bytes' in event['chunk']:
            text += event['chunk']['bytes'].decode('utf-8')
        if 'returnControl' in event:
            return_control = event['returnControl']
    return text, return_control


def _invoke_edge(http_method, api_path, jwt_bearer, payload):
    target = urljoin(f"{SUPABASE_EDGE_BASE}/", api_path.lstrip('/'))
    data = None
    headers = {
        'X-Amigo-Auth': jwt_bearer,
        'Content-Type': 'application/json'
    }
    if http_method.upper() != 'GET':
        data = json.dumps(payload or {}).encode('utf-8')

    req = urllib_request.Request(target, method=http_method.upper(), data=data)
    for k, v in headers.items():
        req.add_header(k, v)

    try:
        with urllib_request.urlopen(req, timeout=20) as response:
            raw = response.read().decode('utf-8')
            try:
                body = json.loads(raw)
            except Exception:
                body = {'raw': raw}
            return response.status, body
    except HTTPError as e:
        raw = e.read().decode('utf-8') if hasattr(e, 'read') else str(e)
        try:
            body = json.loads(raw)
        except Exception:
            body = {'raw': raw}
        return e.code or 500, body
    except Exception as e:
        return 500, {'error': str(e)}


def _build_return_control_results(return_control, jwt_bearer):
    invocation_id = return_control.get('invocationId')
    inputs = return_control.get('invocationInputs', [])
    results = []

    for item in inputs:
        api_input = item.get('apiInvocationInput')
        function_input = item.get('functionInvocationInput')

        if api_input:
            action_group = api_input.get('actionGroup', 'onboarding-operations')
            api_path = api_input.get('apiPath', '')
            http_method = api_input.get('httpMethod', 'GET')

            payload = {}
            request_body = api_input.get('requestBody', {})
            content = request_body.get('content', {})
            app_json = content.get('application/json', {})
            properties = app_json.get('properties', [])
            for prop in properties:
                name = prop.get('name')
                value = prop.get('value')
                if name:
                    payload[name] = value

            status_code, body = _invoke_edge(http_method, api_path, jwt_bearer, payload)
            api_result = {
                'actionGroup': action_group,
                'apiPath': api_path,
                'httpMethod': http_method,
                'httpStatusCode': status_code,
                'responseBody': {
                    'TEXT': {
                        'body': json.dumps(body)
                    }
                }
            }
            if status_code >= 400:
                api_result['responseState'] = 'FAILURE'

            results.append({'apiResult': api_result})
            continue

        if function_input:
            action_group = function_input.get('actionGroup', 'onboarding-operations')
            function_name = function_input.get('function', '')
            parameters = function_input.get('parameters', [])

            args = {}
            for param in parameters:
                name = param.get('name')
                value = param.get('value')
                if name:
                    args[name] = value

            arg_auth = args.get('x_amigo_auth')
            if isinstance(arg_auth, str):
                token_text = arg_auth.strip()
                has_template = (
                    '{x_amigo_auth}' in token_text or '<user_jwt>' in token_text or '{' in token_text or '<' in token_text
                )
                if has_template:
                    arg_auth = jwt_bearer

            header_token = arg_auth if isinstance(arg_auth, str) and arg_auth.strip() else jwt_bearer
            if not header_token.startswith('Bearer '):
                header_token = f'Bearer {header_token}'

            raw = header_token[len('Bearer '):].strip() if header_token.startswith('Bearer ') else header_token
            if raw.count('.') != 2:
                header_token = jwt_bearer

            function_map = {
                'get_profile': ('GET', '/get-profile'),
                'save_onboarding_data': ('POST', '/save-onboarding-data'),
                'get_onboarding_status': ('GET', '/get-onboarding-status'),
                'calculate_bmr': ('POST', '/health-calculations'),
                'calculate_tdee': ('POST', '/health-calculations'),
                'validate_goal': ('POST', '/health-calculations'),
                'calculate_macros': ('POST', '/health-calculations'),
            }
            http_method, api_path = function_map.get(function_name, ('POST', f'/{function_name}'))
            payload = {k: v for k, v in args.items() if k != 'x_amigo_auth'}
            if function_name in ('calculate_bmr', 'calculate_tdee', 'validate_goal', 'calculate_macros'):
                payload['operation'] = function_name

            status_code, body = _invoke_edge(http_method, api_path, header_token, payload)
            function_result = {
                'actionGroup': action_group,
                'function': function_name,
                'responseBody': {
                    'TEXT': {
                        'body': json.dumps(body)
                    }
                }
            }
            if status_code >= 400:
                function_result['responseState'] = 'FAILURE'

            results.append({'functionResult': function_result})

    return invocation_id, results

def verify_supabase_token(token):
    """Verify Supabase JWT token by calling Supabase API"""
    try:
        # Use Supabase's user endpoint to verify the token
        # This endpoint validates the JWT and returns user info
        url = f"{SUPABASE_URL}/auth/v1/user"
        
        req = urllib_request.Request(url)
        req.add_header('Authorization', f'Bearer {token}')
        req.add_header('apikey', os.environ.get('SUPABASE_ANON_KEY', ''))
        
        with urllib_request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                user_data = json.loads(response.read().decode('utf-8'))
                return {
                    'sub': user_data.get('id'),
                    'email': user_data.get('email'),
                    'role': user_data.get('role', 'authenticated')
                }
        return None
    except (HTTPError, URLError) as e:
        print(f"Token verification failed: {e}")
        return None
    except Exception as e:
        print(f"Unexpected error during token verification: {e}")
        return None

def lambda_handler(event, context):
    """
    Lambda function to proxy requests to AWS Bedrock
    Handles authentication and rate limiting
    """
    
    # Debug logging
    print(f"Event: {json.dumps(event)}")
    
    # CORS headers
    headers = {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Amigo-Auth',
        'Access-Control-Allow-Methods': 'POST,OPTIONS'
    }
    
    # Handle OPTIONS request for CORS
    if event.get('httpMethod') == 'OPTIONS':
        return {
            'statusCode': 200,
            'headers': headers,
            'body': ''
        }
    
    try:
        # Parse request body first to check mode/cap for onboarding
        body = json.loads(event.get('body', '{}'))
        mode = body.get('mode')
        cap = body.get('cap')
        
        # Extract and verify authorization token
        incoming_headers = event.get('headers', {}) or {}
        auth_header = incoming_headers.get('Authorization') or incoming_headers.get('authorization', '')
        x_amigo_auth = incoming_headers.get('X-Amigo-Auth') or incoming_headers.get('x-amigo-auth', '')
        
        # Allow unauthenticated onboarding requests (pre-signup)
        is_onboarding = mode == 'agent' and cap == 'onboarding'
        token = None
        user_id = None
        
        # For onboarding, allow anonymous requests but use real JWT when present/valid
        if is_onboarding:
            # Extract token from Authorization header if present
            token_from_header = None
            if auth_header:
                # Handle "Bearer {token}" or just "Bearer"
                if auth_header.startswith('Bearer '):
                    token_from_header = auth_header[7:].strip()  # Everything after "Bearer "
                elif auth_header == 'Bearer':
                    token_from_header = ""  # Empty token
            
            print(f"Onboarding auth check: token_from_header={repr(token_from_header)}, auth_header={repr(auth_header)}")
            
            if token_from_header and token_from_header.strip():
                user_payload = verify_supabase_token(token_from_header)
                if user_payload:
                    user_id = user_payload.get('sub')
                    token = token_from_header
                    print(f"Handling onboarding request with authenticated user: {user_id}")
                else:
                    print("Handling onboarding request with invalid JWT -> falling back to anonymous")
                    user_id = f"onboarding-anon-{int(datetime.utcnow().timestamp())}"
                    token = ""
            else:
                print(f"Handling onboarding request without JWT -> anonymous")
                user_id = f"onboarding-anon-{int(datetime.utcnow().timestamp())}"
                token = ""
        else:
            # Require valid authentication for non-onboarding requests
            if not auth_header or not auth_header.startswith('Bearer '):
                return {
                    'statusCode': 401,
                    'headers': headers,
                    'body': json.dumps({'error': 'Missing or invalid authorization header'})
                }
            
            # Extract token from Authorization header
            token_from_header = auth_header[7:].strip() if auth_header.startswith('Bearer ') else ""
            
            if not token_from_header:
                return {
                    'statusCode': 401,
                    'headers': headers,
                    'body': json.dumps({'error': 'Missing or invalid authorization header'})
                }
            
            token = token_from_header
            user_payload = verify_supabase_token(token)
            
            if not user_payload:
                return {
                    'statusCode': 401,
                    'headers': headers,
                    'body': json.dumps({'error': 'Invalid or expired token'})
                }
            
            user_id = user_payload.get('sub')
        
        if x_amigo_auth:
            print('X-Amigo-Auth header received for downstream tool forwarding path')
        if mode == 'agent':
            agent_id = body.get('agentId') or os.environ.get('BEDROCK_AGENT_ID')
            agent_alias_id = body.get('agentAliasId') or os.environ.get('BEDROCK_AGENT_ALIAS_ID', 'TSTALIASID')
            session_id = body.get('sessionId') or f"mobile-{user_id}-{int(datetime.utcnow().timestamp())}"
            message = body.get('message') or body.get('prompt')

            if not agent_id or not message:
                return {
                    'statusCode': 400,
                    'headers': headers,
                    'body': json.dumps({'error': 'Missing required parameters for agent mode: agentId/message'})
                }

            # Only include bearer token in session attributes if we have a valid token
            bearer_token = f'Bearer {token}' if token else ''
            session_attributes = {
                'user_id': user_id,
                'auth_header_name': 'X-Amigo-Auth'
            }
            
            # Only add x_amigo_auth if we have a valid token
            if bearer_token:
                session_attributes['x_amigo_auth'] = bearer_token
            
            response = bedrock_agent_runtime.invoke_agent(
                agentId=agent_id,
                agentAliasId=agent_alias_id,
                sessionId=session_id,
                inputText=message,
                sessionState={
                    'sessionAttributes': session_attributes
                }
            )

            text, return_control = _read_agent_events(response)

            while return_control:
                invocation_id, results = _build_return_control_results(return_control, bearer_token)
                if not invocation_id or not results:
                    break

                # Build session attributes for follow-up
                follow_up_attributes = {
                    'user_id': user_id,
                    'auth_header_name': 'X-Amigo-Auth'
                }
                if bearer_token:
                    follow_up_attributes['x_amigo_auth'] = bearer_token

                follow_up = bedrock_agent_runtime.invoke_agent(
                    agentId=agent_id,
                    agentAliasId=agent_alias_id,
                    sessionId=session_id,
                    sessionState={
                        'invocationId': invocation_id,
                        'returnControlInvocationResults': results,
                        'sessionAttributes': follow_up_attributes
                    }
                )
                text, return_control = _read_agent_events(follow_up)

            result = {
                'completion': text,
                'stopReason': 'end_turn',
                'usage': {
                    'inputTokens': 0,
                    'outputTokens': 0
                },
                'userId': user_id,
                'timestamp': datetime.utcnow().isoformat()
            }

            return {
                'statusCode': 200,
                'headers': headers,
                'body': json.dumps(result)
            }
        
        # Extract parameters
        prompt = body.get('prompt')
        model_id = body.get('modelId', 'anthropic.claude-3-haiku-20240307-v1:0')
        max_tokens = body.get('maxTokens', 2048)
        temperature = body.get('temperature', 0.7)
        system_prompt = body.get('systemPrompt')
        
        if not prompt:
            return {
                'statusCode': 400,
                'headers': headers,
                'body': json.dumps({'error': 'Missing required parameter: prompt'})
            }
        
        # Build Claude request
        claude_request = {
            'anthropic_version': 'bedrock-2023-05-31',
            'max_tokens': max_tokens,
            'temperature': temperature,
            'messages': [
                {
                    'role': 'user',
                    'content': [
                        {
                            'type': 'text',
                            'text': prompt
                        }
                    ]
                }
            ]
        }
        
        if system_prompt:
            claude_request['system'] = system_prompt
        
        # Call Bedrock
        response = bedrock_runtime.invoke_model(
            modelId=model_id,
            body=json.dumps(claude_request)
        )
        
        # Parse response
        response_body = json.loads(response['body'].read())
        
        # Extract completion text
        completion = ''
        if response_body.get('content'):
            for content_block in response_body['content']:
                if content_block.get('type') == 'text':
                    completion += content_block.get('text', '')
        
        # Build response
        result = {
            'completion': completion,
            'stopReason': response_body.get('stop_reason', 'end_turn'),
            'usage': {
                'inputTokens': response_body.get('usage', {}).get('input_tokens', 0),
                'outputTokens': response_body.get('usage', {}).get('output_tokens', 0)
            },
            'userId': user_id,
            'timestamp': datetime.utcnow().isoformat()
        }
        
        return {
            'statusCode': 200,
            'headers': headers,
            'body': json.dumps(result)
        }
        
    except json.JSONDecodeError:
        return {
            'statusCode': 400,
            'headers': headers,
            'body': json.dumps({'error': 'Invalid JSON in request body'})
        }
    except Exception as e:
        print(f"Error: {str(e)}")
        return {
            'statusCode': 500,
            'headers': headers,
            'body': json.dumps({'error': f'Internal server error: {str(e)}'})
        }

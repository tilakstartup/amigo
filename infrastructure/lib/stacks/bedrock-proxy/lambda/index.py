import json
import boto3
import os
from datetime import datetime
from urllib import request as urllib_request
from urllib.error import HTTPError, URLError

# Initialize Bedrock client (AWS_REGION is automatically available in Lambda)
bedrock_runtime = boto3.client('bedrock-runtime')
bedrock_agent_runtime = boto3.client('bedrock-agent-runtime')

# Supabase JWT verification
SUPABASE_URL = os.environ.get('SUPABASE_URL')


def _read_agent_events(response):
    text = ""
    return_control = None
    for event in response.get('completion', []):
        if 'chunk' in event and 'bytes' in event['chunk']:
            text += event['chunk']['bytes'].decode('utf-8')
        if 'returnControl' in event:
            return_control = event['returnControl']
    return text, return_control


# Legacy edge function invocation removed - all function execution now happens client-side
def _invoke_edge_DEPRECATED(http_method, api_path, jwt_bearer, payload):
    """
    DEPRECATED: This function was used for server-side edge function calls.
    Now all functions are executed client-side via RETURN_CONTROL.
    Kept for reference only.
    """
    pass


# Legacy server-side execution removed - all function execution now happens client-side
# This function is no longer used but kept for reference
def _build_return_control_results_DEPRECATED(return_control, jwt_bearer):
    """
    DEPRECATED: This function was used for server-side function execution.
    Now all functions are executed client-side via RETURN_CONTROL.
    Kept for reference only.
    """
    pass

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
            
            if token_from_header and token_from_header.strip():
                user_payload = verify_supabase_token(token_from_header)
                if user_payload:
                    user_id = user_payload.get('sub')
                    token = token_from_header
                else:
                    user_id = f"onboarding-anon-{int(datetime.utcnow().timestamp())}"
                    token = ""
            else:
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
        
        if mode == 'agent':
            agent_id = body.get('agentId') or os.environ.get('BEDROCK_AGENT_ID')
            agent_alias_id = body.get('agentAliasId') or os.environ.get('BEDROCK_AGENT_ALIAS_ID', 'TSTALIASID')
            session_id = body.get('sessionId') or f"mobile-{user_id}-{int(datetime.utcnow().timestamp())}"
            message = body.get('message') or body.get('prompt')
            return_control_invocation_results = body.get('returnControlInvocationResults')
            
            # Extract invocation ID if present
            invocation_id_from_user = None
            if return_control_invocation_results:
                for item in return_control_invocation_results:
                    inv_id = item.get('invocationId') or item.get('invocation_id')
                    if inv_id:
                        invocation_id_from_user = inv_id
                        break
            
            # Log what user sent to Lambda
            print(f"📥 USER → LAMBDA [session={session_id}, invocation={invocation_id_from_user}]: message={message}, returnControlInvocationResults={json.dumps(return_control_invocation_results) if return_control_invocation_results else None}")

            # Allow empty message if returnControlInvocationResults is provided (tool result handshake)
            has_return_control_results = return_control_invocation_results is not None and len(return_control_invocation_results) > 0
            if not agent_id or (not message and not has_return_control_results):
                return {
                    'statusCode': 400,
                    'headers': headers,
                    'body': json.dumps({'error': 'Missing required parameters for agent mode: agentId and (message or returnControlInvocationResults)'})
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
            
            # Build invoke_agent parameters
            invoke_params = {
                'agentId': agent_id,
                'agentAliasId': agent_alias_id,
                'sessionId': session_id,
                'sessionState': {
                    'sessionAttributes': session_attributes
                }
            }
            
            # Add returnControlInvocationResults if provided
            if return_control_invocation_results:
                # Log function results being returned
                print(f"✅ [session={session_id}, invocation={invocation_id_from_user}] Returning results for {len(return_control_invocation_results)} invocation(s)")
                for item in return_control_invocation_results:
                    func_results = item.get('functionResults') or item.get('function_results') or []
                    for func_result in func_results:
                        action_group = func_result.get('actionGroup') or func_result.get('action_group')
                        function_name = func_result.get('functionName') or func_result.get('function_name')
                        result_data = func_result.get('result', '{}')
                        print(f"  ← [invocation={invocation_id_from_user}] {action_group}.{function_name} = {result_data}")
                
                # Transform client format to Bedrock format
                # Client sends: [{"invocation_id": "...", "function_results": [...]}]
                # Bedrock expects: [{"functionResult": {...}} or {"apiResult": {...}}]
                # IMPORTANT: If the action group uses API schema, we must return apiResult, not functionResult
                bedrock_results = []
                invocation_id_to_send = None
                for item in return_control_invocation_results:
                    invocation_id = item.get('invocationId') or item.get('invocation_id')
                    if invocation_id and not invocation_id_to_send:
                        invocation_id_to_send = invocation_id
                    # Handle both camelCase and snake_case field names
                    function_results = item.get('functionResults') or item.get('function_results') or []
                    
                    for func_result in function_results:
                        action_group = func_result.get('actionGroup') or func_result.get('action_group') or 'data_operations'
                        function_name = func_result.get('functionName') or func_result.get('function_name') or ''
                        success = func_result.get('success', False)
                        result_data = func_result.get('result', '{}')
                        error_msg = func_result.get('error', '')
                        
                        # List of function schema action groups (currently empty - all use API schema)
                        # If we add function schema action groups in the future, add them here
                        function_schema_functions = []
                        
                        # Default: All action groups use API schema (OpenAPI 3.0)
                        # Only check against function_schema_functions list for exceptions
                        is_function_schema = function_name in function_schema_functions
                        
                        if not is_function_schema:
                            # Return apiResult format for API schema action groups (default)
                            # Convert function_name to API path: calculate_bmr -> /calculate-bmr
                            api_path = '/' + function_name.replace('_', '-')
                            
                            # Determine HTTP method based on function name
                            # GET methods: functions starting with 'get_'
                            # POST methods: everything else (save, calculate, validate, update, delete)
                            http_method = 'GET' if function_name.startswith('get_') else 'POST'
                            
                            # Always return 200 status code, use responseState for success/failure
                            # This allows Bedrock Agent to process errors gracefully
                            bedrock_result = {
                                'apiResult': {
                                    'actionGroup': action_group,
                                    'apiPath': api_path,
                                    'httpMethod': http_method,
                                    'httpStatusCode': 200,
                                    'responseBody': {
                                        'application/json': {
                                            'body': result_data if isinstance(result_data, str) else json.dumps(result_data)
                                        }
                                    }
                                }
                            }
                            
                            if not success:
                                bedrock_result['apiResult']['responseState'] = 'FAILURE'
                                if error_msg:
                                    bedrock_result['apiResult']['responseBody']['application/json']['body'] = json.dumps({
                                        'error': error_msg
                                    })
                        else:
                            # Return functionResult format for function schema action groups (exceptions only)
                            bedrock_result = {
                                'functionResult': {
                                    'actionGroup': action_group,
                                    'function': function_name,
                                    'responseBody': {
                                        'TEXT': {
                                            'body': result_data if isinstance(result_data, str) else json.dumps(result_data)
                                        }
                                    }
                                }
                            }
                            
                            if not success:
                                bedrock_result['functionResult']['responseState'] = 'FAILURE'
                                if error_msg:
                                    bedrock_result['functionResult']['responseBody']['TEXT']['body'] = json.dumps({
                                        'error': error_msg
                                    })
                        
                        bedrock_results.append(bedrock_result)
                
                # Only add returnControlInvocationResults if we have actual results
                if bedrock_results and invocation_id_to_send:
                    # AWS Bedrock expects returnControlInvocationResults in sessionState
                    # Format: [{"functionResult": {...}}, {"functionResult": {...}}]
                    invoke_params['sessionState']['returnControlInvocationResults'] = bedrock_results
                    
                    # CRITICAL: invocationId goes at sessionState level (same level as returnControlInvocationResults)
                    invoke_params['sessionState']['invocationId'] = invocation_id_to_send
                else:
                    print(f"⚠️ WARNING: returnControlInvocationResults provided but no results or invocationId to send")
                
                # CRITICAL: Do NOT include inputText when sending invocation results
                # This tells Bedrock to continue the RETURN_CONTROL flow, not start a new request
            else:
                # Normal request with message
                if not message:
                    return {
                        'statusCode': 400,
                        'headers': headers,
                        'body': json.dumps({'error': 'Missing required parameter: message'})
                    }
                invoke_params['inputText'] = message
            
            # Log conversation flow
            if message:
                print(f"💬 [session={session_id}] User message: {message}")
            
            # Log what Lambda is sending to Agent
            print(f"📤 LAMBDA → AGENT [session={session_id}, invocation={invocation_id_from_user}]: inputText={message if message else '[RETURN_CONTROL_RESULTS]'}")
            
            response = bedrock_agent_runtime.invoke_agent(**invoke_params)

            text, return_control = _read_agent_events(response)
            
            # Extract invocation ID from agent response
            invocation_id_from_agent = return_control.get('invocationId') if return_control else None
            
            # Log what Lambda received from Agent
            print(f"📥 AGENT → LAMBDA [session={session_id}, invocation={invocation_id_from_agent}]: completion={text}, return_control={json.dumps(return_control) if return_control else None}")
            
            # Log agent response
            if text:
                print(f"🤖 [session={session_id}] Agent response: {text}")
            
            # Return invocations to client for client-side handling
            invocations = None
            invocation_id = None
            
            if return_control:
                invocation_id = return_control.get('invocationId')
                invocation_inputs = return_control.get('invocationInputs', [])
                
                # Log function invocations
                print(f"🔧 [session={session_id}, invocation={invocation_id}] Agent requesting {len(invocation_inputs)} function invocation(s)")
                
                # Convert invocations to client format
                invocations = []
                for item in invocation_inputs:
                    api_input = item.get('apiInvocationInput')
                    function_input = item.get('functionInvocationInput')
                    
                    if api_input:
                        # Convert API invocation to function format for consistency
                        action_group = api_input.get('actionGroup', 'data_operations')
                        api_path = api_input.get('apiPath', '')
                        
                        # Map API paths to function names
                        function_name = api_path.lstrip('/').replace('-', '_')
                        
                        # Extract parameters from requestBody
                        params = {}
                        request_body = api_input.get('requestBody', {})
                        content = request_body.get('content', {})
                        app_json = content.get('application/json', {})
                        properties = app_json.get('properties', [])
                        for prop in properties:
                            name = prop.get('name')
                            value = prop.get('value')
                            if name:
                                params[name] = value
                        
                        invocations.append({
                            'action_group': action_group,
                            'function_name': function_name,
                            'params': params
                        })
                    
                    elif function_input:
                        action_group = function_input.get('actionGroup', 'data_operations')
                        function_name = function_input.get('function', '')
                        parameters = function_input.get('parameters', [])
                        
                        params = {}
                        for param in parameters:
                            name = param.get('name')
                            value = param.get('value')
                            if name:
                                params[name] = value
                        
                        invocations.append({
                            'action_group': action_group,
                            'function_name': function_name,
                            'params': params
                        })
                        print(f"  → [invocation={invocation_id}] {action_group}.{function_name}({json.dumps(params)})")
                
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
            
            # Add invocations if present
            if invocations:
                result['invocations'] = invocations
                result['invocationId'] = invocation_id
            
            # Log what Lambda is sending to User
            print(f"📤 LAMBDA → USER [session={session_id}, invocation={invocation_id}]: {json.dumps(result)}")

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

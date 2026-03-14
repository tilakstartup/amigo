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


def _merge_session_attributes(existing_attributes, updates):
    """
    Merge session attribute updates while preserving immutable fields.
    
    Immutable fields (never changed after initialization):
    - user_id, auth_header_name, hat, responsibilities, 
      data_to_be_collected, data_to_be_calculated, notes, x_amigo_auth
    
    Mutable fields (can be updated):
    - data_collected, json_validation_retry_count
    
    Args:
        existing_attributes: Current session attributes from Bedrock
        updates: Dictionary of fields to update (only mutable fields)
    
    Returns:
        Merged session attributes dictionary
    """
    if not existing_attributes:
        return updates
    
    # Start with existing attributes
    merged = existing_attributes.copy()
    
    # Only allow updates to mutable fields
    mutable_fields = {'data_collected', 'json_validation_retry_count'}
    
    for key, value in updates.items():
        if key in mutable_fields:
            merged[key] = value
        # Silently ignore updates to immutable fields
    
    return merged


def _build_success_response(completion, data_collected, invocation_id, user_id, invocations=None):
    """
    Build a unified success response structure.

    Args:
        completion: Agent JSON object (dict, not string)
        data_collected: Accumulated fields from session attributes (dict)
        invocation_id: Invocation ID from agent response (string)
        user_id: User identifier (string)
        invocations: Optional list of function invocations (list or None)

    Returns:
        dict: Unified response with all required fields

    Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
    """
    response = {
        'completion': completion,
        'data_collected': data_collected,
        'invocations': invocations if invocations else None,
        'invocationId': invocation_id,
        'error': None,
        'userId': user_id,
        'timestamp': datetime.utcnow().isoformat() + 'Z'
    }
    return response


def _build_error_response(error_message, user_id, invocation_id=None):
    """
    Build a unified error response structure.

    Args:
        error_message: Descriptive error message (string)
        user_id: User identifier for logging (string)
        invocation_id: Optional invocation ID if available (string or None)

    Returns:
        dict: Error response with required fields, no completion/data_collected/invocations

    Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7
    """
    response = {
        'completion': None,
        'data_collected': None,
        'invocations': None,
        'invocationId': invocation_id,
        'error': error_message,
        'userId': user_id,
        'timestamp': datetime.utcnow().isoformat() + 'Z'
    }
    return response


def _sanitize_json_string(text):
    """
    Sanitize a string for JSON parsing by removing/replacing control characters
    that are invalid inside JSON strings (e.g. literal newlines, tabs, etc.).
    
    The Bedrock agent sometimes returns pretty-printed JSON where string values
    contain literal newline characters (\\n as actual newlines, not escaped).
    json.loads() rejects these as "Invalid control character".
    
    Strategy: replace literal control chars inside string values with their
    escaped equivalents, without touching the structural whitespace.
    We do this by scanning char-by-char and only replacing control chars
    that appear inside JSON string literals.
    """
    result = []
    in_string = False
    escape_next = False
    for ch in text:
        if escape_next:
            result.append(ch)
            escape_next = False
            continue
        if ch == '\\' and in_string:
            result.append(ch)
            escape_next = True
            continue
        if ch == '"':
            in_string = not in_string
            result.append(ch)
            continue
        if in_string and ord(ch) < 0x20:
            # Replace control character with its JSON escape sequence
            escape_map = {'\n': '\\n', '\r': '\\r', '\t': '\\t', '\b': '\\b', '\f': '\\f'}
            result.append(escape_map.get(ch, f'\\u{ord(ch):04x}'))
        else:
            result.append(ch)
    return ''.join(result)


def _validate_json_response(response_text):
    """
    Validate agent response is valid JSON with all required fields and correct types.
    
    Validates:
    - Response is parseable JSON object
    - All required fields present: status_of_aim, ui, input, previous_field_collected
    - Enum values for status_of_aim, ui.render.type, input.type
    - String constraints (non-empty for text, field, label)
    - previous_field_collected.value is string or null (not empty string)
    
    Args:
        response_text: Agent response text to validate
    
    Returns:
        tuple: (is_valid: bool, error_message: str or None, parsed_json: dict or None)
    
    Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 5.13
    """
    # Requirement 5.1: Response must be valid JSON (parseable as JSON object)
    try:
        # Sanitize control characters before parsing (agent may return literal newlines in strings)
        sanitized = _sanitize_json_string(response_text)
        parsed = json.loads(sanitized)
        if not isinstance(parsed, dict):
            return False, "Response is not a JSON object", None
    except (json.JSONDecodeError, ValueError) as e:
        return False, f"Invalid JSON: {str(e)}", None
    
    # Requirement 5.2: All required fields must be present
    required_fields = ['status_of_aim', 'ui', 'input', 'previous_field_collected']
    for field in required_fields:
        if field not in parsed:
            return False, f"Missing required field: {field}", None
    
    # Requirement 5.3: status_of_aim must be valid enum
    valid_status_values = ['not_set', 'in_progress', 'completed']
    if parsed['status_of_aim'] not in valid_status_values:
        return False, f"Invalid status_of_aim value: {parsed['status_of_aim']}", None
    
    # Requirement 5.4: ui must contain render field (tone is optional)
    ui = parsed.get('ui')
    if not isinstance(ui, dict):
        return False, "ui must be an object", None
    if 'render' not in ui:
        return False, "ui must contain render field", None
    
    # Requirement 5.5: ui.render must contain type and text fields
    render = ui.get('render')
    if not isinstance(render, dict):
        return False, "ui.render must be an object", None
    if 'type' not in render or 'text' not in render:
        return False, "ui.render must contain type and text fields", None
    
    # Requirement 5.6: ui.render.type must be valid enum
    valid_render_types = ['info', 'message', 'message_with_summary']
    if render['type'] not in valid_render_types:
        return False, f"Invalid ui.render.type value: {render['type']}", None
    
    # Requirement 5.7: ui.render.text must be non-empty string
    if not isinstance(render['text'], str) or len(render['text']) == 0:
        return False, "ui.render.text must be a non-empty string", None
    
    # Requirement 5.8: input must contain type field
    input_obj = parsed.get('input')
    # input is optional when render.type == "info" (agent is providing info before calling a function)
    if render['type'] == 'info' and input_obj is None:
        return True, None, parsed
    if not isinstance(input_obj, dict):
        return False, "input must be an object", None
    if 'type' not in input_obj:
        return False, "input must contain type field", None
    
    # Requirement 5.9: input.type must be valid enum
    valid_input_types = ['text', 'weight', 'date', 'quick_pills', 'yes_no', 'dropdown']
    if input_obj['type'] not in valid_input_types:
        return False, f"Invalid input.type value: {input_obj['type']}", None
    
    # Requirement 5.10: previous_field_collected must be an object or null
    # null is allowed when: status_of_aim == "completed" OR no field was collected this turn
    prev_field = parsed.get('previous_field_collected')
    if prev_field is None:
        # null is always valid — means no field was collected this turn
        return True, None, parsed
    if not isinstance(prev_field, dict):
        return False, "previous_field_collected must be an object or null", None
    if 'field' not in prev_field or 'label' not in prev_field or 'value' not in prev_field:
        return False, "previous_field_collected must contain field, label, and value fields", None
    
    # Requirement 5.11: previous_field_collected.field must be non-empty string
    if not isinstance(prev_field['field'], str) or len(prev_field['field']) == 0:
        return False, "previous_field_collected.field must be a non-empty string", None
    
    # Requirement 5.12: previous_field_collected.label must be non-empty string
    if not isinstance(prev_field['label'], str) or len(prev_field['label']) == 0:
        return False, "previous_field_collected.label must be a non-empty string", None
    
    # Requirement 5.13: previous_field_collected.value must be string or null (not empty string)
    value = prev_field['value']
    if value is not None:
        if not isinstance(value, str):
            return False, "previous_field_collected.value must be a string or null", None
        if len(value) == 0:
            return False, "previous_field_collected.value cannot be an empty string", None
    
    return True, None, parsed


def _accumulate_data_collected(current_field, session_attributes):
    """
    Accumulate current_field into data_collected in session attributes.
    
    Rules:
    - Store entire current_field object keyed by field name
    - Overwrite existing fields (no append/merge)
    - Preserve existing entry if new value is null
    - Treat empty string as null (not stored)
    
    Args:
        current_field: Dict with 'field', 'label', 'value' keys
        session_attributes: Current session attributes dict
    
    Returns:
        Updated session attributes dict with accumulated data_collected
    
    Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
    """
    if not current_field or not isinstance(current_field, dict):
        return session_attributes
    
    # Extract field information
    field_name = current_field.get('field')
    field_label = current_field.get('label')
    field_value = current_field.get('value')
    
    # Validate field name and label are present
    if not field_name or not field_label:
        return session_attributes
    
    # Get current data_collected from session attributes
    data_collected_str = session_attributes.get('data_collected', '{}')
    try:
        data_collected = json.loads(data_collected_str) if isinstance(data_collected_str, str) else data_collected_str
    except (json.JSONDecodeError, ValueError):
        data_collected = {}
    
    # Requirement 4.6: Treat empty string and string "null" as null
    if field_value == '' or field_value == 'null':
        field_value = None
    
    # Requirement 4.5, 4.7: Preserve existing entry if new value is null
    if field_value is not None:
        # Requirement 4.4: Store entire current_field object keyed by field name
        # Requirement 4.5: Overwrite existing fields (no append/merge)
        data_collected[field_name] = {
            'field': field_name,
            'label': field_label,
            'value': field_value
        }
    elif field_name not in data_collected:
        # New field with null value - store it
        data_collected[field_name] = {
            'field': field_name,
            'label': field_label,
            'value': None
        }
    # else: field exists and new value is null - preserve existing entry
    
    # Update session attributes with accumulated data
    session_attributes['data_collected'] = json.dumps(data_collected)
    
    return session_attributes


def _read_agent_events(response):
    text = ""
    return_control = None
    session_attributes = None
    for event in response.get('completion', []):
        if 'chunk' in event and 'bytes' in event['chunk']:
            text += event['chunk']['bytes'].decode('utf-8')
        if 'returnControl' in event:
            return_control = event['returnControl']
        # Extract session attributes from top-level sessionState event
        if 'sessionState' in event:
            node = event['sessionState']
            if isinstance(node, dict):
                attrs = node.get('sessionAttributes')
                if attrs:
                    session_attributes = attrs
                    print(f"✅ Got sessionAttributes from sessionState event: {list(attrs.keys())}")
        # Also check inside trace events (some Bedrock versions put it there)
        if 'trace' in event:
            node = event['trace']
            if isinstance(node, dict):
                attrs = node.get('sessionAttributes')
                if attrs:
                    session_attributes = attrs
                inner = node.get('sessionState')
                if isinstance(inner, dict):
                    attrs = inner.get('sessionAttributes')
                    if attrs:
                        session_attributes = attrs
    return text, return_control, session_attributes


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
        # Parse request body first to check mode/hat for onboarding
        body = json.loads(event.get('body', '{}'))
        mode = body.get('mode')
        hat = body.get('hat')
        
        # Extract and verify authorization token
        incoming_headers = event.get('headers', {}) or {}
        auth_header = incoming_headers.get('Authorization') or incoming_headers.get('authorization', '')
        x_amigo_auth = incoming_headers.get('X-Amigo-Auth') or incoming_headers.get('x-amigo-auth', '')
        
        # Allow unauthenticated onboarding requests (pre-signup)
        is_onboarding = mode == 'agent' and hat == 'onboarding'
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
            session_config = body.get('sessionConfig')
            
            # Extract initial_message from sessionConfig if present
            initial_message = None
            if session_config:
                initial_message = session_config.get('initial_message')
                # If initial_message is provided, use it as the message
                if initial_message:
                    message = initial_message
            
            # Extract invocation ID if present
            invocation_id_from_user = None
            if return_control_invocation_results:
                for item in return_control_invocation_results:
                    inv_id = item.get('invocationId') or item.get('invocation_id')
                    if inv_id:
                        invocation_id_from_user = inv_id
                        break
            
            # Log what user sent to Lambda
            print(f"📥 USER → LAMBDA [session={session_id}, invocation={invocation_id_from_user}]: message={message}, sessionConfig={json.dumps(session_config, separators=(',', ':')) if session_config else None}, returnControlInvocationResults={json.dumps(return_control_invocation_results, separators=(',', ':')) if return_control_invocation_results else None}")

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
            
            # Initialize or retrieve session attributes
            session_attributes = None
            
            # Add sessionConfig fields to session attributes (except initial_message)
            if session_config:
                # New session: Initialize all session attributes
                session_attributes = {
                    'user_id': user_id,
                    'auth_header_name': 'X-Amigo-Auth'
                }
                
                # Store config fields in session attributes (lists must be JSON-encoded)
                if 'hat' in session_config:
                    session_attributes['hat'] = session_config['hat']
                if 'responsibilities' in session_config:
                    session_attributes['responsibilities'] = json.dumps(session_config['responsibilities'])
                if 'data_to_be_collected' in session_config:
                    session_attributes['data_to_be_collected'] = json.dumps(session_config['data_to_be_collected'])
                if 'data_to_be_calculated' in session_config:
                    session_attributes['data_to_be_calculated'] = json.dumps(session_config['data_to_be_calculated'])
                if 'notes' in session_config:
                    session_attributes['notes'] = json.dumps(session_config['notes'])
                
                # Initialize data_collected as empty object for new sessions
                session_attributes['data_collected'] = json.dumps({})
                
                # Initialize json_validation_retry_count to 0 for new sessions
                session_attributes['json_validation_retry_count'] = '0'
                
                # Signal authentication status (no raw token in session attributes)
                session_attributes['is_authenticated'] = 'true' if bearer_token else 'false'
            else:
                # Existing session: send only auth fields; Bedrock persists all other session attributes
                # (hat, responsibilities, data_collected, etc.) internally across turns
                session_attributes = {
                    'user_id': user_id,
                    'auth_header_name': 'X-Amigo-Auth'
                }
                session_attributes['is_authenticated'] = 'true' if bearer_token else 'false'
                # Restore data_collected from client (client accumulates across turns in-memory)
                client_data_collected = body.get('data_collected')
                if client_data_collected is not None:
                    if isinstance(client_data_collected, dict):
                        raw = client_data_collected
                    elif isinstance(client_data_collected, str):
                        try:
                            raw = json.loads(client_data_collected)
                        except (json.JSONDecodeError, ValueError):
                            raw = {}
                    else:
                        raw = {}
                    # Sanitize: treat string "null" values as actual null
                    for k, v in raw.items():
                        if isinstance(v, dict) and v.get('value') == 'null':
                            v['value'] = None
                    session_attributes['data_collected'] = json.dumps(raw)
                    print(f"📊 [session={session_id}] Restored data_collected from client: {len(raw)} field(s)")
            
            # Build invoke_agent parameters
            invoke_params = {
                'agentId': agent_id,
                'agentAliasId': agent_alias_id,
                'sessionId': session_id,
                'enableTrace': True,
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
            print(f"📤 LAMBDA → AGENT [session={session_id}, invocation={invocation_id_from_user}]: inputText={message if message else '[RETURN_CONTROL_RESULTS]'}, sessionAttributes={json.dumps(session_attributes, separators=(',', ':'))}")
            
            response = bedrock_agent_runtime.invoke_agent(**invoke_params)

            text, return_control, retrieved_session_attributes = _read_agent_events(response)
            
            # Extract invocation ID from agent response
            invocation_id_from_agent = return_control.get('invocationId') if return_control else None
            
            # Log what Lambda received from Agent
            print(f"📥 AGENT → LAMBDA [session={session_id}, invocation={invocation_id_from_agent}]: completion={text.replace(chr(10), ' ') if text else None}, return_control={json.dumps(return_control, separators=(',', ':')) if return_control else None}")
            
            # Log agent response
            if text:
                print(f"🤖 [session={session_id}] Agent response: {text.replace(chr(10), ' ')}")
            
            # JSON Validation and Retry Logic (Requirements 5.x, 6.x)
            # Only validate if we have text response and no return_control (function invocations)
            # Function invocations don't need JSON validation
            if text and not return_control:
                is_valid, error_message, parsed_json = _validate_json_response(text)
                
                if not is_valid:
                    # Validation failed - check retry count
                    # Requirement 6.1: Check json_validation_retry_count from session attributes
                    retry_count = 0
                    if retrieved_session_attributes:
                        retry_count = int(retrieved_session_attributes.get('json_validation_retry_count', '0'))
                    
                    print(f"⚠️ [session={session_id}] JSON validation failed (attempt {retry_count + 1}/3): {error_message}")
                    
                    # Requirement 6.2, 6.3: Increment retry count and retry if count < 3
                    if retry_count < 3:
                        # Increment retry count in session attributes
                        # Use retrieved attributes if available, otherwise fall back to sent attributes
                        updated_session_attributes = (retrieved_session_attributes or session_attributes or {}).copy()
                        updated_session_attributes['json_validation_retry_count'] = str(retry_count + 1)
                        
                        # Requirement 6.4: Send retry message to agent in the same session
                        retry_message = "Please respond with JSON object ONLY"
                        print(f"🔄 [session={session_id}] Retrying with message: {retry_message}")
                        
                        # Re-invoke agent with retry message
                        retry_params = {
                            'agentId': agent_id,
                            'agentAliasId': agent_alias_id,
                            'sessionId': session_id,
                            'inputText': retry_message,
                            'sessionState': {
                                'sessionAttributes': updated_session_attributes
                            }
                        }
                        
                        response = bedrock_agent_runtime.invoke_agent(**retry_params)
                        retry_text, return_control, retry_retrieved_attrs = _read_agent_events(response)
                        
                        # Merge: prefer newly retrieved attrs, fall back to what we sent
                        if retry_retrieved_attrs:
                            retrieved_session_attributes = retry_retrieved_attrs
                        else:
                            retrieved_session_attributes = updated_session_attributes
                        
                        text = retry_text
                        invocation_id_from_agent = return_control.get('invocationId') if return_control else None
                        
                        print(f"📥 AGENT → LAMBDA [session={session_id}, invocation={invocation_id_from_agent}] (retry): completion={text.replace(chr(10), ' ') if text else None}")
                        
                        # Validate retry response
                        if text and not return_control:
                            is_valid, error_message, parsed_json = _validate_json_response(text)
                            if not is_valid:
                                print(f"⚠️ [session={session_id}] Retry validation failed: {error_message}")
                                # Don't block on retry failure — pass through best-effort
                                # Try to parse as JSON anyway for data accumulation
                                try:
                                    parsed_json = json.loads(text)
                                except (json.JSONDecodeError, ValueError):
                                    parsed_json = None
                    else:
                        # Requirement 6.5: Max retries reached - return error response
                        print(f"❌ [session={session_id}] Max retries (3) exceeded, returning error")
                        error_result = _build_error_response(
                            error_message='Agent failed to respond with valid JSON after 3 retries',
                            user_id=user_id,
                            invocation_id=invocation_id_from_agent
                        )
                        
                        return {
                            'statusCode': 200,
                            'headers': headers,
                            'body': json.dumps(error_result)
                        }
                
                # Data accumulation after validation
                # Accumulate previous_field_collected into data_collected in session attributes
                # Use retrieved_session_attributes if available (Bedrock persisted them),
                # otherwise fall back to sent session_attributes
                effective_session_attrs = retrieved_session_attributes or session_attributes or {}
                
                if parsed_json:
                    prev_field = parsed_json.get('previous_field_collected')
                    if prev_field and isinstance(prev_field, dict) and prev_field.get('field'):
                        effective_session_attrs = _accumulate_data_collected(
                            prev_field,
                            effective_session_attrs
                        )
                        retrieved_session_attributes = effective_session_attrs
                        print(f"📊 [session={session_id}] Accumulated field: {prev_field.get('field')} = {prev_field.get('value')}")
                    else:
                        # Old format fallback: accumulate from data.collected flat map
                        old_collected = parsed_json.get('data', {}).get('collected', {})
                        if old_collected and isinstance(old_collected, dict):
                            data_collected_str = effective_session_attrs.get('data_collected', '{}')
                            try:
                                existing = json.loads(data_collected_str) if isinstance(data_collected_str, str) else data_collected_str
                            except (json.JSONDecodeError, ValueError):
                                existing = {}
                            for k, v in old_collected.items():
                                if v is not None and v != '':
                                    existing[k] = {'field': k, 'label': k, 'value': str(v)}
                            effective_session_attrs['data_collected'] = json.dumps(existing)
                            retrieved_session_attributes = effective_session_attrs
                            print(f"📊 [session={session_id}] Accumulated {len(old_collected)} field(s) (old format fallback)")
            
            # If we retrieved session attributes from Bedrock, use them for the next invocation
            # This ensures session state persists across turns
            if retrieved_session_attributes:
                # Preserve immutable fields from retrieved session attributes
                # Only update mutable fields (data_collected, json_validation_retry_count)
                # Note: For this invocation, we already sent the session attributes
                # The retrieved attributes will be used in the NEXT invocation
                # Store them for potential use (e.g., in data accumulation logic)
                pass
            
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
                
            # Requirement 4.8, 4.9, 7.4: Extract data_collected from session attributes
            # Use retrieved attrs if available, otherwise fall back to sent session_attributes
            effective_attrs_for_response = retrieved_session_attributes or session_attributes or {}
            data_collected = {}
            if 'data_collected' in effective_attrs_for_response:
                data_collected_str = effective_attrs_for_response.get('data_collected', '{}')
                try:
                    data_collected = json.loads(data_collected_str) if isinstance(data_collected_str, str) else data_collected_str
                    print(f"📊 [session={session_id}] Including data_collected with {len(data_collected)} field(s)")
                except (json.JSONDecodeError, ValueError):
                    data_collected = {}

            # Parse completion as JSON object if it's a string (Requirement 7.3)
            completion_obj = None
            if text:
                try:
                    completion_obj = json.loads(_sanitize_json_string(text))
                except (json.JSONDecodeError, ValueError):
                    completion_obj = text  # fallback: keep as string if not valid JSON

            # Build unified success response (Requirements 7.1, 7.2, 7.3, 7.4, 7.5)
            result = _build_success_response(
                completion=completion_obj,
                data_collected=data_collected,
                invocation_id=invocation_id,
                user_id=user_id,
                invocations=invocations if invocations else None
            )

            # Log what Lambda is sending to User
            print(f"📤 LAMBDA → USER [session={session_id}, invocation={invocation_id}]: {json.dumps(result, separators=(',', ':'))}")

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

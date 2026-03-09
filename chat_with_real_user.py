#!/usr/bin/env python3
"""
Chat with Bedrock agent using real Supabase user credentials.

Flow:
1) Sign in to Supabase (email/password)
2) Get real user JWT
3) Start interactive chat with onboarding session context
4) Execute return-control tool calls against Supabase edge functions
"""

import json
import os
import sys
import uuid
from urllib.parse import urljoin
from typing import Optional

try:
    import requests
    import boto3
except ImportError:
    os.system("pip3 install requests boto3 -q")
    import requests
    import boto3


SUPABASE_URL = os.getenv("SUPABASE_URL", "https://hibbnohfwvbglyxgyaav.supabase.co")
SUPABASE_ANON_KEY = os.getenv(
    "SUPABASE_ANON_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpYmJub2hmd3ZiZ2x5eGd5YWF2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI4NjQwNDMsImV4cCI6MjA4ODQ0MDA0M30.8acSzRLPqFFOf1WF-k5BECV8Vfdx1bVlaKTxM_s26Rc",
)

EMAIL = os.getenv("TEST_SUPABASE_EMAIL", "tilak@yopmail.com")
PASSWORD = os.getenv("TEST_SUPABASE_PASSWORD", "imathsage")

AGENT_ID = os.getenv("BEDROCK_AGENT_ID", "4XLAIQ6BUY")
AGENT_ALIAS_ID = os.getenv("BEDROCK_AGENT_ALIAS_ID", "TSTALIASID")
AWS_REGION = os.getenv("AWS_REGION", "us-east-1")
EDGE_BASE = os.getenv("SUPABASE_EDGE_BASE", "https://hibbnohfwvbglyxgyaav.supabase.co/functions/v1")

JSON_CONTRACT_HINT = (
    "Respond ONLY in valid JSON with keys: "
    "type, version, session_context{cap,responsibilities,collect_data,collect_metrics}, "
    "aimofchat{name,status}, ui{render{type,text,data[]},tone,next_question}, input{type,options}, "
    "data{collected,metrics}, actions, missing_fields, error."
)


def extract_json(text: str):
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            try:
                return json.loads(text[start : end + 1])
            except json.JSONDecodeError:
                return None
        return None


def redact_secrets(payload):
    if isinstance(payload, dict):
        redacted = {}
        for key, value in payload.items():
            if key == "jwt_token" and isinstance(value, str):
                redacted[key] = f"{value[:20]}...{value[-20:]}"
            else:
                redacted[key] = redact_secrets(value)
        return redacted
    if isinstance(payload, list):
        return [redact_secrets(item) for item in payload]
    return payload


def login_and_get_jwt(email: str, password: str) -> tuple[str, str]:
    auth_url = f"{SUPABASE_URL}/auth/v1/token?grant_type=password"
    response = requests.post(
        auth_url,
        json={"email": email, "password": password},
        headers={"apikey": SUPABASE_ANON_KEY, "Content-Type": "application/json"},
        timeout=15,
    )
    response.raise_for_status()
    auth = response.json()
    return auth["access_token"], auth["user"]["id"]


def _invoke_edge(http_method: str, api_path: str, jwt_bearer: str, payload: Optional[dict]):
    target = urljoin(f"{EDGE_BASE}/", api_path.lstrip("/"))
    headers = {
        "X-Amigo-Auth": jwt_bearer,
        "Content-Type": "application/json",
    }
    if http_method.upper() == "GET":
        r = requests.get(target, headers=headers, timeout=20)
    else:
        r = requests.post(target, headers=headers, json=payload or {}, timeout=20)
    try:
        body = r.json()
    except Exception:
        body = {"raw": r.text}
    return r.status_code, body


def _read_agent_events(response):
    text = ""
    return_control = None
    for event in response.get("completion", []):
        if "chunk" in event and "bytes" in event["chunk"]:
            text += event["chunk"]["bytes"].decode("utf-8")
        if "returnControl" in event:
            return_control = event["returnControl"]
    return text, return_control


def _build_return_control_results(return_control: dict, jwt_bearer: str):
    invocation_id = return_control.get("invocationId")
    inputs = return_control.get("invocationInputs", [])
    results = []

    for item in inputs:
        if os.getenv("DEBUG_RETURN_CONTROL") == "1":
            print("[DEBUG] return control item:", json.dumps(item, indent=2))

        api_input = item.get("apiInvocationInput")
        function_input = item.get("functionInvocationInput")

        if api_input:
            action_group = api_input.get("actionGroup", "onboarding-operations")
            api_path = api_input.get("apiPath", "")
            http_method = api_input.get("httpMethod", "GET")

            payload = {}
            request_body = api_input.get("requestBody", {})
            content = request_body.get("content", {})
            app_json = content.get("application/json", {})
            properties = app_json.get("properties", [])
            for p in properties:
                name = p.get("name")
                value = p.get("value")
                if name:
                    payload[name] = value

            status_code, body = _invoke_edge(http_method, api_path, jwt_bearer, payload)
            api_result = {
                "actionGroup": action_group,
                "apiPath": api_path,
                "httpMethod": http_method,
                "httpStatusCode": status_code,
                "responseBody": {
                    "TEXT": {
                        "body": json.dumps(body)
                    }
                }
            }
            if status_code >= 400:
                api_result["responseState"] = "FAILURE"

            results.append({"apiResult": api_result})
            continue

        if function_input:
            action_group = function_input.get("actionGroup", "onboarding-operations")
            function_name = function_input.get("function", "")
            parameters = function_input.get("parameters", [])

            args = {}
            for p in parameters:
                name = p.get("name")
                value = p.get("value")
                if name:
                    args[name] = value

            arg_auth = args.get("x_amigo_auth")
            if isinstance(arg_auth, str):
                token_text = arg_auth.strip()
                has_template = (
                    "{x_amigo_auth}" in token_text
                    or "<user_jwt>" in token_text
                    or "{" in token_text
                    or "<" in token_text
                )
                if has_template:
                    arg_auth = jwt_bearer

            header_token = arg_auth if isinstance(arg_auth, str) and arg_auth.strip() else jwt_bearer
            if not header_token.startswith("Bearer "):
                header_token = f"Bearer {header_token}"

            raw = header_token[len("Bearer "):].strip() if header_token.startswith("Bearer ") else header_token
            if raw.count(".") != 2:
                header_token = jwt_bearer

            function_map = {
                "get_profile": ("GET", "/get-profile"),
                "save_onboarding_data": ("POST", "/save-onboarding-data"),
                "get_onboarding_status": ("GET", "/get-onboarding-status"),
            }
            http_method, api_path = function_map.get(function_name, ("POST", f"/{function_name}"))

            payload = {k: v for k, v in args.items() if k != "x_amigo_auth"}
            if os.getenv("DEBUG_RETURN_CONTROL") == "1":
                print(f"[DEBUG] function={function_name} method={http_method} path={api_path}")
                print(f"[DEBUG] header token prefix={header_token[:24]}...")
                print(f"[DEBUG] payload={json.dumps(payload)}")
            status_code, body = _invoke_edge(http_method, api_path, header_token, payload)
            function_result = {
                "actionGroup": action_group,
                "function": function_name,
                "responseBody": {
                    "TEXT": {
                        "body": json.dumps(body)
                    }
                }
            }
            if status_code >= 400:
                function_result["responseState"] = "FAILURE"

            results.append({"functionResult": function_result})

    return invocation_id, results


def _invoke_with_return_control(client, session_id: str, message: str, jwt_token: str, user_id: str):
    response = client.invoke_agent(
        agentId=AGENT_ID,
        agentAliasId=AGENT_ALIAS_ID,
        sessionId=session_id,
        inputText=message,
        sessionState={
            "sessionAttributes": {
                "x_amigo_auth": f"Bearer {jwt_token}",
                "user_id": user_id,
                "auth_header_name": "X-Amigo-Auth"
            }
        }
    )

    text, return_control = _read_agent_events(response)

    while return_control:
        invocation_id, results = _build_return_control_results(return_control, f"Bearer {jwt_token}")
        if not invocation_id or not results:
            break
        follow_up = client.invoke_agent(
            agentId=AGENT_ID,
            agentAliasId=AGENT_ALIAS_ID,
            sessionId=session_id,
            sessionState={
                "invocationId": invocation_id,
                "returnControlInvocationResults": results,
                "sessionAttributes": {
                    "x_amigo_auth": f"Bearer {jwt_token}",
                    "user_id": user_id,
                    "auth_header_name": "X-Amigo-Auth"
                }
            }
        )
        text, return_control = _read_agent_events(follow_up)

    return text


def build_session_context(user_id: str, cap: str) -> dict:
    return {
        "cap": cap,
        "user_id": user_id,
        "responsibilities": [
            "Use X-Amigo-Auth header from session attribute x_amigo_auth when calling edge functions",
            "Fetch profile via GET /get-profile",
            "Collect missing fields based on cap",
            "Save updates via POST /save-onboarding-data",
            "Validate status via GET /get-onboarding-status",
        ],
        "collect_data": ["first_name", "last_name", "age", "weight", "height", "gender", "activity_level"],
    }


def invoke_once(user_question: str, cap: str = "onboarding"):
    jwt_token, user_id = login_and_get_jwt(EMAIL, PASSWORD)

    session_context = build_session_context(user_id, cap)

    message = (
        f"SESSION_CONTEXT:{json.dumps(session_context)}\n\n"
        f"{JSON_CONTRACT_HINT}\n\n"
        f"User message: {user_question}"
    )

    client = boto3.client("bedrock-agent-runtime", region_name=AWS_REGION)
    response = client.invoke_agent(
        agentId=AGENT_ID,
        agentAliasId=AGENT_ALIAS_ID,
        sessionId=str(uuid.uuid4()),
        inputText=message,
        sessionState={
            "sessionAttributes": {
                "x_amigo_auth": f"Bearer {jwt_token}",
                "user_id": user_id,
                "auth_header_name": "X-Amigo-Auth"
            }
        }
    )

    text, return_control = _read_agent_events(response)
    if return_control:
        invocation_id, results = _build_return_control_results(return_control, f"Bearer {jwt_token}")
        follow_up = client.invoke_agent(
            agentId=AGENT_ID,
            agentAliasId=AGENT_ALIAS_ID,
            sessionId=str(uuid.uuid4()),
            sessionState={
                "invocationId": invocation_id,
                "returnControlInvocationResults": results,
                "sessionAttributes": {
                    "x_amigo_auth": f"Bearer {jwt_token}",
                    "user_id": user_id,
                    "auth_header_name": "X-Amigo-Auth"
                }
            }
        )
        text, _ = _read_agent_events(follow_up)

    parsed = extract_json(text)

    print("\n✅ Auth + Bedrock invocation complete")
    print(f"User ID: {user_id}")
    print(f"Cap: {cap}")
    print(f"Question: {user_question}\n")

    if parsed:
        print("Agent JSON response:")
        print(json.dumps(redact_secrets(parsed), indent=2))
    else:
        print("Agent raw response:")
        print(text)


def start_chat_session(cap: str = "onboarding"):
    jwt_token, user_id = login_and_get_jwt(EMAIL, PASSWORD)
    client = boto3.client("bedrock-agent-runtime", region_name=AWS_REGION)
    session_id = str(uuid.uuid4())
    first_turn = True

    print("🤖 Amigo Chat - Real User Mode")
    print("=" * 60)
    print("✅ Real auth login successful")
    print(f"User ID: {user_id}")
    print(f"Cap: {cap}")
    print(f"Session ID: {session_id}")
    print("Type 'quit' or 'exit' to end the conversation")
    print("=" * 60)
    print()

    while True:
        try:
            user_question = input("You: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 Session closed")
            break

        if not user_question:
            continue
        if user_question.lower() in {"exit", "quit", "bye"}:
            print("👋 Session closed")
            break

        if first_turn:
            session_context = build_session_context(user_id, cap)
            message = (
                f"SESSION_CONTEXT:{json.dumps(session_context)}\n\n"
                f"{JSON_CONTRACT_HINT}\n\n"
                f"User message: {user_question}"
            )
            first_turn = False
        else:
            message = f"{JSON_CONTRACT_HINT}\n\nUser message: {user_question}"

        text = _invoke_with_return_control(client, session_id, message, jwt_token, user_id)

        parsed = extract_json(text)
        if parsed:
            print("\nAmigo JSON:")
            print(json.dumps(redact_secrets(parsed), indent=2))

            session_ctx = parsed.get("session_context", {})
            aim = parsed.get("aimofchat", {})
            ui = parsed.get("ui", {})
            input_meta = parsed.get("input", {})
            data = parsed.get("data", {})

            if session_ctx:
                print(f"Session Cap: {session_ctx.get('cap')}")
            if aim:
                print(f"Aim: {aim.get('name')} [{aim.get('status')}]")

            render = ui.get("render", {})
            if render.get("type"):
                print(f"Render Type: {render.get('type')}")
            if render.get("text"):
                print(f"Message: {render.get('text')}")
            if render.get("data"):
                print("Render Data:")
                for item in render.get("data", []):
                    label = item.get("label", "")
                    var_name = item.get("var_name_in_collected", "")
                    value = item.get("value", "")
                    print(f"  - {label} ({var_name}): {value}")

            if input_meta.get("type"):
                print(f"Input Type: {input_meta.get('type')}")
                options = input_meta.get("options", [])
                if options:
                    print("Input Options:")
                    for opt in options:
                        print(f"  - {opt.get('label')} ({opt.get('value')})")

            if data:
                collected = data.get("collected", {})
                metrics = data.get("metrics", {})
                if collected:
                    print(f"Collected Data: {list(collected.keys())}")
                if metrics:
                    print(f"Metrics: {list(metrics.keys())}")
            print()
        else:
            print(f"\nAmigo raw:\n{text}\n")


def run_scripted_session(questions: list[str], cap: str = "onboarding"):
    jwt_token, user_id = login_and_get_jwt(EMAIL, PASSWORD)
    client = boto3.client("bedrock-agent-runtime", region_name=AWS_REGION)
    session_id = str(uuid.uuid4())
    first_turn = True

    print("\n✅ Scripted chat session started")
    print(f"User ID: {user_id}")
    print(f"Cap: {cap}")
    print(f"Session ID: {session_id}\n")

    for user_question in questions:
        if first_turn:
            session_context = build_session_context(user_id, cap)
            message = (
                f"SESSION_CONTEXT:{json.dumps(session_context)}\n\n"
                f"{JSON_CONTRACT_HINT}\n\n"
                f"User message: {user_question}"
            )
            first_turn = False
        else:
            message = f"{JSON_CONTRACT_HINT}\n\nUser message: {user_question}"

        print(f"You: {user_question}")

        text = _invoke_with_return_control(client, session_id, message, jwt_token, user_id)

        parsed = extract_json(text)
        if parsed:
            render = parsed.get("ui", {}).get("render", {})
            print(f"Amigo: {render.get('text', '(no text)')}\n")
        else:
            print(f"Amigo raw: {text}\n")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--chat":
        cap = args[1] if len(args) > 1 else "onboarding"
        start_chat_session(cap=cap)
    elif args and args[0] == "--scripted":
        cap = args[1] if len(args) > 1 else "onboarding"
        run_scripted_session([
            "I want to start onboarding.",
            "My first name is Tilak.",
            "My last name is Putta."
        ], cap=cap)
    else:
        cap = args[0] if args else "onboarding"
        start_chat_session(cap=cap)

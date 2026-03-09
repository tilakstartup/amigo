#!/usr/bin/env python3
"""
Interactive chat with Amigo Bedrock Agent
"""
import boto3
import uuid
import sys
import json


JSON_CONTRACT_HINT = (
    "Respond ONLY in valid JSON with keys: "
    "type, version, session_context{cap,responsibilities,collect_data,collect_metrics}, aimofchat{name,status}, "
    "ui{render{type,text,data[]},tone,next_question}, "
    "input{type,options}, data{collected,metrics}, actions, missing_fields, error. "
    "input.type must be one of quick_pills|text|date|yes_no|dropdown. "
    "ui.render.type must be one of: info (no response needed), message (asking user), message_with_summary (showing summary). "
    "ui.render.text must be plain text only (no JSON). "
    "ui.render.data is an ARRAY of objects: [{label, var_name_in_collected, value}, ...]. "
    "For yes_no options use values yes and no."
)


def _extract_json(text: str):
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start = text.find('{')
        end = text.rfind('}')
        if start != -1 and end != -1 and end > start:
            try:
                return json.loads(text[start:end + 1])
            except json.JSONDecodeError:
                return None
        return None

def chat_with_agent(agent_id, agent_alias_id='TSTALIASID', session_context=None):
    """Start an interactive chat session with the agent
    
    Args:
        agent_id: Bedrock agent ID
        agent_alias_id: Agent alias ID
        session_context: Optional dict with {role, collect_data[], collect_metrics[], aimofchat}
    """
    
    bedrock = boto3.client('bedrock-agent-runtime', region_name='us-east-1')
    session_id = str(uuid.uuid4())
    first_message = True
    
    print("🤖 Amigo Chat - Interactive Mode")
    print("=" * 60)
    print(f"Session ID: {session_id}")
    print(f"Agent ID: {agent_id}")
    if session_context:
        print(f"Session Context: {json.dumps(session_context, indent=2)}")
    print("\nType 'quit' or 'exit' to end the conversation")
    print("=" * 60)
    print()
    
    while True:
        # Get user input
        try:
            user_input = input("You: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n\n👋 Goodbye!")
            break
            
        if not user_input:
            continue
            
        if user_input.lower() in ['quit', 'exit', 'bye']:
            print("\n👋 Goodbye!")
            break
        
        # Build message with session context on first turn
        if first_message and session_context:
            message = f"SESSION_CONTEXT:{json.dumps(session_context)}\n\n{JSON_CONTRACT_HINT}\n\nUser message: {user_input}"
            first_message = False
        else:
            message = f"{JSON_CONTRACT_HINT}\n\nUser message: {user_input}"
        
        # Send to agent
        try:
            response = bedrock.invoke_agent(
                agentId=agent_id,
                agentAliasId=agent_alias_id,
                sessionId=session_id,
                inputText=message
            )
            
            # Collect response
            agent_response = ""
            event_stream = response['completion']
            
            for event in event_stream:
                if 'chunk' in event:
                    chunk = event['chunk']
                    if 'bytes' in chunk:
                        agent_response += chunk['bytes'].decode('utf-8')
            
            parsed = _extract_json(agent_response)
            if parsed is not None:
                print("\nAmigo JSON:")
                print(json.dumps(parsed, indent=2))
                
                session_ctx = parsed.get("session_context", {})
                aim = parsed.get("aimofchat", {})
                ui = parsed.get("ui", {})
                input_meta = parsed.get("input", {})
                data = parsed.get("data", {})
                
                if session_ctx:
                    print(f"Session Cap: {session_ctx.get('cap')}")
                if aim:
                    print(f"Aim: {aim.get('name')} [{aim.get('status')}]")
                
                # Handle render structure
                render = ui.get("render", {})
                if render.get("type"):
                    print(f"Render Type: {render.get('type')}")
                if render.get("text"):
                    print(f"Message: {render.get('text')}")
                if render.get("data"):
                    render_data = render.get("data", [])
                    if render_data:
                        print(f"Render Data:")
                        for item in render_data:
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
                print("\nAmigo (non-JSON fallback):")
                print(agent_response)
                print()
            
        except Exception as e:
            print(f"\n❌ Error: {e}\n")

if __name__ == '__main__':
    agent_id = '4XLAIQ6BUY'
    
    # Example session contexts you can use:
    # Goal setting session
    goal_setting_context = {
        "cap": "goal_setting",
        "responsibilities": [
            "Collect user data",
            "Calculate metrics",
            "Validate USDA minimums",
            "Summarize goal",
            "Get confirmation",
            "Save goal"
        ],
        "collect_data": ["weight", "height", "age", "gender", "activity_level", "target_weight", "target_date"],
        "collect_metrics": ["bmr", "tdee", "daily_calories", "weekly_rate_kg"]
    }
    
    # Default: no context (let agent decide)
    chat_with_agent(agent_id, session_context=None)
    
    # To use goal setting context, uncomment:
    # chat_with_agent(agent_id, session_context=goal_setting_context)

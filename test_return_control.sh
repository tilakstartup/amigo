#!/bin/bash

# Test script to capture full chat conversation with return control flow
echo "=== TESTING RETURN CONTROL FLOW ==="
echo ""
echo "Step 1: Terminate and restart app..."
xcrun simctl terminate booted com.amigoai.ios 2>/dev/null || true
sleep 2

echo "Step 2: Start log capture..."
log stream --style compact --predicate 'process == "Amigo"' --level debug > /tmp/chat_logs.txt 2>&1 &
LOG_PID=$!
sleep 2

echo "Step 3: Launch app..."
xcrun simctl launch booted com.amigoai.ios > /dev/null 2>&1
echo "App launched. Waiting 30 seconds for conversation to complete..."
sleep 30

echo "Step 4: Stop log capture..."
kill $LOG_PID 2>/dev/null || true
sleep 1

echo ""
echo "=== FULL CONVERSATION TRANSCRIPT ==="
echo ""

echo "Reconstructed Conversation:"
echo "─────────────────────────────────────────────────────────────"
echo ""

# Extract and reconstruct the full conversation from chunked logs
awk '
BEGIN {
    in_prompt = 0
    in_response = 0
    prompt = ""
    response = ""
    prompt_count = 0
    response_count = 0
}

/PROMPT_START/ { 
    in_prompt = 1
    prompt = ""
    next
}

/PROMPT_END/ { 
    if (in_prompt && prompt != "") {
        prompt_count++
        print "================================================================"
        print "USER MESSAGE #" prompt_count ":"
        print "================================================================"
        print prompt
        print ""
    }
    in_prompt = 0
    prompt = ""
    next
}

/PROMPT_PART_[0-9]+\/[0-9]+:/ {
    if (in_prompt) {
        sub(/.*PROMPT_PART_[0-9]+\/[0-9]+: /, "")
        if ($0 != "") {
            prompt = prompt $0 "\n"
        }
    }
    next
}

/RESPONSE_START/ { 
    in_response = 1
    response = ""
    next
}

/RESPONSE_END/ { 
    if (in_response && response != "") {
        response_count++
        print "================================================================"
        print "AGENT RESPONSE #" response_count ":"
        print "================================================================"
        print response
        print ""
    }
    in_response = 0
    response = ""
    next
}

/RESPONSE_PART_[0-9]+\/[0-9]+:/ {
    if (in_response) {
        sub(/.*RESPONSE_PART_[0-9]+\/[0-9]+: /, "")
        if ($0 != "") {
            response = response $0 "\n"
        }
    }
    next
}

END {
    print "================================================================"
    print "SUMMARY: " prompt_count " user messages, " response_count " agent responses"
    print "================================================================"
}
' /tmp/chat_logs.txt

echo ""
echo "=== DETAILED ANALYSIS ==="
echo ""

echo "1. Session Information:"
SESSION_ID=$(grep "Generated session ID:" /tmp/chat_logs.txt | head -1 | sed 's/.*Generated session ID: //')
if [ -n "$SESSION_ID" ]; then
    echo "   Session ID: $SESSION_ID"
else
    echo "   No session ID found"
fi

echo ""
echo "2. Authentication Status:"
if grep -q "Auth token retrieved: Present" /tmp/chat_logs.txt; then
    echo "   User is authenticated"
    USER_ID=$(grep "user_id" /tmp/chat_logs.txt | head -1 | grep -o '"user_id": "[^"]*"' | cut -d'"' -f4)
    if [ -n "$USER_ID" ]; then
        echo "   User ID: $USER_ID"
    fi
else
    echo "   User is NOT authenticated"
fi

echo ""
echo "3. Conversation Flow:"
PROMPT_COUNT=$(grep -c "PROMPT_START" /tmp/chat_logs.txt 2>/dev/null || echo "0")
RESPONSE_COUNT=$(grep -c "RESPONSE_START" /tmp/chat_logs.txt 2>/dev/null || echo "0")
echo "   User prompts sent: $PROMPT_COUNT"
echo "   Agent responses received: $RESPONSE_COUNT"

echo ""
echo "4. Action Invocations & Return Control:"
INVOCATION_COUNT=$(grep -c "Processing.*action invocations" /tmp/chat_logs.txt 2>/dev/null || echo "0")
RC_COUNT=$(grep -c "returnControlInvocationResults" /tmp/chat_logs.txt 2>/dev/null || echo "0")
EXECUTING_COUNT=$(grep -c "Executing:" /tmp/chat_logs.txt 2>/dev/null || echo "0")

# Clean up counts
INVOCATION_COUNT=$(echo "$INVOCATION_COUNT" | tr -d '[:space:]')
RC_COUNT=$(echo "$RC_COUNT" | tr -d '[:space:]')
EXECUTING_COUNT=$(echo "$EXECUTING_COUNT" | tr -d '[:space:]')

# Check if any are greater than 0
if [ "${INVOCATION_COUNT:-0}" -gt 0 ] 2>/dev/null || [ "${RC_COUNT:-0}" -gt 0 ] 2>/dev/null || [ "${EXECUTING_COUNT:-0}" -gt 0 ] 2>/dev/null; then
    echo "   Found action invocations:"
    echo "      - Invocation batches: $INVOCATION_COUNT"
    echo "      - Return control sends: $RC_COUNT"
    echo "      - Function executions: $EXECUTING_COUNT"
    echo ""
    echo "   Details:"
    grep -E "Processing.*action invocations|Executing:|succeeded|failed|get_profile|save_profile" /tmp/chat_logs.txt | sed 's/^/     /' | head -20
else
    echo "   No action invocations found (agent may not have needed to call functions)"
fi

echo ""
echo "5. Error Detection:"
ERROR_COUNT=$(grep -E "ERROR|Failed" /tmp/chat_logs.txt | grep -v "NoError\|error.*null\|0 errors\|succeeded" | wc -l | tr -d ' ')
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo "   Found $ERROR_COUNT error lines:"
    grep -E "ERROR|Failed" /tmp/chat_logs.txt | grep -v "NoError\|error.*null\|0 errors\|succeeded" | sed 's/^/     /' | head -10
else
    echo "   No errors found"
fi

echo ""
echo "6. Lambda Proxy Logs:"
echo "   Checking recent Lambda invocations..."
LAMBDA_OUTPUT=$(aws logs tail /aws/lambda/amigo-bedrock-proxy-dev --since 5m --format short --region us-east-1 2>&1 | head -30)
if [ -n "$LAMBDA_OUTPUT" ]; then
    echo "$LAMBDA_OUTPUT" | sed 's/^/     /'
else
    echo "   No recent Lambda logs found"
fi

echo ""
echo "=== CONVERSATION TIMELINE ==="
echo ""
echo "Key events in chronological order:"
grep -E "Initial message:|START SESSION|Sending|PROMPT_START|RESPONSE_START|Executing:|succeeded|failed|Agent call succeeded" /tmp/chat_logs.txt | \
    sed 's/.*Amigo.debug.dylib) //' | \
    nl -w3 -s'. ' | \
    sed 's/^/  /' | \
    head -50

echo ""
echo "─────────────────────────────────────────────────────────────"
echo "Full raw log saved to: /tmp/chat_logs.txt"
echo "View with: cat /tmp/chat_logs.txt"
echo "Search for specific terms: grep -i 'search_term' /tmp/chat_logs.txt"
echo "Extract messages: grep -A 50 'PROMPT_START\\|RESPONSE_START' /tmp/chat_logs.txt"
echo "─────────────────────────────────────────────────────────────"

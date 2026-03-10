# Return Control Analysis - iOS Onboarding

## RETURN_CONTROL Invocation Received

The Bedrock agent sent back the following RETURN_CONTROL to the iOS app:

```json
{
  "invocationId": "22460c75-3336-4d75-8771-50bfa45a62e0",
  "invocationInputs": [
    {
      "apiInvocationInput": {
        "actionGroup": "data_operations",
        "actionInvocationType": "RESULT",
        "agentId": "FDONOFHDHR",
        "apiPath": "/get-profile",
        "httpMethod": "GET",
        "parameters": []
      }
    }
  ]
}
```

## Problem

The agent is trying to invoke `GET /get-profile` from the `data_operations` action group. This action requires authentication (JWT token) to access the Supabase Edge Function.

However, during onboarding:
- Users are anonymous (not yet signed up)
- No JWT token is available
- The Lambda proxy sends an empty bearer token
- The Supabase Edge Function returns 401 Unauthorized

## Error Flow

1. iOS app starts onboarding (no auth token)
2. Agent receives instruction: "get the user profile first using get profile tool"
3. Agent invokes RETURN_CONTROL with `get_profile` action
4. Lambda proxy calls Supabase Edge Function with empty bearer token
5. Edge Function returns: `{"error": "Missing X-Amigo-Auth header (Bearer <user_jwt>)"}`
6. Lambda returns error to agent: `dependencyFailedException: GET:401:FAILURE`
7. Agent fails to continue conversation

## Solution Options

### Option 1: Update Agent Instructions (RECOMMENDED)
Remove or make optional the "get the user profile first" instruction for onboarding. The agent should:
- Skip profile retrieval during onboarding
- Start directly with collecting onboarding information
- Only try to get profile if user is authenticated

### Option 2: Make get_profile Handle Anonymous Users
Update the Supabase Edge Function to:
- Return empty profile for anonymous users
- Allow the agent to proceed without profile data
- This requires changes to the Edge Function

### Option 3: Client-Side Action Handling
Handle `get_profile` action in the mobile app instead of calling the Edge Function:
- Return empty profile for anonymous onboarding
- This is what we implemented for health calculations
- Requires updating ActionGroupRegistry

## Recommendation

**Option 1** is the best approach because:
- Onboarding users don't have profiles yet
- The agent should collect information, not retrieve it
- Simpler and cleaner flow
- No code changes needed in Edge Functions or mobile app

Update the agent instruction from:
```
"get the user profile first using get profile tool and greet if you get their name"
```

To:
```
"If user is authenticated, you may get their profile to personalize greeting. For onboarding, start directly with collecting information."
```

Or simply remove the get_profile instruction for onboarding entirely.

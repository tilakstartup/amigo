# iOS OAuth Flow Testing

## Issue Identified
The OAuth flow is completing but `supabase.auth.currentSessionOrNull()` returns NULL immediately after `signInWith(Google)` returns.

## Root Cause
On iOS, `supabase.auth.signInWith(Google)` opens a browser for OAuth. The flow is:
1. App calls `signInWith(Google)`
2. Browser opens for OAuth
3. User logs in
4. Browser redirects to deep link (amigo://auth#access_token=...&refresh_token=...)
5. App receives deep link in `handleDeepLink()`
6. Deep link handler calls `sessionManager.handleDeepLinkSession()`
7. Session is imported into Supabase

The problem: Step 1 returns immediately (before OAuth completes), so `currentSessionOrNull()` is NULL.

## Solution Implemented
Added a polling loop in `OAuthAuthenticator.signInWithGoogle()` and `signInWithApple()` that:
1. Waits up to 10 seconds for the session to appear
2. Polls every 500ms
3. Returns the session once it's available

## Testing Steps
1. Launch app
2. Click "Sign in with Google" or "Sign in with Apple"
3. Complete OAuth in browser
4. App should receive deep link and import session
5. Polling loop should detect the session
6. Session should be saved to secure storage
7. Token should be available for Bedrock agent

## Expected Logs (with new polling)
```
🔐 [OAuth] Starting Google sign-in...
🔐 [OAuth] Google OAuth flow completed, waiting for deep link callback...
🔐 [OAuth] Waiting for session... attempt 1/20
🔐 [OAuth] Waiting for session... attempt 2/20
...
🔐 [OAuth] Session obtained from Supabase after X attempts
🔐 [OAuth] User ID: <uuid>
🔐 [OAuth] User email: <email>
✅ [iOS AuthViewModel] Session saved, setting isAuthenticated=true
```

## If Still Failing
If the polling loop times out (20 attempts = 10 seconds), it means:
1. Deep link is not being received
2. Deep link is being received but not processed
3. Session import is failing

Check:
- Is the deep link URL scheme configured in Info.plist?
- Is `handleDeepLink()` being called?
- Is `sessionManager.handleDeepLinkSession()` working?

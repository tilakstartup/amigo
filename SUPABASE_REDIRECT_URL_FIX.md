# Supabase Redirect URL Configuration

## Problem
Safari can't open the redirect URL because Supabase is trying to redirect to a URL that isn't configured in the Supabase project settings.

## Solution
You need to add the redirect URLs to Supabase's allowed redirect URLs list.

## Steps

1. Go to Supabase Dashboard:
   https://supabase.com/dashboard/project/hibbnohfwvbglyxgyaav/auth/url-configuration

2. Under "Redirect URLs", add these URLs:
   ```
   amigo://auth/callback
   amigoai://oauth-callback
   com.amigoai.ios://oauth-callback
   ```

3. Click "Save"

4. Also verify the "Site URL" is set correctly (should be one of the above or a web URL)

## Why This Matters

When Supabase completes OAuth:
1. Google redirects to Supabase
2. Supabase validates the redirect URL against the allowed list
3. If not in the list, Supabase rejects it
4. Safari shows "Can't open page" error

## Testing

After adding the redirect URLs:
1. Rebuild the app
2. Try signing in with Google again
3. Should now redirect back to the app successfully

## If Still Not Working

Check:
1. Exact URL scheme matches Info.plist
2. No typos in redirect URLs
3. Supabase project is the correct one
4. Changes are saved (click Save button)
5. Clear browser cache if testing in web

# Bundle ID Change Notice

## Change Made

The iOS app bundle ID has been changed from `com.amigo.ios` to `com.tilakputta.amigo.ios` because `com.amigo.ios` is globally reserved and cannot be registered in Apple Developer Portal.

## Files Updated

1. `mobile/ios/Amigo/Info.plist` - Updated CFBundleIdentifier and CFBundleURLName
2. `mobile/ios/project.yml` - Updated PRODUCT_BUNDLE_IDENTIFIER
3. `mobile/ios/Amigo.xcodeproj/project.pbxproj` - Updated PRODUCT_BUNDLE_IDENTIFIER

## URL Scheme Updated

The deep link URL scheme has also been updated:
- Old: `amigo://oauth-callback`
- New: `com.tilakputta.amigo.ios://oauth-callback`

## What You Need to Update

### 1. Apple Developer Portal

When creating the App ID, use:
- **Bundle ID**: `com.tilakputta.amigo.ios`

### 2. Google Cloud Console (iOS OAuth Client)

Update the iOS OAuth client:
- **Bundle ID**: `com.tilakputta.amigo.ios`

### 3. Supabase

Update redirect URLs to include:
```
com.tilakputta.amigo.ios://oauth-callback
```

Remove the old URL:
```
com.amigo.ios://oauth-callback
```

## Testing

After these changes:
1. Rebuild the iOS app
2. Test Google OAuth - it should still work
3. Test Apple Sign In once configured
4. Test deep linking

## Production Considerations

If you want a cleaner bundle ID for production, consider:
- Registering a domain you own (e.g., `com.yourdomain.amigo`)
- Using your company name (e.g., `com.yourcompany.amigo.ios`)

The bundle ID can be changed again before production, but it requires:
- Updating all OAuth providers
- Updating Supabase configuration
- Rebuilding and re-signing the app

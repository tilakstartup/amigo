# Supabase Deep Link Configuration for Mobile Apps

## Overview

When users click email confirmation links, they should be redirected back to your mobile app, not localhost.

## Step 1: Configure Supabase Redirect URLs

1. Go to Supabase Dashboard: https://supabase.com/dashboard/project/hibbnohfwvbglyxgyaav/auth/url-configuration

2. Add the following **Redirect URLs**:
   ```
   com.amigo.android://auth/callback
   com.amigo.ios://auth/callback
   http://localhost:3000/*
   ```

3. Set **Site URL** to:
   ```
   com.amigo.android://
   ```

4. Click **Save**

## Step 2: Configure Android Deep Links

### Update AndroidManifest.xml

The manifest needs intent filters for deep links:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:theme="@style/Theme.Amigo">
    
    <!-- Main launcher -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    
    <!-- Deep link for auth callback -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="com.amigo.android"
            android:host="auth" />
    </intent-filter>
</activity>
```

## Step 3: Configure iOS Deep Links

### Update Info.plist

Add URL scheme for deep links:

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleURLName</key>
        <string>com.amigo.ios</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>com.amigo.ios</string>
        </array>
    </dict>
</array>
```

## Step 4: Handle Deep Links in Code

### Android - MainActivity.kt

```kotlin
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.data?.let { uri ->
        // Handle deep link
        if (uri.scheme == "com.amigo.android" && uri.host == "auth") {
            // Supabase will handle the callback automatically
            // Just refresh the session
            viewModel.refreshSession()
        }
    }
}
```

### iOS - AmigoApp.swift

```swift
.onOpenURL { url in
    // Handle deep link
    if url.scheme == "com.amigo.ios" && url.host == "auth" {
        // Supabase will handle the callback automatically
        // Just refresh the session
        authViewModel.refreshSession()
    }
}
```

## Step 5: Disable Email Confirmation (Recommended for Development)

For easier development, you can disable email confirmation:

1. Go to: https://supabase.com/dashboard/project/hibbnohfwvbglyxgyaav/auth/providers
2. Click on **Email** provider
3. **Disable** "Confirm email"
4. Click **Save**

This allows users to sign in immediately without clicking the confirmation email.

## Testing Deep Links

### Android

```bash
# Test deep link
adb shell am start -W -a android.intent.action.VIEW -d "com.amigo.android://auth/callback?token=test"
```

### iOS

```bash
# Test deep link
xcrun simctl openurl booted "com.amigo.ios://auth/callback?token=test"
```

## Troubleshooting

**Deep link not working on Android?**
- Check `android:launchMode="singleTask"` is set
- Verify scheme matches exactly: `com.amigo.android`
- Check intent filter has `android:autoVerify="true"`

**Deep link not working on iOS?**
- Verify URL scheme in Info.plist
- Check scheme matches exactly: `com.amigo.ios`
- Rebuild the app after changing Info.plist

**Still redirecting to localhost?**
- Check Supabase redirect URLs are saved
- Clear browser cache
- Try in incognito/private mode

## Production Setup

For production with custom domain:

1. **Set up Universal Links (iOS)** and **App Links (Android)**
2. **Add domain to Supabase**: `https://yourdomain.com/*`
3. **Configure AASA file** (iOS) and **assetlinks.json** (Android)
4. **Update redirect URLs** to use HTTPS

See: https://supabase.com/docs/guides/auth/native-mobile-deep-linking

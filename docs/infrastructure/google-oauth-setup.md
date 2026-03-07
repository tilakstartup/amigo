# Google OAuth Setup Guide

This guide walks you through setting up Google OAuth authentication for your Amigo Health app with Supabase.

## Prerequisites

- Supabase project created
- Google Cloud Console account
- Deep linking already configured (see `supabase-deeplink-setup.md`)

## Step 1: Create Google OAuth Credentials

### 1.1 Go to Google Cloud Console

1. Visit [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Navigate to **APIs & Services** > **Credentials**

### 1.2 Configure OAuth Consent Screen

1. Click **OAuth consent screen** in the left sidebar
2. Select **External** user type (unless you have a Google Workspace)
3. Click **Create**
4. Fill in the required fields:
   - **App name**: Amigo Health
   - **User support email**: Your email
   - **Developer contact information**: Your email
5. Click **Save and Continue**
6. Skip the **Scopes** section (click **Save and Continue**)
7. Add test users if needed (for testing phase)
8. Click **Save and Continue** and then **Back to Dashboard**

### 1.3 Create OAuth 2.0 Client IDs

You need to create **three** OAuth client IDs:

#### A. Web Application (for Supabase)

1. Click **Create Credentials** > **OAuth client ID**
2. Select **Web application**
3. Name it: `Amigo Health - Supabase`
4. Under **Authorized redirect URIs**, add:
   ```
   https://[YOUR-PROJECT-REF].supabase.co/auth/v1/callback
   ```
   Replace `[YOUR-PROJECT-REF]` with your Supabase project reference ID
   (Find it in Supabase Dashboard > Settings > API > Project URL)
5. Click **Create**
6. **Save the Client ID and Client Secret** - you'll need these for Supabase

#### B. Android Application

1. Click **Create Credentials** > **OAuth client ID**
2. Select **Android**
3. Name it: `Amigo Health - Android`
4. Package name: `com.amigo.android`
5. Get your SHA-1 certificate fingerprint:
   ```bash
   # For debug keystore
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   
   # For release keystore (when you have one)
   keytool -list -v -keystore /path/to/your/release.keystore -alias your-alias
   ```
6. Copy the SHA-1 fingerprint and paste it
7. Click **Create**
8. **Save the Client ID**

#### C. iOS Application

1. Click **Create Credentials** > **OAuth client ID**
2. Select **iOS**
3. Name it: `Amigo Health - iOS`
4. Bundle ID: `com.amigo.ios`
5. Click **Create**
6. **Save the Client ID**

## Step 2: Configure Supabase

### 2.1 Enable Google Provider

1. Go to your Supabase Dashboard
2. Navigate to **Authentication** > **Providers**
3. Find **Google** in the list and click to expand
4. Toggle **Enable Sign in with Google** to ON
5. Enter the credentials from Step 1.3.A:
   - **Client ID**: Paste the Web Application Client ID
   - **Client Secret**: Paste the Web Application Client Secret
6. Click **Save**

### 2.2 Configure Redirect URLs (if not already done)

1. In Supabase Dashboard, go to **Authentication** > **URL Configuration**
2. Add your redirect URLs:
   - **Site URL**: `https://yourdomain.com` (or your production URL)
   - **Redirect URLs**: Add these:
     ```
     com.amigo.android://oauth-callback
     com.amigo.ios://oauth-callback
     http://localhost:3000
     ```

## Step 3: Update Your App Configuration

### 3.1 Update .env file

Add the Google OAuth client IDs to your `.env` file:

```env
# Existing Supabase config
SUPABASE_URL=your_supabase_url
SUPABASE_ANON_KEY=your_supabase_anon_key

# Google OAuth Client IDs
GOOGLE_WEB_CLIENT_ID=your_web_client_id_from_step_1.3.A
GOOGLE_ANDROID_CLIENT_ID=your_android_client_id_from_step_1.3.B
GOOGLE_IOS_CLIENT_ID=your_ios_client_id_from_step_1.3.C
```

### 3.2 Update Android Configuration

The Android app should already be configured with the deep link scheme. Verify in `mobile/android/src/main/AndroidManifest.xml`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="com.amigo.android" android:host="oauth-callback" />
</intent-filter>
```

### 3.3 Update iOS Configuration

The iOS app should already be configured with the URL scheme. Verify in `mobile/ios/Amigo/Info.plist`:

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>com.amigo.ios</string>
        </array>
    </dict>
</array>
```

## Step 4: Test Google OAuth

### 4.1 Test on Android

1. Launch the Android app
2. Click "Continue with Google"
3. Select a Google account
4. Grant permissions
5. You should be redirected back to the app and signed in

### 4.2 Test on iOS

1. Launch the iOS app
2. Click "Continue with Google"
3. Select a Google account
4. Grant permissions
5. You should be redirected back to the app and signed in

## Troubleshooting

### Error: "redirect_uri_mismatch"

- Check that the redirect URI in Google Cloud Console matches exactly:
  `https://[YOUR-PROJECT-REF].supabase.co/auth/v1/callback`
- Make sure there are no trailing slashes

### Error: "invalid_client"

- Verify the Client ID and Client Secret in Supabase match the Web Application credentials
- Make sure you're using the Web Application credentials, not Android or iOS

### Deep Link Not Working

- Verify the deep link schemes are configured correctly (see `supabase-deeplink-setup.md`)
- Check that the redirect URLs in Supabase include your app schemes
- On Android, verify the SHA-1 fingerprint matches your keystore

### "This app is not verified" Warning

- This is normal during development
- Click "Advanced" > "Go to [App Name] (unsafe)" to proceed
- For production, you'll need to verify your app with Google

## Production Checklist

Before going to production:

- [ ] Verify your app with Google (OAuth consent screen)
- [ ] Generate and use release keystore for Android
- [ ] Update SHA-1 fingerprint in Google Cloud Console with release keystore
- [ ] Update redirect URLs to use production domain
- [ ] Test OAuth flow on physical devices
- [ ] Add privacy policy and terms of service URLs to OAuth consent screen

## Additional Resources

- [Supabase Google OAuth Documentation](https://supabase.com/docs/guides/auth/social-login/auth-google)
- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Android Deep Linking Guide](https://developer.android.com/training/app-links)
- [iOS Universal Links Guide](https://developer.apple.com/ios/universal-links/)

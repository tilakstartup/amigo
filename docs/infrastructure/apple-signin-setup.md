# Apple Sign In Setup Guide

This guide walks you through setting up Apple Sign In for your Amigo Health iOS app with Supabase.

## Prerequisites

- Apple Developer Account (paid membership required)
- Supabase project created
- iOS app with bundle ID: `com.amigo.ios`

## Step 1: Configure Apple Developer Account

### 1.1 Enable Sign in with Apple for Your App ID

1. Go to [Apple Developer Portal](https://developer.apple.com/account/)
2. Navigate to **Certificates, Identifiers & Profiles**
3. Click **Identifiers** in the left sidebar
4. Find and click your App ID: `com.amigoai.ios` (or create it if it doesn't exist)
5. In the Capabilities section, check **Sign in with Apple**
6. Click **Save**

### 1.2 Create a Services ID

1. In **Certificates, Identifiers & Profiles**, click **Identifiers**
2. Click the **+** button to create a new identifier
3. Select **Services IDs** and click **Continue**
4. Fill in the details:
   - **Description**: Amigo Health Web
   - **Identifier**: `com.amigoai.ios.web` (must be different from your app bundle ID)
5. Click **Continue** and then **Register**

### 1.3 Configure the Services ID

1. Click on the Services ID you just created (`com.amigo.ios.web`)
2. Check **Sign in with Apple**
3. Click **Configure** next to Sign in with Apple
4. In the configuration dialog:
   - **Primary App ID**: Select `com.amigoai.ios`
   - **Domains and Subdomains**: Add your Supabase project domain:
     ```
     hibbnohfwvbglyxgyaav.supabase.co
     ```
   - **Return URLs**: Add your Supabase callback URL:
     ```
     https://hibbnohfwvbglyxgyaav.supabase.co/auth/v1/callback
     ```
5. Click **Save**
6. Click **Continue** and then **Save** again

### 1.4 Create a Key for Sign in with Apple

1. In **Certificates, Identifiers & Profiles**, click **Keys** in the left sidebar
2. Click the **+** button to create a new key
3. Fill in the details:
   - **Key Name**: Amigo Health Apple Sign In Key
4. Check **Sign in with Apple**
5. Click **Configure** next to Sign in with Apple
6. Select your Primary App ID: `com.amigo.ios`
7. Click **Save**
8. Click **Continue** and then **Register**
9. **IMPORTANT**: Download the key file (`.p8` file) - you can only download it once!
10. Note down:
    - **Key ID** (10 characters, shown on the download page)
    - **Team ID** (10 characters, found in the top right of the Apple Developer portal)

## Step 2: Configure Supabase

### 2.1 Enable Apple Provider

1. Go to your Supabase Dashboard
2. Navigate to **Authentication** > **Providers**
3. Find **Apple** in the list and click to expand
4. Toggle **Enable Sign in with Apple** to ON
5. Fill in the required fields:
   - **Services ID**: `com.amigoai.ios.web` (the Services ID you created)
   - **Team ID**: Your 10-character Apple Team ID
   - **Key ID**: Your 10-character Key ID from the downloaded key
   - **Secret Key**: Open the `.p8` file you downloaded and paste the entire contents (including the BEGIN and END lines)
6. Click **Save**

### 2.2 Verify Redirect URLs

1. In Supabase Dashboard, go to **Authentication** > **URL Configuration**
2. Make sure your redirect URLs include:
   ```
   com.amigoai.ios://oauth-callback
   ```

## Step 3: Configure iOS App

### 3.1 Add Sign in with Apple Capability

The capability should already be added to your Xcode project, but let's verify:

1. Open `mobile/ios/Amigo.xcodeproj` in Xcode
2. Select the **Amigo** target
3. Go to the **Signing & Capabilities** tab
4. If "Sign in with Apple" is not listed, click **+ Capability** and add it
5. Make sure your Team is selected in the Signing section

### 3.2 Update Info.plist (Already Done)

The URL scheme should already be configured in `mobile/ios/Amigo/Info.plist`:

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

## Step 4: Test Apple Sign In

### 4.1 Test on iOS Simulator

1. Launch the iOS app in the simulator
2. Click "Continue with Apple"
3. Sign in with your Apple ID
4. Choose whether to share or hide your email
5. You should be redirected back to the app and signed in

### 4.2 Test on Physical Device

For the best testing experience, use a physical iOS device:

1. Connect your iPhone/iPad
2. Build and run the app on the device
3. Click "Continue with Apple"
4. Use Face ID/Touch ID to authenticate
5. You should be redirected back to the app

## Troubleshooting

### Error: "Invalid client"

- Verify the Services ID in Supabase matches exactly: `com.amigo.ios.web`
- Check that the Team ID and Key ID are correct
- Make sure the Secret Key includes the full contents of the `.p8` file

### Error: "Invalid redirect URI"

- Verify the Return URL in Apple Developer Portal matches:
  `https://hibbnohfwvbglyxgyaav.supabase.co/auth/v1/callback`
- Check that the domain is added: `hibbnohfwvbglyxgyaav.supabase.co`

### Sign in with Apple button not showing

- Make sure the "Sign in with Apple" capability is added in Xcode
- Verify your app is signed with a valid provisioning profile
- Check that your Apple Developer account is in good standing

### "This app is not verified" or similar errors

- Make sure you've enabled Sign in with Apple for your App ID
- Verify the Services ID is properly configured
- Check that the Primary App ID is set correctly in the Services ID configuration

## Production Checklist

Before going to production:

- [ ] Test Apple Sign In on physical iOS devices
- [ ] Verify email sharing options work correctly
- [ ] Test with different Apple IDs
- [ ] Ensure proper error handling for declined sign-ins
- [ ] Update redirect URLs if using a custom domain
- [ ] Test the "Hide My Email" feature
- [ ] Verify user data is properly stored in Supabase

## Additional Resources

- [Apple Sign In Documentation](https://developer.apple.com/sign-in-with-apple/)
- [Supabase Apple OAuth Documentation](https://supabase.com/docs/guides/auth/social-login/auth-apple)
- [Apple Human Interface Guidelines for Sign in with Apple](https://developer.apple.com/design/human-interface-guidelines/sign-in-with-apple)

## Important Notes

### Email Privacy

Apple allows users to hide their email address. When a user chooses "Hide My Email":
- Apple generates a unique relay email address
- Emails sent to this address are forwarded to the user's real email
- The relay email format: `[random]@privaterelay.appleid.com`
- Your app should handle these relay emails properly

### User Identification

- Apple provides a unique user identifier that remains constant
- This identifier is different from the user's Apple ID
- Use this identifier to link the user across sessions

### Testing

- You can test with any Apple ID
- The simulator works for basic testing
- Physical devices provide the full experience with Face ID/Touch ID

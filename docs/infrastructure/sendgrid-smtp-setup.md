# SendGrid SMTP Setup for Supabase

SendGrid provides a free tier with 100 emails/day, perfect for development and authentication emails.

## Quick Setup (2 minutes)

### Step 1: Create SendGrid Account

1. Go to https://signup.sendgrid.com/
2. Fill in your details:
   - Email address
   - Password
   - Company name (can use "Personal" or your app name)
3. Click "Create Account"
4. Verify your email address

### Step 2: Create API Key

1. Log in to SendGrid Dashboard
2. Go to **Settings** → **API Keys** (https://app.sendgrid.com/settings/api_keys)
3. Click **"Create API Key"**
4. Configure:
   - **API Key Name**: `Supabase SMTP`
   - **API Key Permissions**: Select **"Restricted Access"**
   - Expand **"Mail Send"** and toggle it **ON**
   - Leave everything else OFF
5. Click **"Create & View"**
6. **IMPORTANT**: Copy the API key now - you won't see it again!
   - It looks like: `SG.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

### Step 3: Verify Sender Identity

SendGrid requires you to verify your sender email:

1. Go to **Settings** → **Sender Authentication** (https://app.sendgrid.com/settings/sender_auth)
2. Click **"Verify a Single Sender"**
3. Fill in the form:
   - **From Name**: `Amigo` (or your app name)
   - **From Email Address**: Your email (e.g., `noreply@yourdomain.com` or your personal email)
   - **Reply To**: Same as From Email
   - **Company Address**: Your address
   - **City, State, Zip, Country**: Your location
4. Click **"Create"**
5. Check your email and click the verification link
6. Wait for verification (usually instant)

### Step 4: Configure Supabase

1. Go to Supabase Dashboard: https://supabase.com/dashboard
2. Select your project: `hibbnohfwvbglyxgyaav`
3. Navigate to: **Settings** → **Auth** → **SMTP Settings**
4. Enable **"Enable Custom SMTP"**
5. Enter the following:

```
SMTP Host:      smtp.sendgrid.net
SMTP Port:      587
SMTP Username:  apikey
SMTP Password:  <Your API Key from Step 2>
Sender Email:   <Your verified email from Step 3>
Sender Name:    Amigo
```

**IMPORTANT**: 
- Username is literally the word `apikey` (not your email!)
- Password is your SendGrid API key (starts with `SG.`)

6. Click **"Save"**

### Step 5: Test

Try signing up a new user in your app! You should receive the confirmation email.

## Free Tier Limits

- **100 emails per day** (3,000 per month)
- **No credit card required**
- **No sandbox mode** - send to anyone
- **Perfect for development and testing**

## Monitoring

### Check Email Activity

1. Go to SendGrid Dashboard → **Activity** (https://app.sendgrid.com/email_activity)
2. View sent emails, deliveries, bounces, and opens

### Check Statistics

1. Go to **Stats** → **Overview** (https://app.sendgrid.com/statistics)
2. See daily/monthly email volume

## Troubleshooting

### Email Not Received

1. **Check SendGrid Activity Feed**:
   - Go to Activity tab
   - Look for your email
   - Check status (Delivered, Bounced, etc.)

2. **Check Spam Folder**:
   - SendGrid emails sometimes go to spam initially
   - Mark as "Not Spam" to train filters

3. **Verify Sender Email**:
   - Make sure sender email is verified in SendGrid
   - Check Settings → Sender Authentication

4. **Check API Key Permissions**:
   - Ensure "Mail Send" permission is enabled
   - Try creating a new API key if needed

### Authentication Failed

- **Username must be `apikey`** (not your email!)
- **Password must be your API Key** (starts with `SG.`)
- Check for extra spaces when copying API key
- Verify API key has "Mail Send" permission

### Rate Limit Exceeded

- Free tier: 100 emails/day
- Upgrade to paid plan if you need more
- Or wait until next day for limit reset

## Upgrading

When you need more than 100 emails/day:

### Essentials Plan - $19.95/month
- 50,000 emails/month
- Email validation
- Dedicated IP (optional)

### Pro Plan - $89.95/month
- 100,000 emails/month
- Advanced analytics
- Dedicated IP included
- Subuser management

See pricing: https://sendgrid.com/pricing/

## Security Best Practices

1. **Protect Your API Key**:
   - Never commit to git
   - Store in environment variables or secrets manager
   - Rotate regularly

2. **Use Restricted API Keys**:
   - Only enable "Mail Send" permission
   - Create separate keys for different purposes

3. **Monitor Activity**:
   - Check Activity Feed regularly
   - Watch for unusual sending patterns
   - Set up alerts for bounces/spam reports

4. **Rotate API Keys**:
   ```
   1. Create new API key
   2. Update Supabase with new key
   3. Test email sending
   4. Delete old API key
   ```

## Email Templates

Customize your authentication emails in Supabase:

1. Go to **Authentication** → **Email Templates**
2. Customize:
   - **Confirm signup**: Welcome email
   - **Magic link**: Passwordless login
   - **Reset password**: Password reset
   - **Change email**: Email change confirmation

## Cost Comparison

| Service | Free Tier | Paid Plans |
|---------|-----------|------------|
| **SendGrid** | 100/day (3,000/month) | $19.95/month for 50K |
| **AWS SES** | 62,000/month (from EC2) | $0.10 per 1,000 |
| **Mailgun** | 100/day (3,000/month) | $35/month for 50K |
| **Postmark** | 100/month | $15/month for 10K |

SendGrid offers the best free tier for getting started!

## Additional Resources

- [SendGrid Documentation](https://docs.sendgrid.com/)
- [SendGrid API Reference](https://docs.sendgrid.com/api-reference)
- [Supabase SMTP Configuration](https://supabase.com/docs/guides/auth/auth-smtp)
- [SendGrid Best Practices](https://docs.sendgrid.com/ui/sending-email/deliverability)

## Support

- SendGrid Support: https://support.sendgrid.com/
- SendGrid Status: https://status.sendgrid.com/
- Community Forum: https://community.sendgrid.com/

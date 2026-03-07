# Supabase Setup

## Prerequisites

- Supabase CLI installed (`brew install supabase/tap/supabase`)
- Supabase account (https://supabase.com)

## Initialize Supabase Project

The project has been initialized with `supabase init`. This created:
- `supabase/config.toml` - Supabase configuration
- `supabase/migrations/` - Database migration files
- `supabase/functions/` - Edge functions (if needed)

## Create Supabase Project

### Option 1: Supabase Dashboard

1. Go to https://supabase.com/dashboard
2. Click "New project"
3. Fill in details:
   - Name: `amigo-dev`
   - Database Password: (generate strong password)
   - Region: Choose closest to your users
4. Wait for project creation (~2 minutes)

### Option 2: Supabase CLI

```bash
supabase projects create amigo-dev --org-id <your-org-id> --region us-east-1
```

## Link Local Project to Remote

```bash
supabase link --project-ref <your-project-ref>
```

You'll find your project ref in the Supabase dashboard URL:
`https://supabase.com/dashboard/project/<project-ref>`

## Configure Environment Variables

Add to `.env` file (DO NOT COMMIT):

```env
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
SUPABASE_SERVICE_ROLE_KEY=<your-service-role-key>
```

Get these values from:
- Dashboard → Project Settings → API

## Database Migrations

Migrations will be created in later tasks. For now, the structure is ready.

To apply migrations later:

```bash
supabase db push
```

## Local Development (Optional)

To run Supabase locally:

```bash
# Start local Supabase
supabase start

# This will start:
# - PostgreSQL database
# - Auth server
# - Storage server
# - Realtime server
# - Studio (web UI)
```

Local URLs will be displayed. Use these for development.

## Verify Setup

```bash
# Check status
supabase status

# Check link
supabase projects list
```

## Next Steps

- Create database schema (Task 5)
- Set up Row Level Security policies
- Configure storage buckets
- Test authentication

## Troubleshooting

### CLI Not Found

```bash
brew install supabase/tap/supabase
```

### Link Failed

- Verify project ref is correct
- Check you're logged in: `supabase login`
- Verify project exists in dashboard

### Migration Errors

- Check SQL syntax
- Verify table names
- Check for conflicts with existing tables

## Security Notes

1. **Never commit** `.env` file
2. **Use anon key** in mobile apps (it's safe for client-side)
3. **Never expose** service role key in mobile apps
4. **Enable RLS** on all tables
5. **Test RLS policies** thoroughly

## Cost Considerations

Supabase Free Tier:
- 500MB database
- 1GB file storage
- 2GB bandwidth
- 50,000 monthly active users

This is sufficient for development and early testing.

Paid tiers start at $25/month for production use.

# Verification: Shared Module — JWT Verification (`auth.ts`)

## What was verified

`supabase/functions/_shared/auth.ts` — exports `verifyJwt(jwt: string): Promise<JwtPayload | null>` using Supabase Auth.

## Automated checks

### Type-check

```bash
~/.deno/bin/deno check --config supabase/functions/deno.json supabase/functions/_shared/auth.ts
```

**Result:** exits 0, no errors printed. ✅ PASS

## Manual checks required

The following require a live Supabase environment and a valid JWT:

```bash
# Set env vars first
export TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"[email]","password":"[password]"}' \
  | jq -r '.access_token')

# Smoke-test with valid token
SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
~/.deno/bin/deno eval "
  import { verifyJwt } from './supabase/functions/_shared/auth.ts';
  const result = await verifyJwt('$TOKEN');
  console.log(JSON.stringify(result));
"
# Expected: { "userId": "<uuid>", "subscriptionStatus": "free", "isActive": true }

# Smoke-test with invalid token
SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
~/.deno/bin/deno eval "
  import { verifyJwt } from './supabase/functions/_shared/auth.ts';
  const result = await verifyJwt('garbage-token');
  console.log(JSON.stringify(result));
"
# Expected: null
```

## Status

| Check | Status |
|---|---|
| Type-check (deno check) | ✅ PASS |
| Valid token returns JwtPayload | ⏳ Manual |
| Invalid token returns null | ⏳ Manual |
| Expired token returns null | ⏳ Manual |

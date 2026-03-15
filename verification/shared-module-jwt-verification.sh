#!/bin/bash
set -e

# Load env vars if .env exists
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

echo "=== Task 3: JWT Verification (auth.ts) ==="

# 1. Type-check the file
echo ""
echo "[1] Type-checking auth.ts..."
if deno check --config supabase/functions/deno.json supabase/functions/_shared/auth.ts 2>&1; then
  echo "PASS: auth.ts type-checks cleanly"
else
  echo "FAIL: auth.ts has type errors"
  exit 1
fi

# 2. Smoke-test verifyJwt with a real token (requires SUPABASE_URL, SUPABASE_ANON_KEY, TOKEN)
if [ -z "$TOKEN" ]; then
  echo ""
  echo "[2] Skipping live verifyJwt smoke test — TOKEN not set"
  echo "    To run: export TOKEN=<your-jwt> and re-run this script"
else
  echo ""
  echo "[2] Smoke-testing verifyJwt with real token..."
  RESULT=$(SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
    deno eval "
      import { verifyJwt } from './supabase/functions/_shared/auth.ts';
      const result = await verifyJwt('$TOKEN');
      console.log(JSON.stringify(result));
    " 2>&1)
  echo "Result: $RESULT"
  if echo "$RESULT" | grep -q '"userId"'; then
    echo "PASS: verifyJwt returned a valid JwtPayload"
  else
    echo "FAIL: verifyJwt did not return expected payload"
    exit 1
  fi

  echo ""
  echo "[3] Smoke-testing verifyJwt with garbage token..."
  NULL_RESULT=$(SUPABASE_URL=$SUPABASE_URL SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY \
    deno eval "
      import { verifyJwt } from './supabase/functions/_shared/auth.ts';
      const result = await verifyJwt('not-a-real-token');
      console.log(JSON.stringify(result));
    " 2>&1)
  echo "Result: $NULL_RESULT"
  if [ "$NULL_RESULT" = "null" ]; then
    echo "PASS: verifyJwt returned null for invalid token"
  else
    echo "FAIL: verifyJwt should return null for invalid token"
    exit 1
  fi
fi

echo ""
echo "=== All automated checks passed ==="

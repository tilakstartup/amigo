import { createClient } from "@supabase/supabase-js"
import type { JwtPayload, SubscriptionStatus } from "./types.ts"

// Creates a Supabase anon client using env vars.
function getAnonClient() {
  const url = Deno.env.get("SUPABASE_URL") ?? ""
  const key = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
  return createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
}

/**
 * Decodes the JWT payload (base64url) without verifying the signature.
 * Signature verification is handled by Supabase Auth's getUser() call.
 * We decode separately only to read custom top-level claims (e.g. user_subscription)
 * that are injected at signing time and not returned by getUser().
 */
// deno-lint-ignore no-explicit-any
function decodeJwtPayload(jwt: string): Record<string, any> | null {
  try {
    const parts = jwt.split('.')
    if (parts.length !== 3) return null
    let b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    // Add padding
    b64 += '='.repeat((4 - b64.length % 4) % 4)
    const decoded = atob(b64)
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

// Extracts subscription info from the JWT payload.
// user_subscription is a custom top-level claim injected at signing time.
// Falls back to { subscriptionStatus: 'free', isActive: true } when claim is absent.
function extractSubscription(
  // deno-lint-ignore no-explicit-any
  jwtPayload: Record<string, any>,
): Pick<JwtPayload, "subscriptionStatus" | "isActive"> {
  const claim = jwtPayload?.user_subscription
  if (!claim) {
    return { subscriptionStatus: "free", isActive: true }
  }

  const rawStatus: string = claim.subscription_status ?? "free"
  const subscriptionStatus: SubscriptionStatus =
    rawStatus === "pro" ? "pro" : "free"

  const isActive: boolean =
    claim.is_active === undefined ? true : Boolean(claim.is_active)

  return { subscriptionStatus, isActive }
}

/**
 * Verifies a JWT using Supabase Auth and returns a JwtPayload.
 * Returns null if the JWT is absent, malformed, expired, or if any error occurs.
 *
 * Requirements: 2.1, 2.2, 2.3, 12.1
 */
export async function verifyJwt(jwt: string): Promise<JwtPayload | null> {
  try {
    const client = getAnonClient()
    const { data, error } = await client.auth.getUser(jwt)

    if (error || !data?.user) {
      return null
    }

    const userId = data.user.id
    if (!userId) return null

    // Decode JWT to read custom top-level claims (user_subscription)
    // getUser() only returns app_metadata/user_metadata, not custom top-level claims
    const jwtPayload = decodeJwtPayload(jwt) ?? {}
    const { subscriptionStatus, isActive } = extractSubscription(jwtPayload)

    console.log('[auth] verifyJwt:', { userId, subscriptionStatus, isActive, hasUserSubscription: !!jwtPayload.user_subscription })

    return { userId, subscriptionStatus, isActive }
  } catch {
    // Network failure, unexpected error — return null per spec
    return null
  }
}

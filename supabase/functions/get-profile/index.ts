import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  const requestId = crypto.randomUUID()
  const startedAt = Date.now()
  try {
    console.log('[get-profile] request.start', JSON.stringify({
      requestId,
      method: req.method,
      hasAuthHeader: !!req.headers.get('X-Amigo-Auth')
    }))

    // User JWT is passed in custom header and validated in-function
    const authHeader = req.headers.get('X-Amigo-Auth') || ''
    const jwt = authHeader.replace('Bearer ', '').trim()

    if (!jwt) {
      console.warn('[get-profile] auth.missing', JSON.stringify({ requestId }))
      return new Response(
        JSON.stringify({ error: 'Missing X-Amigo-Auth header (Bearer <user_jwt>)' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Validate user token via Supabase Auth
    const authClient = createClient(
      Deno.env.get('SUPABASE_URL') || '',
      Deno.env.get('SUPABASE_ANON_KEY') || ''
    )

    const { data: authData, error: authError } = await authClient.auth.getUser(jwt)

    if (authError || !authData.user) {
      console.warn('[get-profile] auth.invalid', JSON.stringify({
        requestId,
        message: authError?.message || 'No user in auth response'
      }))
      return new Response(
        JSON.stringify({ error: 'Invalid user JWT' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const userId = authData.user.id
    console.log('[get-profile] auth.ok', JSON.stringify({ requestId, userId }))

    // Create Supabase client with user JWT for RLS enforcement
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') || '',
      Deno.env.get('SUPABASE_ANON_KEY') || '',
      {
        global: {
          headers: {
            Authorization: `Bearer ${jwt}`
          }
        }
      }
    )

    // Fetch user profile (RLS enforced - can only access own profile)
    const { data: profile, error: profileError } = await supabase
      .from('users_profiles')
      .select('*')
      .eq('id', userId)
      .single()

    if (profileError && profileError.code !== 'PGRST116') {
      console.error('[get-profile] profile.fetch_error', JSON.stringify({
        requestId,
        userId,
        code: profileError.code,
        message: profileError.message
      }))
      return new Response(
        JSON.stringify({ error: `Error fetching profile: ${profileError.message}` }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    console.log('[get-profile] profile.fetch_ok', JSON.stringify({
      requestId,
      userId,
      profileFound: !!profile
    }))

    const displayName = profile?.display_name || ''
    const nameParts = displayName.trim().split(/\s+/).filter(Boolean)
    const firstName = nameParts.length > 0 ? nameParts[0] : null
    const lastName = nameParts.length > 1 ? nameParts.slice(1).join(' ') : null

    // Return legacy onboarding-compatible shape mapped from users_profiles
    const responseData = {
      id: userId,
      email: profile?.email || authData.user.email || null,
      first_name: firstName,
      last_name: lastName,
      weight: profile?.weight_kg ?? null,
      height: profile?.height_cm ?? null,
      age: profile?.age ?? null,
      gender: null,
      activity_level: profile?.activity_level ?? null,
      onboarding_completed: profile?.onboarding_completed ?? false,
      display_name: profile?.display_name ?? null,
      weight_kg: profile?.weight_kg ?? null,
      height_cm: profile?.height_cm ?? null
    }

    const response = new Response(
      JSON.stringify(responseData),
      { 
        status: 200,
        headers: { 'Content-Type': 'application/json' } 
      }
    )

    console.log('[get-profile] request.end', JSON.stringify({
      requestId,
      userId,
      durationMs: Date.now() - startedAt
    }))

    return response

  } catch (error) {
    console.error('[get-profile] request.error', JSON.stringify({
      requestId,
      durationMs: Date.now() - startedAt,
      message: error instanceof Error ? error.message : String(error)
    }))
    return new Response(
      JSON.stringify({ 
        error: error instanceof Error ? error.message : 'Unknown error' 
      }),
      { 
        status: 500,
        headers: { 'Content-Type': 'application/json' } 
      }
    )
  }
})

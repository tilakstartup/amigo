import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  try {
    // User JWT is passed in custom header and validated in-function
    const authHeader = req.headers.get('X-Amigo-Auth') || ''
    const jwt = authHeader.replace('Bearer ', '').trim()

    if (!jwt) {
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
      return new Response(
        JSON.stringify({ error: 'Invalid user JWT' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const userId = authData.user.id

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
      console.error('Profile fetch error:', profileError)
      return new Response(
        JSON.stringify({ error: `Error fetching profile: ${profileError.message}` }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

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

    return new Response(
      JSON.stringify(responseData),
      { 
        status: 200,
        headers: { 'Content-Type': 'application/json' } 
      }
    )

  } catch (error) {
    console.error('Error in get-profile:', error)
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

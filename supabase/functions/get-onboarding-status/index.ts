import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  const requestId = crypto.randomUUID()
  const startedAt = Date.now()
  try {
    console.log('[get-onboarding-status] request.start', JSON.stringify({
      requestId,
      method: req.method,
      hasAuthHeader: !!req.headers.get('X-Amigo-Auth')
    }))

    // User JWT is passed in custom header and validated in-function
    const authHeader = req.headers.get('X-Amigo-Auth') || ''
    const jwt = authHeader.replace('Bearer ', '').trim()

    if (!jwt) {
      console.warn('[get-onboarding-status] auth.missing', JSON.stringify({ requestId }))
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
      console.warn('[get-onboarding-status] auth.invalid', JSON.stringify({
        requestId,
        message: authError?.message || 'No user in auth response'
      }))
      return new Response(
        JSON.stringify({ error: 'Invalid user JWT' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const userId = authData.user.id
    console.log('[get-onboarding-status] auth.ok', JSON.stringify({ requestId, userId }))


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

    // Fetch user profile to check onboarding status
    const { data: profile, error: profileError } = await supabase
      .from('users_profiles')
      .select('display_name, age, height_cm, weight_kg, activity_level, onboarding_completed')
      .eq('id', userId)
      .single()

    if (profileError && profileError.code !== 'PGRST116') {
      console.error('[get-onboarding-status] profile.fetch_error', JSON.stringify({
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

    console.log('[get-onboarding-status] profile.fetch_ok', JSON.stringify({
      requestId,
      userId,
      profileFound: !!profile,
      onboardingCompleted: profile?.onboarding_completed || false
    }))

    const displayName = profile?.display_name || ''
    const hasFirstName = displayName.trim().split(/\s+/).filter(Boolean).length > 0
    const hasLastName = displayName.trim().split(/\s+/).filter(Boolean).length > 1

    const fieldStates: Record<string, boolean> = {
      first_name: hasFirstName,
      last_name: hasLastName,
      weight: profile?.weight_kg != null,
      height: profile?.height_cm != null,
      age: profile?.age != null,
      gender: false,
      activity_level: !!profile?.activity_level
    }

    const requiredFields = Object.keys(fieldStates)
    const completedFields = requiredFields.filter((field) => fieldStates[field])

    const missingFields = requiredFields.filter(field => !completedFields.includes(field))

    const status = {
      user_id: userId,
      onboarding_completed: profile?.onboarding_completed || false,
      first_name: hasFirstName ? displayName.trim().split(/\s+/)[0] : null,
      last_name: hasLastName ? displayName.trim().split(/\s+/).slice(1).join(' ') : null,
      age: profile?.age ?? null,
      weight: profile?.weight_kg ?? null,
      height: profile?.height_cm ?? null,
      gender: null,
      activity_level: profile?.activity_level ?? null,
      progress: {
        completed_fields: completedFields,
        missing_fields: missingFields,
        total_required: requiredFields.length,
        completed_count: completedFields.length,
        completion_percentage: Math.round((completedFields.length / requiredFields.length) * 100)
      },
      current_profile: profile || {}
    }

    const response = new Response(
      JSON.stringify(status),
      { 
        status: 200,
        headers: { 'Content-Type': 'application/json' } 
      }
    )

    console.log('[get-onboarding-status] request.end', JSON.stringify({
      requestId,
      userId,
      completionPercentage: status.progress.completion_percentage,
      durationMs: Date.now() - startedAt
    }))

    return response

  } catch (error) {
    console.error('[get-onboarding-status] request.error', JSON.stringify({
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

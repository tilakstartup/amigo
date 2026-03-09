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

    // Fetch user profile to check onboarding status
    const { data: profile, error: profileError } = await supabase
      .from('users_profiles')
      .select('display_name, age, height_cm, weight_kg, activity_level, onboarding_completed')
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

    return new Response(
      JSON.stringify(status),
      { 
        status: 200,
        headers: { 'Content-Type': 'application/json' } 
      }
    )

  } catch (error) {
    console.error('Error in get-onboarding-status:', error)
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

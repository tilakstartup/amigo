import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

interface OnboardingData {
  first_name?: string
  last_name?: string
  weight?: number
  height?: number
  age?: number
  gender?: string
  activity_level?: string
  onboarding_completed?: boolean
}

function normalizeActivityLevel(value?: string): string | null {
  if (!value) return null
  const normalized = value.trim().toLowerCase()
  const map: Record<string, string> = {
    sedentary: 'sedentary',
    light: 'lightly_active',
    lightly_active: 'lightly_active',
    moderate: 'moderately_active',
    moderately_active: 'moderately_active',
    active: 'very_active',
    very_active: 'very_active',
    extremely_active: 'extremely_active'
  }
  return map[normalized] ?? null
}

serve(async (req) => {
  try {
    if (req.method !== 'POST') {
      return new Response(
        JSON.stringify({ error: 'Method not allowed' }),
        { status: 405, headers: { 'Content-Type': 'application/json' } }
      )
    }

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

    // Parse request body
    const body: OnboardingData = await req.json()

    // Validate data
    if (!body || Object.keys(body).length === 0) {
      return new Response(
        JSON.stringify({ error: 'No data provided' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const firstName = body.first_name?.trim()
    const lastName = body.last_name?.trim()
    const displayName = [firstName, lastName].filter(Boolean).join(' ').trim()

    const profileUpdate: Record<string, unknown> = {
      id: userId,
      email: authData.user.email,
      updated_at: new Date().toISOString()
    }

    if (displayName) profileUpdate.display_name = displayName
    if (typeof body.age === 'number') profileUpdate.age = body.age
    if (typeof body.weight === 'number') profileUpdate.weight_kg = body.weight
    if (typeof body.height === 'number') profileUpdate.height_cm = body.height
    const normalizedActivityLevel = normalizeActivityLevel(body.activity_level)
    if (normalizedActivityLevel) profileUpdate.activity_level = normalizedActivityLevel
    if (typeof body.onboarding_completed === 'boolean') profileUpdate.onboarding_completed = body.onboarding_completed

    // Update or insert user profile (RLS enforced - can only write own data)
    const { data: updated, error: updateError } = await supabase
      .from('users_profiles')
      .upsert(
        profileUpdate,
        { onConflict: 'id' }
      )
      .select()
      .single()

    if (updateError) {
      console.error('Update error:', updateError)
      return new Response(
        JSON.stringify({ error: `Error saving onboarding data: ${updateError.message}` }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        user_id: userId,
        profile: {
          ...updated,
          first_name: firstName ?? null,
          last_name: lastName ?? null,
          weight: updated?.weight_kg ?? null,
          height: updated?.height_cm ?? null,
          gender: body.gender ?? null
        },
        message: 'Onboarding data saved successfully'
      }),
      { 
        status: 200,
        headers: { 'Content-Type': 'application/json' } 
      }
    )

  } catch (error) {
    console.error('Error in save-onboarding-data:', error)
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

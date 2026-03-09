import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  try {
    // For testing: accept JWT from header or query param
    const authHeader = req.headers.get('Authorization') || ''
    const jwtFromHeader = authHeader.replace('Bearer ', '').trim()
    
    // If no JWT in header, create the Supabase client without explicit auth
    // The RLS will still apply based on the user context from the request
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') || '',
      Deno.env.get('SUPABASE_ANON_KEY') || ''
    )

    // Try to make a simple public query to test if functions work at all
    const { data: profiles, error } = await supabase
      .from('users_profiles')
      .select('id, email')
      .limit(1)

    if (error) {
      return new Response(
        JSON.stringify({
          error: `Database query failed: ${error.message}`,
          code: error.code
        }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    return new Response(
      JSON.stringify({
        message: 'GET-PROFILE public test endpoint',
        jwt_received: jwtFromHeader ? 'yes' : 'no',
        profiles_count: profiles?.length || 0,
        sample_profile: profiles?.length > 0 ? profiles[0] : null
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error('Error:', error)
    return new Response(
      JSON.stringify({error: error instanceof Error ? error.message : 'Unknown error'}),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  try {
    // TEST: Function without JWT validation -  just try to access with the JWT as Authorization header
    const authHeader = req.headers.get('Authorization') || ''
    console.log(`Received Authorization header: ${authHeader.substring(0, 30)}...`)
    
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') || '',
      Deno.env.get('SUPABASE_ANON_KEY') || ''
    )

    // Try to get user using the Authorization header directly
    // The Supabase client should read the Authorization header from the request context
    const { data: { user }, error: authError } = await supabase.auth.getUser()
    
    if (authError) {
      console.error('Auth error:', authError)
      return new Response(
        JSON.stringify({ 
          error: 'Auth error', 
          details: authError.message,
          authHeader: authHeader.substring(0, 50)
        }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    if (!user) {
      return new Response(
        JSON.stringify({ error: 'No user found', authHeader: authHeader.substring(0, 50) }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    return new Response(
      JSON.stringify({ 
        success: true,
        user_id: user.id,
        email: user.email,
        message: 'Test function working'
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error('Error:', error)
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

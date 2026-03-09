import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  try {
    // Get auth header
    const authHeader = req.headers.get('x-auth-token') || ''
    
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: 'Missing x-auth-token header' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Create Supabase client and set auth context
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') || '',
      Deno.env.get('SUPABASE_ANON_KEY') || '',
      {
        global: {
          headers: {
            Authorization: `Bearer ${authHeader}`
          }
        }
      }
    )

    // Get the user using the JWT
    const { data: { user }, error } = await supabase.auth.getUser(authHeader)
    
    if (error || !user) {
      return new Response(
        JSON.stringify({ error: 'Invalid token', details: error?.message }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Success! We got the user
    return new Response(
      JSON.stringify({
        success: true,
        user_id: user.id,
        email: user.email,
        message: 'JWT validated successfully'
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

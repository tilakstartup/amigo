import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

// Test function that avoids JWT validation issues
serve(async (req) => {
  try {
    // Don't require Authorization header - just create a client
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') || '',
      Deno.env.get('SUPABASE_ANON_KEY') || ''
    )

    // Try a simple query without auth
    const { data, error } = await supabase
      .from('users_profiles')
      .select('count(*)', { count: 'exact' })

    if (error) {
      return new Response(
        JSON.stringify({ error: error.message, code: error.code }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    return new Response(
      JSON.stringify({
        message: 'Database accessible without auth',
        count: data,
        headers_received: Object.fromEntries(req.headers.entries())
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

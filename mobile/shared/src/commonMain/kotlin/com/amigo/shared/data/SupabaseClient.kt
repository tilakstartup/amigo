package com.amigo.shared.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    
    private var _client: SupabaseClient? = null
    
    fun initialize(supabaseUrl: String, supabaseKey: String) {
        if (_client == null) {
            _client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey
            ) {
                install(Auth)
                install(Postgrest)
                install(Storage)
                install(Realtime)
            }
        }
    }
    
    fun getClient(): SupabaseClient {
        return _client ?: throw IllegalStateException(
            "Supabase client not initialized. Call initialize() first."
        )
    }
}

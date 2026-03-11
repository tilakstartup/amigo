package com.amigo.shared.auth

import com.amigo.shared.data.SupabaseClientProvider

/**
 * Factory class to create authentication components
 */
object AuthFactory {
    
    /**
     * Initialize Supabase client with credentials
     */
    fun initializeSupabase(supabaseUrl: String, supabaseKey: String) {
        SupabaseClientProvider.initialize(supabaseUrl, supabaseKey)
    }
    
    /**
     * Get the Supabase client instance
     */
    fun getSupabaseClient() = SupabaseClientProvider.getClient()
    
    /**
     * Create EmailAuthenticator instance
     */
    fun createEmailAuthenticator(): EmailAuthenticator {
        val client = SupabaseClientProvider.getClient()
        return EmailAuthenticator(client)
    }
    
    /**
     * Create OAuthAuthenticator instance
     */
    fun createOAuthAuthenticator(): OAuthAuthenticator {
        val client = SupabaseClientProvider.getClient()
        return OAuthAuthenticator(client)
    }
    
    /**
     * Create SessionManager instance with platform-specific secure storage
     */
    fun createSessionManager(secureStorage: SecureStorage): SessionManager {
        val client = SupabaseClientProvider.getClient()
        return SessionManager(client, secureStorage)
    }
}

package com.amigo.shared.session

import com.amigo.shared.profile.ProfileManager
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient

/**
 * Factory for creating SessionInitializer instances with shared ProfileCache.
 * Implements singleton pattern to ensure single ProfileCache instance across app.
 * 
 * Note: This is a simple singleton suitable for single-threaded initialization.
 * For production apps, ensure create() is called once during app startup.
 */
object SessionInitializerFactory {
    private val profileCache = ProfileCache()
    private var instance: SessionInitializer? = null
    
    /**
     * Create or get SessionInitializer instance using the global SupabaseClient.
     * Uses singleton pattern to maintain shared ProfileCache.
     * 
     * Should be called once during app initialization.
     */
    fun create(sessionManager: SessionManager): SessionInitializer {
        if (instance == null) {
            val supabaseClient = SupabaseClientProvider.getClient()
            val profileManager = ProfileManager(supabaseClient)
            instance = SessionInitializer(profileManager, profileCache, sessionManager)
        }
        return instance!!
    }
    
    /**
     * Create or get SessionInitializer instance with explicit SupabaseClient.
     * Uses singleton pattern to maintain shared ProfileCache.
     * 
     * Should be called once during app initialization.
     */
    fun create(supabaseClient: SupabaseClient, sessionManager: SessionManager): SessionInitializer {
        if (instance == null) {
            val profileManager = ProfileManager(supabaseClient)
            instance = SessionInitializer(profileManager, profileCache, sessionManager)
        }
        return instance!!
    }
    
    /**
     * Get existing SessionInitializer instance.
     * Returns null if not yet created via create().
     */
    fun getInstance(): SessionInitializer? = instance
    
    /**
     * Clear cached instance (useful for testing or logout).
     */
    fun reset() {
        instance = null
        profileCache.clearAll()
    }
}

package com.amigo.shared.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.util.*

class SessionManager(
    private val supabase: SupabaseClient,
    private val secureStorage: SecureStorage
) {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private companion object {
        const val USER_ID_KEY = "cached_user_id"
    }

    /**
     * Initializes the session.
     * The Supabase SDK 3.x automatically loads and manages sessions.
     * We just need to observe the sessionStatus flow.
     */
    suspend fun initialize() {
        // Check initial session status
        val session = supabase.auth.currentSessionOrNull()
        _isAuthenticated.value = session != null
        
        // Note: In a real app, you'd want to collect sessionStatus in a coroutine
        // to keep isAuthenticated in sync. For now, we rely on explicit checks.
    }

    suspend fun signOut() {
        supabase.auth.signOut()
        _isAuthenticated.value = false
        // Clear cached user_id
        secureStorage.remove(USER_ID_KEY)
    }

    /**
     * Call this before ANY database operation.
     * It ensures we have a valid session for RLS.
     * The SDK automatically refreshes expired tokens.
     */
    suspend fun getAccessToken(): String? {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            session?.accessToken
        } catch (e: Exception) {
            println("❌ Could not retrieve session: ${e.message}")
            null
        }
    }

    /**
     * Get the current authenticated user.
     * Returns null if not authenticated.
     * 
     * Workaround for Supabase SDK 3.x: After importSession(), session.user is null.
     * We cache the user_id in secure storage and retrieve it when needed.
     */
    suspend fun getCurrentUser(): com.amigo.shared.auth.models.User? {
        println("🔍 [SessionManager] getCurrentUser() START")
        val session = supabase.auth.currentSessionOrNull()
        if (session == null) {
            println("❌ [SessionManager] getCurrentUser() - No session found")
            return null
        }
        
        println("✅ [SessionManager] getCurrentUser() - Session found")
        val user = session.user
        
        // If user is available from session, use it
        if (user != null) {
            println("✅ [SessionManager] getCurrentUser() - User found from session: id=${user.id}, email=${user.email}")
            return com.amigo.shared.auth.models.User(
                id = user.id ?: "",
                email = user.email ?: "",
                displayName = user.userMetadata?.get("display_name") as? String,
                avatarUrl = user.userMetadata?.get("avatar_url") as? String,
                createdAt = user.createdAt.toString(),
                emailVerified = user.emailConfirmedAt != null
            )
        }
        
        // Workaround: Get cached user_id from secure storage
        println("⚠️ [SessionManager] Session user is null, checking cached user_id")
        val cachedUserId = secureStorage.getString(USER_ID_KEY)
        if (cachedUserId != null) {
            println("✅ [SessionManager] Found cached user_id: $cachedUserId")
            return com.amigo.shared.auth.models.User(
                id = cachedUserId,
                email = "", // We don't cache email, but user_id is what matters
                displayName = null,
                avatarUrl = null,
                createdAt = "",
                emailVerified = false
            )
        }
        
        println("❌ [SessionManager] No cached user_id found")
        return null
    }
    
    /**
     * Set session from OAuth tokens (for deep link handling).
     * In SDK 3.x, we create a UserSession and import it.
     * 
     * We also extract and cache the user_id from the access token JWT
     * as a workaround for the SDK bug where session.user is null after importSession().
     */
    suspend fun setSessionFromTokens(accessToken: String, refreshToken: String, expiresIn: Long = 3600) {
        try {
            println("🔗 [SessionManager] setSessionFromTokens() START")
            println("🔗 [SessionManager] Access token length: ${accessToken.length}")
            println("🔗 [SessionManager] Refresh token length: ${refreshToken.length}")
            println("🔗 [SessionManager] Expires in: $expiresIn seconds")
            
            // Extract and cache user_id from JWT before importing session
            val userId = extractUserIdFromJWT(accessToken)
            if (userId != null) {
                println("🔗 [SessionManager] Caching user_id: $userId")
                secureStorage.saveString(USER_ID_KEY, userId)
            } else {
                println("⚠️ [SessionManager] Could not extract user_id from JWT")
            }
            
            // Create a UserSession from the tokens
            val userSession = io.github.jan.supabase.auth.user.UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                tokenType = "bearer",
                user = null // User will be null initially, but session is valid
            )
            
            println("🔗 [SessionManager] Calling supabase.auth.importSession()...")
            supabase.auth.importSession(userSession)
            
            println("🔗 [SessionManager] Session imported, calling initialize()...")
            initialize()
            
            println("✅ [SessionManager] Session set successfully, isAuthenticated=${_isAuthenticated.value}")
        } catch (e: Exception) {
            println("❌ [SessionManager] Failed to set session from tokens: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * Extract user ID from a JWT access token.
     * JWT format: header.payload.signature
     * The payload contains the 'sub' claim which is the user ID.
     */
    private fun extractUserIdFromJWT(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            
            // Decode base64url payload
            val payload = parts[1]
            val base64 = payload.replace('-', '+').replace('_', '/')
            val paddedBase64 = when (base64.length % 4) {
                2 -> base64 + "=="
                3 -> base64 + "="
                else -> base64
            }
            
            val decodedString = paddedBase64.decodeBase64Bytes().decodeToString()
            
            // Parse JSON and extract 'sub' field
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonPayload = json.parseToJsonElement(decodedString).jsonObject
            jsonPayload["sub"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("⚠️ [SessionManager] Failed to decode JWT: ${e.message}")
            null
        }
    }
}
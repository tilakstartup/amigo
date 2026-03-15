package com.amigo.shared.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private companion object {
        const val USER_ID_KEY = "cached_user_id"
        const val USER_EMAIL_KEY = "cached_user_email"
        const val USER_DISPLAY_NAME_KEY = "cached_user_display_name"
        const val USER_AVATAR_URL_KEY = "cached_user_avatar_url"
    }

    /**
     * Initializes the session.
     * The Supabase SDK 3.x automatically loads and manages sessions.
     * We observe the sessionStatus flow to keep isAuthenticated in sync.
     */
    suspend fun initialize() {
        // Check initial session status
        val session = supabase.auth.currentSessionOrNull()
        _isAuthenticated.value = session != null
        
        // Observe session status changes to keep isAuthenticated in sync
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        println("✅ [SessionManager] Session authenticated")
                        _isAuthenticated.value = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        println("❌ [SessionManager] Session not authenticated")
                        _isAuthenticated.value = false
                    }
                    else -> {
                        println("⏳ [SessionManager] Session status: $status")
                        // Keep current state for other statuses (Initializing, RefreshFailure, etc.)
                    }
                }
            }
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
        _isAuthenticated.value = false
        // Clear all cached user data
        secureStorage.remove(USER_ID_KEY)
        secureStorage.remove(USER_EMAIL_KEY)
        secureStorage.remove(USER_DISPLAY_NAME_KEY)
        secureStorage.remove(USER_AVATAR_URL_KEY)
    }

    /**
     * Call this before ANY database operation.
     * It ensures we have a valid session for RLS.
     * The SDK automatically refreshes expired tokens.
     */
    suspend fun getAccessToken(): String? {
        return try {
            // Refresh session first to ensure token is valid
            supabase.auth.refreshCurrentSession()
            val session = supabase.auth.currentSessionOrNull()
            session?.accessToken
        } catch (e: Exception) {
            println("⚠️ Token refresh failed, returning existing token: ${e.message}")
            // Fall back to current session even if refresh failed
            try {
                supabase.auth.currentSessionOrNull()?.accessToken
            } catch (e2: Exception) {
                println("❌ Could not retrieve session: ${e2.message}")
                null
            }
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
        
        // Workaround: Get cached user data from secure storage
        println("⚠️ [SessionManager] Session user is null, checking cached user data")
        val cachedUserId = secureStorage.getString(USER_ID_KEY)
        if (cachedUserId != null) {
            val cachedEmail = secureStorage.getString(USER_EMAIL_KEY) ?: ""
            val cachedDisplayName = secureStorage.getString(USER_DISPLAY_NAME_KEY)
            val cachedAvatarUrl = secureStorage.getString(USER_AVATAR_URL_KEY)
            
            println("✅ [SessionManager] Found cached user data: id=$cachedUserId, email=$cachedEmail, displayName=$cachedDisplayName, avatarUrl=$cachedAvatarUrl")
            return com.amigo.shared.auth.models.User(
                id = cachedUserId,
                email = cachedEmail,
                displayName = cachedDisplayName,
                avatarUrl = cachedAvatarUrl,
                createdAt = "",
                emailVerified = false
            )
        }
        
        println("❌ [SessionManager] No cached user data found")
        return null
    }
    
    /**
     * Set session from OAuth tokens (for deep link handling).
     * In SDK 3.x, we create a UserSession and import it.
     * 
     * We also extract and cache user data from the access token JWT
     * as a workaround for the SDK bug where session.user is null after importSession().
     */
    suspend fun setSessionFromTokens(accessToken: String, refreshToken: String, expiresIn: Long = 3600) {
        try {
            println("🔗 [SessionManager] setSessionFromTokens() START")
            println("🔗 [SessionManager] Access token length: ${accessToken.length}")
            println("🔗 [SessionManager] Refresh token length: ${refreshToken.length}")
            println("🔗 [SessionManager] Expires in: $expiresIn seconds")
            
            // Extract and cache user data from JWT before importing session
            val userData = extractUserDataFromJWT(accessToken)
            if (userData != null) {
                println("🔗 [SessionManager] Caching user data: id=${userData.userId}, email=${userData.email}, displayName=${userData.displayName}, avatarUrl=${userData.avatarUrl}")
                secureStorage.saveString(USER_ID_KEY, userData.userId)
                secureStorage.saveString(USER_EMAIL_KEY, userData.email)
                userData.displayName?.let { secureStorage.saveString(USER_DISPLAY_NAME_KEY, it) }
                userData.avatarUrl?.let { secureStorage.saveString(USER_AVATAR_URL_KEY, it) }
            } else {
                println("⚠️ [SessionManager] Could not extract user data from JWT")
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
     * User data extracted from JWT.
     */
    private data class JWTUserData(
        val userId: String,
        val email: String,
        val displayName: String?,
        val avatarUrl: String?
    )
    
    /**
     * Extract user data from a JWT access token.
     * JWT format: header.payload.signature
     * The payload contains user information including:
     * - 'sub': user ID
     * - 'email': user email
     * - 'user_metadata': object with display_name, avatar_url, etc.
     */
    private fun extractUserDataFromJWT(token: String): JWTUserData? {
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
            
            // Parse JSON and extract user fields
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonPayload = json.parseToJsonElement(decodedString).jsonObject
            
            val userId = jsonPayload["sub"]?.jsonPrimitive?.content ?: return null
            val email = jsonPayload["email"]?.jsonPrimitive?.content ?: ""
            
            // Extract user_metadata if present
            val userMetadata = jsonPayload["user_metadata"]?.jsonObject
            val displayName = userMetadata?.get("full_name")?.jsonPrimitive?.content
                ?: userMetadata?.get("name")?.jsonPrimitive?.content
                ?: userMetadata?.get("display_name")?.jsonPrimitive?.content
            val avatarUrl = userMetadata?.get("avatar_url")?.jsonPrimitive?.content
                ?: userMetadata?.get("picture")?.jsonPrimitive?.content
            
            println("🔍 [SessionManager] Extracted from JWT: userId=$userId, email=$email, displayName=$displayName, avatarUrl=$avatarUrl")
            
            JWTUserData(
                userId = userId,
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl
            )
        } catch (e: Exception) {
            println("⚠️ [SessionManager] Failed to decode JWT: ${e.message}")
            null
        }
    }
}

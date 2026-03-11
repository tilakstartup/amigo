package com.amigo.shared.auth

import com.amigo.shared.auth.models.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.ktor.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.Clock

class SessionManager(
    private val supabase: SupabaseClient,
    private val secureStorage: SecureStorage
) {
    
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    companion object {
        private const val SESSION_KEY = "amigo_session"
        private const val ACCESS_TOKEN_KEY = "amigo_access_token"
        private const val REFRESH_TOKEN_KEY = "amigo_refresh_token"
    }
    
    /**
     * Initialize session manager and restore session if available.
     * 
     * This method:
     * 1. Attempts to restore session from secure storage
     * 2. If found, syncs with Supabase and hydrates user data if needed
     * 3. If not found, tries to get session from Supabase directly
     * 4. Validates and refreshes expired sessions
     * 
     * Should be called once when the app starts.
     */
    suspend fun initialize() {
        println("� [KMP SessionManager] initialize() START")
        // Try to restore session from secure storage
        val savedSession = restoreSession()
        println("🔍 [KMP SessionManager] restoreSession() returned: ${if (savedSession != null) "NOT NULL (user.id=${savedSession.user.id})" else "NULL"}")
        if (savedSession != null) {
            println("✅ [KMP SessionManager] Session restored, syncing with Supabase...")
            syncSupabaseSession(savedSession)
            val hydratedSession = hydrateSessionIfNeeded(savedSession)
            println("🔍 [KMP SessionManager] After hydration: user.id=${hydratedSession.user.id}")
            _currentSession.value = hydratedSession
            _isAuthenticated.value = true
            println("✅ [KMP SessionManager] _currentSession.value set, user.id=${_currentSession.value?.user?.id}")
            if (hydratedSession != savedSession) {
                saveSession(hydratedSession)
            }
            
            // Validate and refresh if needed
            if (isSessionExpired(hydratedSession)) {
                println("⚠️ [KMP SessionManager] Session expired, refreshing...")
                refreshSession()
            }
        } else {
            println("⚠️ [KMP SessionManager] No saved session, trying Supabase...")
            // Try to get session from Supabase
            val supabaseSession = supabase.auth.currentSessionOrNull()
            if (supabaseSession != null) {
                // Extract user ID from JWT if session.user is incomplete
                val userId = supabaseSession.user?.id ?: extractUserIdFromJWT(supabaseSession.accessToken)
                
                val session = Session(
                    accessToken = supabaseSession.accessToken,
                    refreshToken = supabaseSession.refreshToken ?: "",
                    expiresAt = normalizeEpochSeconds(supabaseSession.expiresAt),
                    user = User(
                        id = normalizeUserId(userId),
                        email = supabaseSession.user?.email ?: "",
                        displayName = supabaseSession.user?.userMetadata?.get("display_name") as? String,
                        avatarUrl = supabaseSession.user?.userMetadata?.get("avatar_url") as? String,
                        createdAt = supabaseSession.user?.createdAt.toString(),
                        emailVerified = supabaseSession.user?.emailConfirmedAt != null
                    )
                )
                saveSession(session)
                _currentSession.value = session
                _isAuthenticated.value = true
            }
        }
    }
    
    /**
     * Save session to secure storage
     */
    suspend fun saveSession(session: Session) {
        try {
            println("💾 [SessionManager] saveSession() START")
            println("💾 [SessionManager] Session details: user.id=${session.user.id}, email=${session.user.email}")
            println("💾 [SessionManager] Access token length: ${session.accessToken.length}")
            println("💾 [SessionManager] Refresh token length: ${session.refreshToken.length}")
            println("💾 [SessionManager] Expires at: ${session.expiresAt}")
            
            val sessionJson = Json.encodeToString(session)
            println("💾 [SessionManager] Serialized JSON length: ${sessionJson.length}")
            
            println("💾 [SessionManager] Saving SESSION_KEY...")
            secureStorage.saveString(SESSION_KEY, sessionJson)
            
            println("💾 [SessionManager] Saving ACCESS_TOKEN_KEY...")
            secureStorage.saveString(ACCESS_TOKEN_KEY, session.accessToken)
            
            println("💾 [SessionManager] Saving REFRESH_TOKEN_KEY...")
            secureStorage.saveString(REFRESH_TOKEN_KEY, session.refreshToken)
            
            _currentSession.value = session
            _isAuthenticated.value = true
            
            println("✅ [SessionManager] saveSession() COMPLETE")
        } catch (e: Exception) {
            // Handle serialization error
            println("❌ [SessionManager] Failed to save session: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Restore session from secure storage
     */
    private suspend fun restoreSession(): Session? {
        return try {
            println("🔵 [KMP SessionManager] restoreSession() START")
            val sessionJson = secureStorage.getString(SESSION_KEY)
            println("🔍 [KMP SessionManager] secureStorage.getString() returned: ${if (sessionJson != null) "NOT NULL (length=${sessionJson.length})" else "NULL"}")
            if (sessionJson != null) {
                println("🔍 [KMP SessionManager] Attempting to deserialize JSON...")
                val session = Json.decodeFromString<Session>(sessionJson)
                println("✅ [KMP SessionManager] Deserialization SUCCESS! user.id=${session.user.id}, email=${session.user.email}")
                session
            } else {
                println("⚠️ [KMP SessionManager] No session JSON in storage")
                null
            }
        } catch (e: Exception) {
            // Handle deserialization error
            println("❌ [KMP SessionManager] restoreSession() FAILED: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if session is expired
     */
    fun isSessionExpired(session: Session): Boolean {
        val currentTime = Clock.System.now().epochSeconds
        return session.expiresAt < currentTime
    }
    
    /**
     * Refresh session using refresh token
     */
    suspend fun refreshSession(): AuthResult {
        return try {
            val currentSession = _currentSession.value
            if (currentSession == null) {
                return AuthResult.Error(
                    message = "No session to refresh",
                    code = AuthErrorCode.SESSION_EXPIRED
                )
            }
            
            // Refresh session with Supabase
            supabase.auth.refreshCurrentSession()
            
            // Get new session
            val newSupabaseSession = supabase.auth.currentSessionOrNull()
            if (newSupabaseSession != null) {
                val newSession = Session(
                    accessToken = newSupabaseSession.accessToken,
                    refreshToken = newSupabaseSession.refreshToken ?: currentSession.refreshToken,
                    expiresAt = normalizeEpochSeconds(newSupabaseSession.expiresAt),
                    user = currentSession.user
                )
                
                saveSession(newSession)
                AuthResult.Success(newSession)
            } else {
                AuthResult.Error(
                    message = "Failed to refresh session",
                    code = AuthErrorCode.SESSION_EXPIRED
                )
            }
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Failed to refresh session",
                code = AuthErrorCode.SESSION_EXPIRED
            )
        }
    }
    
    /**
     * Validate current session
     */
    suspend fun validateSession(): Boolean {
        val session = _currentSession.value ?: return false
        
        if (isSessionExpired(session)) {
            val result = refreshSession()
            return result is AuthResult.Success
        }
        
        return true
    }
    
    /**
     * Sign out and clear session
     */
    suspend fun signOut() {
        try {
            // Sign out from Supabase
            supabase.auth.signOut()
            
            // Clear secure storage
            secureStorage.clear()
            
            // Clear state
            _currentSession.value = null
            _isAuthenticated.value = false
        } catch (e: Exception) {
            println("Failed to sign out: ${e.message}")
        }
    }
    
    /**
     * Get the current authenticated user.
     * 
     * This method returns the user from the current session. If the session's user object
     * doesn't have an ID populated (which can happen with incomplete Supabase sessions),
     * it will decode the JWT access token to extract the user ID from the 'sub' claim.
     * 
     * @return The current User object with ID, email, and other details, or null if not authenticated
     * 
     * Note: This method is synchronous and safe to call from any context. It will attempt
     * to extract the user ID from the JWT token if the session object is incomplete.
     */
    fun getCurrentUser(): User? {
        val session = _currentSession.value
        val user = session?.user
        
        // If user.id is already populated, return it
        if (user?.id?.isNotBlank() == true) {
            return user
        }
        
        // If user.id is blank but we have an access token, decode it to get the user ID
        if (session?.accessToken?.isNotBlank() == true) {
            try {
                val userId = extractUserIdFromJWT(session.accessToken)
                if (userId != null) {
                    println("🔍 [SessionManager] Extracted userId from JWT: $userId")
                    // Return user with extracted ID
                    return user?.copy(id = userId) ?: User(
                        id = userId,
                        email = "",
                        displayName = null,
                        avatarUrl = null,
                        createdAt = "",
                        emailVerified = false
                    )
                }
            } catch (e: Exception) {
                println("⚠️ [SessionManager] Failed to extract userId from JWT: ${e.message}")
            }
        }
        
        return if (user?.id?.isNotBlank() == true) user else null
    }
    
    /**
     * Extract user ID from a JWT access token.
     * 
     * JWT tokens have the format: header.payload.signature
     * The payload is base64url-encoded JSON containing claims including 'sub' (subject/user ID).
     * 
     * This is a fallback mechanism for when the Supabase session object doesn't have
     * user information populated, which can happen in certain authentication flows.
     * 
     * @param token The JWT access token
     * @return The user ID from the 'sub' claim, or null if extraction fails
     * 
     * Technical details:
     * - JWT uses base64url encoding (RFC 4648 §5) which differs from standard base64
     * - Characters: '-' and '_' instead of '+' and '/'
     * - Padding ('=') may be omitted
     * - This method handles the conversion and padding restoration
     */
    private fun extractUserIdFromJWT(token: String): String? {
        return try {
            // JWT format: header.payload.signature
            val parts = token.split(".")
            if (parts.size != 3) {
                println("⚠️ [SessionManager] JWT doesn't have 3 parts: ${parts.size}")
                return null
            }
            
            val payload = parts[1]
            // Convert base64url to standard base64
            val base64 = payload.replace('-', '+').replace('_', '/')
            // Add padding if needed (base64 requires length to be multiple of 4)
            val paddedBase64 = when (base64.length % 4) {
                2 -> base64 + "=="
                3 -> base64 + "="
                else -> base64
            }
            
            val decodedString = paddedBase64.decodeBase64Bytes().decodeToString()
            
            // Parse JSON to extract 'sub' field (user ID)
            val jsonPayload = Json.parseToJsonElement(decodedString).jsonObject
            val sub = jsonPayload["sub"]?.jsonPrimitive?.content
            sub
        } catch (e: Exception) {
            println("⚠️ [SessionManager] Error decoding JWT: ${e.message}")
            null
        }
    }

    /**
     * Hydrate session with user data if the user ID is missing.
     * 
     * This handles cases where the session was saved without complete user information.
     * It attempts to:
     * 1. Get user data from Supabase session
     * 2. Extract user ID from JWT token if Supabase session is incomplete
     * 3. Merge the data with existing session
     * 
     * @param session The session to hydrate
     * @return The hydrated session with complete user data, or original if hydration fails
     */
    private suspend fun hydrateSessionIfNeeded(session: Session): Session {
        if (session.user.id.isNotBlank()) {
            return session
        }

        return try {
            val supabaseSession = supabase.auth.currentSessionOrNull()
            
            // Try to get user ID from Supabase session, or extract from JWT
            val userId = supabaseSession?.user?.id 
                ?: extractUserIdFromJWT(session.accessToken)
                ?: session.user.id
            
            val hydratedUser = User(
                id = normalizeUserId(userId).ifBlank { session.user.id },
                email = supabaseSession?.user?.email ?: session.user.email,
                displayName = supabaseSession?.user?.userMetadata?.get("display_name") as? String
                    ?: session.user.displayName,
                avatarUrl = supabaseSession?.user?.userMetadata?.get("avatar_url") as? String
                    ?: session.user.avatarUrl,
                createdAt = supabaseSession?.user?.createdAt?.toString() ?: session.user.createdAt,
                emailVerified = supabaseSession?.user?.emailConfirmedAt != null || session.user.emailVerified
            )

            if (hydratedUser.id.isBlank()) {
                session
            } else {
                session.copy(user = hydratedUser)
            }
        } catch (_: Exception) {
            session
        }
    }

    /**
     * Ensure Supabase session is synced with current session
     * Call this before database operations to ensure RLS policies work correctly
     */
    suspend fun ensureSessionSynced(): Boolean {
        return try {
            println("🔄 [SessionManager] ensureSessionSynced() START")
            val session = _currentSession.value
            if (session != null) {
                println("🔄 [SessionManager] Current session exists, syncing...")
                syncSupabaseSession(session)
                println("✅ SessionManager: Session synced with Supabase")
                true
            } else {
                println("⚠️ SessionManager: No session to sync")
                // Try to restore and sync
                val restored = restoreSession()
                if (restored != null) {
                    println("🔄 [SessionManager] Session restored, syncing...")
                    syncSupabaseSession(restored)
                    _currentSession.value = restored
                    _isAuthenticated.value = true
                    println("✅ SessionManager: Session restored and synced")
                    true
                } else {
                    println("❌ [SessionManager] No session to restore")
                    false
                }
            }
        } catch (e: Exception) {
            println("❌ SessionManager: Failed to sync session: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun syncSupabaseSession(session: Session) {
        try {
            println("🔄 [SessionManager] syncSupabaseSession() START")
            println("🔄 [SessionManager] Session user.id: ${session.user.id}")
            println("🔄 [SessionManager] Access token length: ${session.accessToken.length}")
            
            val expiresIn = if (session.expiresAt > 0) {
                val now = Clock.System.now().epochSeconds
                val remaining = session.expiresAt - now
                if (remaining > 0) remaining else 3600L
            } else {
                3600L
            }
            
            println("🔄 [SessionManager] Calculated expiresIn: $expiresIn seconds")
            println("🔄 [SessionManager] Calling supabase.auth.importSession()...")

            supabase.auth.importSession(
                io.github.jan.supabase.gotrue.user.UserSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresIn = expiresIn,
                    tokenType = "Bearer",
                    user = null  // Note: We pass null here, Supabase will decode from token
                )
            )
            
            println("✅ [SessionManager] supabase.auth.importSession() completed")
            
            // Verify the session was imported
            val importedSession = supabase.auth.currentSessionOrNull()
            if (importedSession != null) {
                println("✅ [SessionManager] Verified: Supabase session exists after import")
                println("✅ [SessionManager] Imported session user.id: ${importedSession.user?.id ?: "null"}")
                println("✅ [SessionManager] Imported session token length: ${importedSession.accessToken?.length ?: 0}")
            } else {
                println("⚠️ [SessionManager] WARNING: No Supabase session found after import!")
            }
        } catch (e: Exception) {
            println("❌ [SessionManager] Failed to sync Supabase session: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Get the current access token.
     * 
     * This method attempts to get the access token from multiple sources in order:
     * 1. Current session in memory
     * 2. Restored session from secure storage
     * 3. Supabase session
     * 4. Secure storage directly
     * 
     * If the session is expired, it will attempt to refresh it automatically.
     * 
     * @return The access token string, or null if no valid session exists
     */
    suspend fun getAccessToken(): String? {
        println("🔑 [SessionManager] getAccessToken() START")
        var session = _currentSession.value
        println("🔑 [SessionManager] _currentSession.value: ${if (session != null) "NOT NULL (user.id=${session.user.id}, token=${session.accessToken.take(20)}...)" else "NULL"}")

        // If no session in memory, try to restore from storage
        if (session == null) {
            println("🔑 [SessionManager] Session is null, attempting to restore...")
            val restoredSession = restoreSession()
            if (restoredSession != null) {
                println("🔑 [SessionManager] Session restored successfully")
                session = restoredSession
                _currentSession.value = restoredSession
                _isAuthenticated.value = true
                syncSupabaseSession(restoredSession)
            }
        }

        // If we have a session and it's not expired, return the token
        if (session != null && !isSessionExpired(session)) {
            println("🔑 [SessionManager] Session exists and not expired")
            if (session.accessToken.isNotBlank()) {
                println("🔑 [SessionManager] Returning session access token (${session.accessToken.length} chars)")
                return session.accessToken
            }
        }

        // If session is expired, try to refresh
        if (session != null && isSessionExpired(session)) {
            println("🔑 [SessionManager] Session expired, attempting refresh...")
            val result = refreshSession()
            return if (result is AuthResult.Success) {
                println("🔑 [SessionManager] Refresh successful, returning new token")
                result.session.accessToken
            } else {
                println("🔑 [SessionManager] Refresh failed")
                null
            }
        }

        // Last resort: try Supabase directly
        println("🔑 [SessionManager] No valid session, trying Supabase directly...")
        val supabaseToken = try {
            supabase.auth.currentSessionOrNull()?.accessToken
        } catch (e: Exception) {
            println("🔑 [SessionManager] Error getting Supabase token: ${e.message}")
            null
        }
        
        if (!supabaseToken.isNullOrBlank()) {
            println("🔑 [SessionManager] Got token from Supabase (${supabaseToken.length} chars)")
            return supabaseToken
        }

        println("🔑 [SessionManager] No token found, returning null")
        return null
    }
    
    /**
     * Handle deep link session from email confirmation or OAuth callback.
     * 
     * This imports the session from tokens provided in the deep link and:
     * 1. Imports the session into Supabase
     * 2. Extracts user data (including from JWT if needed)
     * 3. Saves the session locally
     * 
     * @param accessToken The access token from the deep link
     * @param refreshToken The refresh token from the deep link
     * @return AuthResult.Success with the session, or AuthResult.Error if import fails
     */
    suspend fun handleDeepLinkSession(accessToken: String, refreshToken: String): AuthResult {
        return try {
            println("📱 SessionManager: Importing session with tokens")
            println("📱 Access token prefix: ${accessToken.take(20)}...")
            
            // Import the session into Supabase using UserSession
            supabase.auth.importSession(
                io.github.jan.supabase.gotrue.user.UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = 3600, // 1 hour
                    tokenType = "Bearer",
                    user = null
                )
            )
            
            println("📱 SessionManager: Session imported, getting current session")
            
            // Get the imported session
            val supabaseSession = supabase.auth.currentSessionOrNull()
            if (supabaseSession != null) {
                println("📱 SessionManager: Got Supabase session")
                
                // Extract user ID from JWT if Supabase session doesn't have it
                val userId = supabaseSession.user?.id ?: extractUserIdFromJWT(accessToken)
                println("📱 User ID: $userId")
                println("📱 User email: ${supabaseSession.user?.email}")
                
                val session = Session(
                    accessToken = supabaseSession.accessToken,
                    refreshToken = supabaseSession.refreshToken ?: refreshToken,
                    expiresAt = normalizeEpochSeconds(supabaseSession.expiresAt),
                    user = User(
                        id = normalizeUserId(userId),
                        email = supabaseSession.user?.email ?: "",
                        displayName = supabaseSession.user?.userMetadata?.get("display_name") as? String,
                        avatarUrl = supabaseSession.user?.userMetadata?.get("avatar_url") as? String,
                        createdAt = supabaseSession.user?.createdAt.toString(),
                        emailVerified = supabaseSession.user?.emailConfirmedAt != null
                    )
                )
                
                println("📱 SessionManager: Created session with user ID: ${session.user.id}")
                saveSession(session)
                println("✅ SessionManager: Session saved")
                AuthResult.Success(session)
            } else {
                println("❌ SessionManager: No Supabase session after import")
                AuthResult.Error(
                    message = "Failed to import session",
                    code = AuthErrorCode.INVALID_TOKEN
                )
            }
        } catch (e: Exception) {
            println("❌ SessionManager: Error importing session: ${e.message}")
            e.printStackTrace()
            AuthResult.Error(
                message = e.message ?: "Failed to handle deep link session",
                code = AuthErrorCode.INVALID_TOKEN
            )
        }
    }
}

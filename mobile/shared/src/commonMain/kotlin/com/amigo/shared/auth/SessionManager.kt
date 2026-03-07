package com.amigo.shared.auth

import com.amigo.shared.auth.models.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
     * Initialize session manager and restore session if available
     */
    suspend fun initialize() {
        // Try to restore session from secure storage
        val savedSession = restoreSession()
        if (savedSession != null) {
            _currentSession.value = savedSession
            _isAuthenticated.value = true
            
            // Validate and refresh if needed
            if (isSessionExpired(savedSession)) {
                refreshSession()
            }
        } else {
            // Try to get session from Supabase
            val supabaseSession = supabase.auth.currentSessionOrNull()
            if (supabaseSession != null) {
                val session = Session(
                    accessToken = supabaseSession.accessToken,
                    refreshToken = supabaseSession.refreshToken ?: "",
                    expiresAt = (supabaseSession.expiresAt as? Long) ?: 0L,
                    user = User(
                        id = supabaseSession.user?.id ?: "",
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
            val sessionJson = Json.encodeToString(session)
            secureStorage.saveString(SESSION_KEY, sessionJson)
            secureStorage.saveString(ACCESS_TOKEN_KEY, session.accessToken)
            secureStorage.saveString(REFRESH_TOKEN_KEY, session.refreshToken)
            
            _currentSession.value = session
            _isAuthenticated.value = true
        } catch (e: Exception) {
            // Handle serialization error
            println("Failed to save session: ${e.message}")
        }
    }
    
    /**
     * Restore session from secure storage
     */
    private suspend fun restoreSession(): Session? {
        return try {
            val sessionJson = secureStorage.getString(SESSION_KEY)
            if (sessionJson != null) {
                Json.decodeFromString<Session>(sessionJson)
            } else {
                null
            }
        } catch (e: Exception) {
            // Handle deserialization error
            println("Failed to restore session: ${e.message}")
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
                    expiresAt = (newSupabaseSession.expiresAt as? Long) ?: 0L,
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
     * Get current user
     */
    fun getCurrentUser(): User? {
        return _currentSession.value?.user
    }
    
    /**
     * Get access token
     */
    suspend fun getAccessToken(): String? {
        val session = _currentSession.value
        if (session != null && !isSessionExpired(session)) {
            return session.accessToken
        }
        
        // Try to refresh if expired
        val result = refreshSession()
        return if (result is AuthResult.Success) {
            result.session.accessToken
        } else {
            null
        }
    }
    
    /**
     * Handle deep link session from email confirmation
     * This imports the session from the tokens provided in the deep link
     */
    suspend fun handleDeepLinkSession(accessToken: String, refreshToken: String): AuthResult {
        return try {
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
            
            // Get the imported session
            val supabaseSession = supabase.auth.currentSessionOrNull()
            if (supabaseSession != null) {
                val session = Session(
                    accessToken = supabaseSession.accessToken,
                    refreshToken = supabaseSession.refreshToken ?: refreshToken,
                    expiresAt = (supabaseSession.expiresAt as? Long) ?: 0L,
                    user = User(
                        id = supabaseSession.user?.id ?: "",
                        email = supabaseSession.user?.email ?: "",
                        displayName = supabaseSession.user?.userMetadata?.get("display_name") as? String,
                        avatarUrl = supabaseSession.user?.userMetadata?.get("avatar_url") as? String,
                        createdAt = supabaseSession.user?.createdAt.toString(),
                        emailVerified = supabaseSession.user?.emailConfirmedAt != null
                    )
                )
                
                saveSession(session)
                AuthResult.Success(session)
            } else {
                AuthResult.Error(
                    message = "Failed to import session",
                    code = AuthErrorCode.INVALID_TOKEN
                )
            }
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Failed to handle deep link session",
                code = AuthErrorCode.INVALID_TOKEN
            )
        }
    }
}

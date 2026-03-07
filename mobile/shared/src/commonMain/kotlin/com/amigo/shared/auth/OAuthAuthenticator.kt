package com.amigo.shared.auth

import com.amigo.shared.auth.models.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.Apple
import io.github.jan.supabase.gotrue.providers.builtin.OTP

class OAuthAuthenticator(private val supabase: SupabaseClient) {
    
    /**
     * Sign in with Google OAuth
     * Platform-specific implementation required for OAuth flow
     */
    suspend fun signInWithGoogle(): AuthResult {
        return try {
            // This will trigger platform-specific OAuth flow
            // The actual OAuth flow is handled by platform-specific code
            supabase.auth.signInWith(Google)
            
            // Get session after OAuth completes
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val user = User(
                    id = session.user?.id ?: "",
                    email = session.user?.email ?: "",
                    displayName = session.user?.userMetadata?.get("full_name") as? String
                        ?: session.user?.userMetadata?.get("name") as? String,
                    avatarUrl = session.user?.userMetadata?.get("avatar_url") as? String
                        ?: session.user?.userMetadata?.get("picture") as? String,
                    createdAt = session.user?.createdAt.toString(),
                    emailVerified = session.user?.emailConfirmedAt != null
                )
                
                AuthResult.Success(
                    Session(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken ?: "",
                        expiresAt = (session.expiresAt as? Long) ?: 0L,
                        user = user
                    )
                )
            } else {
                AuthResult.Error(
                    message = "Failed to create session after Google sign-in",
                    code = AuthErrorCode.UNKNOWN_ERROR
                )
            }
        } catch (e: Exception) {
            handleOAuthException(e, "Google")
        }
    }
    
    /**
     * Sign in with Apple OAuth
     * Platform-specific implementation required for OAuth flow
     */
    suspend fun signInWithApple(): AuthResult {
        return try {
            // This will trigger platform-specific OAuth flow
            // The actual OAuth flow is handled by platform-specific code
            supabase.auth.signInWith(Apple)
            
            // Get session after OAuth completes
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val user = User(
                    id = session.user?.id ?: "",
                    email = session.user?.email ?: "",
                    displayName = session.user?.userMetadata?.get("full_name") as? String
                        ?: session.user?.userMetadata?.get("name") as? String,
                    avatarUrl = session.user?.userMetadata?.get("avatar_url") as? String,
                    createdAt = session.user?.createdAt.toString(),
                    emailVerified = session.user?.emailConfirmedAt != null
                )
                
                AuthResult.Success(
                    Session(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken ?: "",
                        expiresAt = (session.expiresAt as? Long) ?: 0L,
                        user = user
                    )
                )
            } else {
                AuthResult.Error(
                    message = "Failed to create session after Apple sign-in",
                    code = AuthErrorCode.UNKNOWN_ERROR
                )
            }
        } catch (e: Exception) {
            handleOAuthException(e, "Apple")
        }
    }
    
    /**
     * Handle OAuth callback with authorization code
     * This is called by platform-specific code after OAuth redirect
     */
    suspend fun handleOAuthCallback(code: String, provider: String): AuthResult {
        return try {
            // Exchange authorization code for session
            // This is handled automatically by Supabase SDK
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val user = User(
                    id = session.user?.id ?: "",
                    email = session.user?.email ?: "",
                    displayName = session.user?.userMetadata?.get("full_name") as? String
                        ?: session.user?.userMetadata?.get("name") as? String,
                    avatarUrl = session.user?.userMetadata?.get("avatar_url") as? String
                        ?: session.user?.userMetadata?.get("picture") as? String,
                    createdAt = session.user?.createdAt.toString(),
                    emailVerified = session.user?.emailConfirmedAt != null
                )
                
                AuthResult.Success(
                    Session(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken ?: "",
                        expiresAt = (session.expiresAt as? Long) ?: 0L,
                        user = user
                    )
                )
            } else {
                AuthResult.Error(
                    message = "Failed to create session from OAuth callback",
                    code = AuthErrorCode.UNKNOWN_ERROR
                )
            }
        } catch (e: Exception) {
            handleOAuthException(e, provider)
        }
    }
    
    /**
     * Handle OAuth exceptions
     */
    private fun handleOAuthException(e: Exception, provider: String): AuthResult {
        val message = e.message ?: "Unknown error occurred during $provider sign-in"
        val code = when {
            message.contains("cancelled", ignoreCase = true) -> AuthErrorCode.UNKNOWN_ERROR
            message.contains("network", ignoreCase = true) -> AuthErrorCode.NETWORK_ERROR
            else -> AuthErrorCode.UNKNOWN_ERROR
        }
        
        return AuthResult.Error(message = message, code = code)
    }
}

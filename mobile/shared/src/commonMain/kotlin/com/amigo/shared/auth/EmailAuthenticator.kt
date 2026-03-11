package com.amigo.shared.auth

import com.amigo.shared.auth.models.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class EmailAuthenticator(private val supabase: SupabaseClient) {
    
    /**
     * Sign up a new user with email and password
     */
    suspend fun signUp(email: String, password: String): AuthResult {
        return try {
            // Validate email format
            if (!isValidEmail(email)) {
                return AuthResult.Error(
                    message = "Invalid email format",
                    code = AuthErrorCode.INVALID_EMAIL
                )
            }
            
            // Validate password strength
            if (!isValidPassword(password)) {
                return AuthResult.Error(
                    message = "Password must be at least 8 characters long",
                    code = AuthErrorCode.WEAK_PASSWORD
                )
            }
            
            // Sign up with Supabase
            val result = supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            // Get session
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val user = User(
                    id = normalizeUserId(session.user?.id),
                    email = session.user?.email ?: email,
                    displayName = session.user?.userMetadata?.get("display_name") as? String,
                    avatarUrl = session.user?.userMetadata?.get("avatar_url") as? String,
                    createdAt = session.user?.createdAt.toString(),
                    emailVerified = session.user?.emailConfirmedAt != null
                )
                
                AuthResult.Success(
                    Session(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken ?: "",
                        expiresAt = normalizeEpochSeconds(session.expiresAt),
                        user = user
                    )
                )
            } else {
                // No session means email confirmation is required
                AuthResult.EmailConfirmationRequired(email)
            }
        } catch (e: Exception) {
            handleAuthException(e)
        }
    }
    
    /**
     * Sign in an existing user with email and password
     */
    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            // Validate email format
            if (!isValidEmail(email)) {
                return AuthResult.Error(
                    message = "Invalid email format",
                    code = AuthErrorCode.INVALID_EMAIL
                )
            }
            
            // Sign in with Supabase
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            // Get session
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val user = User(
                    id = normalizeUserId(session.user?.id),
                    email = session.user?.email ?: email,
                    displayName = session.user?.userMetadata?.get("display_name") as? String,
                    avatarUrl = session.user?.userMetadata?.get("avatar_url") as? String,
                    createdAt = session.user?.createdAt.toString(),
                    emailVerified = session.user?.emailConfirmedAt != null
                )
                
                AuthResult.Success(
                    Session(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken ?: "",
                        expiresAt = normalizeEpochSeconds(session.expiresAt),
                        user = user
                    )
                )
            } else {
                AuthResult.Error(
                    message = "Failed to create session",
                    code = AuthErrorCode.UNKNOWN_ERROR
                )
            }
        } catch (e: Exception) {
            handleAuthException(e)
        }
    }
    
    /**
     * Validate email format
     */
    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
    
    /**
     * Validate password strength (minimum 8 characters)
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
    
    /**
     * Handle authentication exceptions
     */
    private fun handleAuthException(e: Exception): AuthResult {
        val rawMessage = e.message ?: "Unknown error occurred"
        
        // Determine error code and user-friendly message
        val (code, userMessage) = when {
            rawMessage.contains("Email not confirmed", ignoreCase = true) -> 
                AuthErrorCode.EMAIL_NOT_CONFIRMED to "Please confirm your email address before signing in. Check your inbox for the confirmation link."
            
            rawMessage.contains("User not found", ignoreCase = true) -> 
                AuthErrorCode.USER_NOT_FOUND to "No account found with this email address."
            
            rawMessage.contains("Invalid login credentials", ignoreCase = true) -> 
                AuthErrorCode.WRONG_PASSWORD to "Invalid email or password. Please try again."
            
            rawMessage.contains("User already registered", ignoreCase = true) -> 
                AuthErrorCode.EMAIL_ALREADY_EXISTS to "An account with this email already exists."
            
            rawMessage.contains("network", ignoreCase = true) -> 
                AuthErrorCode.NETWORK_ERROR to "Network error. Please check your connection and try again."
            
            else -> 
                AuthErrorCode.UNKNOWN_ERROR to "Something went wrong. Please try again."
        }
        
        return AuthResult.Error(message = userMessage, code = code)
    }
}

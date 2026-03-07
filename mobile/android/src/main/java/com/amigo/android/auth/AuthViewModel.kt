package com.amigo.android.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amigo.shared.auth.EmailAuthenticator
import com.amigo.shared.auth.OAuthAuthenticator
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.auth.models.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val emailAuthenticator: EmailAuthenticator,
    private val oauthAuthenticator: OAuthAuthenticator,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()
    
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()
    
    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    init {
        viewModelScope.launch {
            sessionManager.initialize()
            sessionManager.isAuthenticated.collect { authenticated ->
                _isAuthenticated.value = authenticated
            }
        }
    }
    
    fun updateEmail(value: String) {
        _email.value = value
    }
    
    fun updatePassword(value: String) {
        _password.value = value
    }
    
    fun updateConfirmPassword(value: String) {
        _confirmPassword.value = value
    }
    
    fun signIn() {
        if (_email.value.isEmpty() || _password.value.isEmpty()) {
            _errorMessage.value = "Please enter email and password"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val result = emailAuthenticator.signIn(_email.value, _password.value)
                
                when (result) {
                    is AuthResult.Success -> {
                        sessionManager.saveSession(result.session)
                    }
                    is AuthResult.EmailConfirmationRequired -> {
                        // Email confirmation not needed for sign in
                    }
                    is AuthResult.Error -> {
                        // Provide user-friendly message for email not confirmed
                        _errorMessage.value = when (result.code) {
                            com.amigo.shared.auth.models.AuthErrorCode.EMAIL_NOT_CONFIRMED -> 
                                "Please confirm your email address before signing in. Check your inbox for the confirmation link."
                            else -> result.message
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Sign in failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun signUp() {
        if (_email.value.isEmpty() || _password.value.isEmpty()) {
            _errorMessage.value = "Please enter email and password"
            return
        }
        
        if (_password.value != _confirmPassword.value) {
            _errorMessage.value = "Passwords do not match"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            
            try {
                val result = emailAuthenticator.signUp(_email.value, _password.value)
                
                when (result) {
                    is AuthResult.Success -> {
                        sessionManager.saveSession(result.session)
                    }
                    is AuthResult.EmailConfirmationRequired -> {
                        _successMessage.value = "Account created! Please check your email (${result.email}) to confirm your account."
                    }
                    is AuthResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Sign up failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun signInWithGoogle() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val result = oauthAuthenticator.signInWithGoogle()
                
                when (result) {
                    is AuthResult.Success -> {
                        sessionManager.saveSession(result.session)
                    }
                    is AuthResult.EmailConfirmationRequired -> {
                        // Email confirmation not needed for OAuth
                    }
                    is AuthResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Google sign in failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            try {
                sessionManager.signOut()
                _email.value = ""
                _password.value = ""
                _confirmPassword.value = ""
            } catch (e: Exception) {
                _errorMessage.value = "Sign out failed: ${e.message}"
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearSuccess() {
        _successMessage.value = null
    }
    
    suspend fun handleDeepLinkSession(accessToken: String, refreshToken: String) {
        _isLoading.value = true
        _errorMessage.value = null
        
        try {
            val result = sessionManager.handleDeepLinkSession(accessToken, refreshToken)
            
            when (result) {
                is AuthResult.Success -> {
                    // Session imported successfully
                    _isAuthenticated.value = true
                }
                is AuthResult.EmailConfirmationRequired -> {
                    // Not applicable for deep link
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to handle session: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}

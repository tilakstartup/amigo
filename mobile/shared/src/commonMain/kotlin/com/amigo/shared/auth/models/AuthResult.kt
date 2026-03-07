package com.amigo.shared.auth.models

sealed class AuthResult {
    data class Success(val session: Session) : AuthResult()
    data class Error(val message: String, val code: AuthErrorCode) : AuthResult()
    data class EmailConfirmationRequired(val email: String) : AuthResult()
}

enum class AuthErrorCode {
    INVALID_EMAIL,
    WEAK_PASSWORD,
    USER_NOT_FOUND,
    WRONG_PASSWORD,
    EMAIL_ALREADY_EXISTS,
    EMAIL_NOT_CONFIRMED,
    NETWORK_ERROR,
    UNKNOWN_ERROR,
    SESSION_EXPIRED,
    INVALID_TOKEN
}

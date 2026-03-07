package com.amigo.shared.auth

/**
 * Platform-specific secure storage interface
 * iOS: Uses Keychain
 * Android: Uses EncryptedSharedPreferences
 */
expect class SecureStorage {
    suspend fun saveString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun remove(key: String)
    suspend fun clear()
}

package com.amigo.shared.auth

import platform.Foundation.NSUserDefaults

/**
 * iOS SecureStorage implementation using NSUserDefaults
 * TODO: Replace with Keychain implementation for production
 */
actual class SecureStorage {
    
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val keyPrefix = "amigo_secure_"
    
    actual suspend fun saveString(key: String, value: String) {
        userDefaults.setObject(value, forKey = keyPrefix + key)
        userDefaults.synchronize()
    }
    
    actual suspend fun getString(key: String): String? {
        return userDefaults.stringForKey(keyPrefix + key)
    }
    
    actual suspend fun remove(key: String) {
        userDefaults.removeObjectForKey(keyPrefix + key)
        userDefaults.synchronize()
    }
    
    actual suspend fun clear() {
        // Remove all keys with our prefix
        val allKeys = userDefaults.dictionaryRepresentation().keys
        allKeys.forEach { key ->
            val keyStr = key.toString()
            if (keyStr.startsWith(keyPrefix)) {
                userDefaults.removeObjectForKey(keyStr)
            }
        }
        userDefaults.synchronize()
    }
}

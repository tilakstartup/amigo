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
        val fullKey = keyPrefix + key
        println("💾 [iOS SecureStorage] saveString() key=$key, fullKey=$fullKey, valueLength=${value.length}")
        userDefaults.setObject(value, forKey = fullKey)
        val synced = userDefaults.synchronize()
        println("💾 [iOS SecureStorage] synchronize() result=$synced")
        
        // Verify it was saved
        val retrieved = userDefaults.stringForKey(fullKey)
        println("💾 [iOS SecureStorage] Verification: ${if (retrieved != null) "SUCCESS (length=${retrieved.length})" else "FAILED - NULL"}")
    }
    
    actual suspend fun getString(key: String): String? {
        val fullKey = keyPrefix + key
        println("🔍 [iOS SecureStorage] getString() key=$key, fullKey=$fullKey")
        val value = userDefaults.stringForKey(fullKey)
        println("🔍 [iOS SecureStorage] Retrieved: ${if (value != null) "NOT NULL (length=${value.length})" else "NULL"}")
        return value
    }
    
    actual suspend fun remove(key: String) {
        val fullKey = keyPrefix + key
        println("🗑️ [iOS SecureStorage] remove() key=$key, fullKey=$fullKey")
        userDefaults.removeObjectForKey(fullKey)
        userDefaults.synchronize()
    }
    
    actual suspend fun clear() {
        println("🗑️ [iOS SecureStorage] clear() START")
        // Remove all keys with our prefix
        val allKeys = userDefaults.dictionaryRepresentation().keys
        var removedCount = 0
        allKeys.forEach { key ->
            val keyStr = key.toString()
            if (keyStr.startsWith(keyPrefix)) {
                println("🗑️ [iOS SecureStorage] Removing key: $keyStr")
                userDefaults.removeObjectForKey(keyStr)
                removedCount++
            }
        }
        userDefaults.synchronize()
        println("🗑️ [iOS SecureStorage] clear() DONE - removed $removedCount keys")
    }
}

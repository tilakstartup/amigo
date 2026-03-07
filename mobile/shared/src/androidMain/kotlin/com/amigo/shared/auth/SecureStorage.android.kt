package com.amigo.shared.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SecureStorage(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "amigo_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    actual suspend fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
    
    actual suspend fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }
    
    actual suspend fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
    
    actual suspend fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}

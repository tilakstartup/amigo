package com.amigo.shared.session

import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.utils.CurrentTime
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory cache for user profiles with TTL.
 * Reduces network calls and improves app startup performance.
 */
class ProfileCache {
    private val cache = mutableMapOf<String, CachedProfile>()
    private val ttl = 5.minutes // Cache valid for 5 minutes
    
    fun get(userId: String): CachedProfile? {
        return cache[userId]?.takeIf { !it.isStale() }
    }
    
    fun put(userId: String, profile: UserProfile) {
        cache[userId] = CachedProfile(
            profile = profile,
            timestamp = CurrentTime.now()
        )
    }
    
    fun clear(userId: String) {
        cache.remove(userId)
    }
    
    fun clearAll() {
        cache.clear()
    }
}

data class CachedProfile(
    val profile: UserProfile,
    val timestamp: Instant
) {
    fun isStale(): Boolean {
        val now = CurrentTime.now()
        val age = now - timestamp
        return age > 5.minutes
    }
}

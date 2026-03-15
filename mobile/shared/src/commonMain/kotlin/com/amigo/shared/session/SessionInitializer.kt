package com.amigo.shared.session

import com.amigo.shared.data.models.UserProfile
import com.amigo.shared.data.models.UnitPreference
import com.amigo.shared.data.models.Theme
import com.amigo.shared.profile.ProfileManager
import com.amigo.shared.auth.SessionManager
import com.amigo.shared.ai.SessionConfigLoader
import com.amigo.shared.ai.SessionConfig
import com.amigo.shared.utils.Logger
import com.amigo.shared.utils.CurrentTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Orchestrates user session initialization on app startup.
 * Loads user profile, determines routing based on onboarding status,
 * and manages caching for optimal performance.
 */
class SessionInitializer(
    private val profileManager: ProfileManager,
    private val profileCache: ProfileCache,
    private val sessionManager: SessionManager
) {
    private val _state = MutableStateFlow<InitializationState>(InitializationState.Idle)
    val state: StateFlow<InitializationState> = _state.asStateFlow()
    
    /**
     * Initialize session for authenticated user.
     * Returns routing decision based on onboarding status.
     */
    suspend fun initialize(userId: String): InitializationResult {
        Logger.i("SessionInitializer", "🚀 Initializing session for user: $userId")
        _state.value = InitializationState.Loading
        
        // Ensure we have a fresh token before any DB calls
        try {
            sessionManager.getAccessToken()
        } catch (e: Exception) {
            Logger.w("SessionInitializer", "⚠️ Token refresh failed, proceeding anyway: ${e.message}")
        }
        
        // Check cache first
        val cachedProfile = profileCache.get(userId)
        if (cachedProfile != null && !cachedProfile.isStale()) {
            Logger.i("SessionInitializer", "✅ Cache hit for user $userId (age: ${cachedProfile.timestamp})")
            // Use cached data for instant routing
            val decision = determineRoute(cachedProfile.profile)
            _state.value = InitializationState.Success(decision)
            
            // Refresh in background
            refreshProfileInBackground(userId)
            
            return InitializationResult.Success(decision)
        }
        
        Logger.i("SessionInitializer", "📡 Cache miss or stale, loading profile from network")
        
        // Load profile with retry logic
        val profileResult = loadProfileWithRetry(userId)
        
        return when (profileResult) {
            is ProfileLoadResult.Success -> {
                Logger.i("SessionInitializer", "✅ Profile loaded successfully")
                profileCache.put(userId, profileResult.profile)
                val decision = determineRoute(profileResult.profile)
                _state.value = InitializationState.Success(decision)
                InitializationResult.Success(decision)
            }
            is ProfileLoadResult.Error -> {
                Logger.e("SessionInitializer", "❌ Profile load failed: ${profileResult.error.message}")
                _state.value = InitializationState.Error(profileResult.error)
                InitializationResult.Error(profileResult.error)
            }
        }
    }
    
    private suspend fun loadProfileWithRetry(
        userId: String,
        maxRetries: Int = 3
    ): ProfileLoadResult {
        var attempt = 0
        var lastError: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                Logger.i("SessionInitializer", "🔄 Profile load attempt ${attempt + 1}/$maxRetries")
                val profile = profileManager.getProfileOrThrow(userId)
                Logger.i("SessionInitializer", "✅ Profile loaded on attempt ${attempt + 1}")
                return ProfileLoadResult.Success(profile)
            } catch (e: Exception) {
                lastError = e
                attempt++
                Logger.w("SessionInitializer", "⚠️ Attempt $attempt failed: ${e.message}")
                
                // Check if this is a "profile not found" error (list is empty)
                if (e.message?.contains("list is empty", ignoreCase = true) == true) {
                    Logger.i("SessionInitializer", "🆕 Profile not found for user $userId - creating new profile in database")
                    
                    // Get user information from auth session
                    val authUser = sessionManager.getCurrentUser()
                    val now = CurrentTime.nowIso8601()
                    
                    // Create a minimal profile with data from auth session
                    // The onboarding flow will update this profile with additional information
                    val newProfile = UserProfile(
                        id = userId,
                        email = authUser?.email ?: "",
                        displayName = authUser?.displayName,
                        avatarUrl = authUser?.avatarUrl,
                        age = null,
                        heightCm = null,
                        weightKg = null,
                        goalType = null,
                        goalByWhen = null,
                        activityLevel = null,
                        dietaryPreferences = null,
                        unitPreference = UnitPreference.METRIC,
                        theme = Theme.AUTO,
                        onboardingCompleted = false,
                        onboardingCompletedAt = null,
                        createdAt = now,
                        updatedAt = now
                    )
                    
                    // Save to database
                    return try {
                        val createdProfile = profileManager.createProfileOrThrow(newProfile)
                        Logger.i("SessionInitializer", "✅ Profile created in database with email=${createdProfile.email}, displayName=${createdProfile.displayName}, avatarUrl=${createdProfile.avatarUrl}")
                        ProfileLoadResult.Success(createdProfile)
                    } catch (createError: Exception) {
                        Logger.e("SessionInitializer", "❌ Failed to create profile in database: ${createError.message}")
                        // Return the profile anyway so user can proceed to onboarding
                        // The onboarding completion will try to save again
                        Logger.w("SessionInitializer", "⚠️ Returning temporary profile to allow onboarding to proceed")
                        ProfileLoadResult.Success(newProfile)
                    }
                }
                
                if (attempt < maxRetries) {
                    // Exponential backoff: 500ms, 1000ms, 2000ms
                    val delayMs = 500L * (1 shl (attempt - 1))
                    Logger.i("SessionInitializer", "⏳ Waiting ${delayMs}ms before retry...")
                    delay(delayMs)
                }
            }
        }
        
        Logger.e("SessionInitializer", "❌ All $maxRetries attempts failed")
        return ProfileLoadResult.Error(
            lastError ?: Exception("Unknown error loading profile")
        )
    }
    
    private fun refreshProfileInBackground(userId: String) {
        // Launch background refresh without blocking
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Logger.i("SessionInitializer", "🔄 Background refresh started for user $userId")
                val profile = profileManager.getProfileOrThrow(userId)
                profileCache.put(userId, profile)
                Logger.i("SessionInitializer", "✅ Background refresh completed")
            } catch (e: Exception) {
                Logger.w("SessionInitializer", "⚠️ Background refresh failed (non-critical): ${e.message}")
                // Silent failure - we already have cached data
            }
        }
    }
    
    private fun determineRoute(profile: UserProfile): RouteDecision {
        Logger.i("SessionInitializer", "🧭 Determining route for user ${profile.id}")
        Logger.i("SessionInitializer", "📊 onboarding_completed = ${profile.onboardingCompleted}")
        
        return if (profile.onboardingCompleted) {
            Logger.i("SessionInitializer", "✅ Onboarding complete → MainApp")
            RouteDecision.MainApp
        } else {
            Logger.i("SessionInitializer", "🎯 Onboarding incomplete → Loading onboarding session")
            // Load onboarding session config
            val config = try {
                SessionConfigLoader.loadConfig("onboarding")
            } catch (e: Exception) {
                Logger.e("SessionInitializer", "❌ Failed to load onboarding config: ${e.message}")
                null
            }
            
            if (config != null) {
                Logger.i("SessionInitializer", "✅ Onboarding config loaded → Onboarding")
                RouteDecision.Onboarding(config)
            } else {
                Logger.w("SessionInitializer", "⚠️ Onboarding config failed to load, falling back to MainApp")
                // Fallback: proceed to main app even though onboarding not complete
                RouteDecision.MainApp
            }
        }
    }
    
    /**
     * Manually retry initialization after error.
     */
    suspend fun retry(userId: String): InitializationResult {
        Logger.i("SessionInitializer", "🔄 Manual retry requested for user $userId")
        return initialize(userId)
    }
}

sealed class InitializationState {
    object Idle : InitializationState()
    object Loading : InitializationState()
    data class Success(val decision: RouteDecision) : InitializationState()
    data class Error(val error: Exception) : InitializationState()
}

sealed class InitializationResult {
    data class Success(val decision: RouteDecision) : InitializationResult()
    data class Error(val error: Exception) : InitializationResult()
}

sealed class RouteDecision {
    object MainApp : RouteDecision()
    data class Onboarding(val config: SessionConfig?) : RouteDecision()
}

private sealed class ProfileLoadResult {
    data class Success(val profile: UserProfile) : ProfileLoadResult()
    data class Error(val error: Exception) : ProfileLoadResult()
}

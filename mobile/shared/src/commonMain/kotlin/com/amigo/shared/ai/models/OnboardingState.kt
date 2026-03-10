package com.amigo.shared.ai.models

/**
 * Onboarding conversation states
 */
sealed class OnboardingState {
    object Initial : OnboardingState()
    object Collecting : OnboardingState()
    object RequestingPermissions : OnboardingState()
    object Complete : OnboardingState()
}

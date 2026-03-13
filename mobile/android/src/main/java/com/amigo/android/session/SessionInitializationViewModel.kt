package com.amigo.android.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amigo.shared.session.SessionInitializer
import com.amigo.shared.session.InitializationState
import com.amigo.shared.session.RouteDecision
import com.amigo.shared.ai.SessionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android ViewModel that bridges SessionInitializer with Compose UI.
 * Observes initialization state and maps to UI-specific states.
 */
class SessionInitializationViewModel(
    private val sessionInitializer: SessionInitializer
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Idle)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    /**
     * Initialize session for the given user ID.
     * Collects state from SessionInitializer and maps to SessionUiState.
     */
    fun initialize(userId: String) {
        // Start collecting state updates in a separate coroutine
        viewModelScope.launch {
            sessionInitializer.state.collect { state ->
                _uiState.value = when (state) {
                    is InitializationState.Idle -> SessionUiState.Idle
                    is InitializationState.Loading -> SessionUiState.Loading
                    is InitializationState.Success -> {
                        when (val decision = state.decision) {
                            is RouteDecision.MainApp -> SessionUiState.NavigateToMain
                            is RouteDecision.Onboarding -> SessionUiState.NavigateToOnboarding(
                                decision.config
                            )
                        }
                    }
                    is InitializationState.Error -> SessionUiState.Error(
                        state.error.message ?: "Unknown error"
                    )
                }
            }
        }
        
        // Trigger initialization in a separate coroutine
        viewModelScope.launch {
            sessionInitializer.initialize(userId)
        }
    }
    
    /**
     * Manually retry initialization after an error.
     */
    fun retry(userId: String) {
        viewModelScope.launch {
            sessionInitializer.retry(userId)
        }
    }
}

/**
 * UI state for session initialization.
 */
sealed class SessionUiState {
    object Idle : SessionUiState()
    object Loading : SessionUiState()
    object NavigateToMain : SessionUiState()
    data class NavigateToOnboarding(val config: SessionConfig?) : SessionUiState()
    data class Error(val message: String) : SessionUiState()
}

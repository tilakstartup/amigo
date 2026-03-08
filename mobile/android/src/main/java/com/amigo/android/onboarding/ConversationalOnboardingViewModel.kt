package com.amigo.android.onboarding

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amigo.shared.ai.*
import com.amigo.shared.auth.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ConversationalOnboardingViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val engine: OnboardingConversationEngine by lazy {
        val apiEndpoint = "https://n96755fzqk.execute-api.us-east-1.amazonaws.com/dev/invoke"
        OnboardingConversationEngineFactory.create(apiEndpoint, sessionManager)
    }
    
    val messages: StateFlow<List<MessageViewModel>> = engine.messages
        .map { messagesList ->
            messagesList.map { msg ->
                MessageViewModel(
                    id = msg.id,
                    text = msg.text,
                    isFromAmigo = msg.isFromAmigo,
                    timestamp = msg.timestamp,
                    replyType = msg.replyType,
                    replies = msg.replies?.toList(),
                    feature = msg.feature,
                    isFeatureIntro = msg.isFeatureIntro,
                    delayAfterPrevious = msg.delayAfterPrevious,
                    isDisabled = msg.isDisabled
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val isTyping: StateFlow<Boolean> = engine.isTyping
        .map { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    private val _conversationState = engine.conversationState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OnboardingState.Initial
        )
    
    val isComplete: StateFlow<Boolean> = _conversationState
        .map { it is OnboardingState.Complete }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val shouldRequestPermissions: StateFlow<Boolean> = _conversationState
        .map { it is OnboardingState.RequestingPermissions }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val needsTextInput: StateFlow<Boolean> = messages
        .map { messagesList ->
            messagesList.lastOrNull()?.let { lastMessage ->
                lastMessage.isFromAmigo && (lastMessage.replyType == "text" || lastMessage.replyType == null)
            } ?: false
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val userInput = mutableStateOf("")
    val inputPlaceholder = mutableStateOf("Type your message...")
    
    private var isSubmitting = false
    
    fun startOnboarding() {
        viewModelScope.launch {
            try {
                engine.startOnboarding()
            } catch (e: Exception) {
                println("Error starting onboarding: ${e.message}")
            }
        }
    }
    
    suspend fun sendMessage() {
        val message = userInput.value.trim()
        if (message.isEmpty() || isSubmitting) return
        
        isSubmitting = true
        
        // Clear input immediately
        userInput.value = ""
        
        try {
            engine.processUserResponse(message)
        } catch (e: Exception) {
            println("Error processing response: ${e.message}")
        } finally {
            isSubmitting = false
        }
    }
    
    suspend fun sendQuickReply(reply: String) {
        if (isSubmitting) return
        
        isSubmitting = true
        
        try {
            engine.processQuickReply(reply)
        } catch (e: Exception) {
            println("Error processing quick reply: ${e.message}")
        } finally {
            isSubmitting = false
        }
    }
    
    fun getProfileData(): Map<String, String> {
        return engine.getProfileData().toMap()
    }
}

// Message View Model
data class MessageViewModel(
    val id: String,
    val text: String,
    val isFromAmigo: Boolean,
    val timestamp: Long,
    val replyType: String?,
    val replies: List<String>?,
    val feature: FeatureIntro?,
    val isFeatureIntro: Boolean,
    val delayAfterPrevious: Long,
    val isDisabled: Boolean
) {
    val formattedTime: String
        get() {
            val date = Date(timestamp)
            val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            return formatter.format(date)
        }
}
